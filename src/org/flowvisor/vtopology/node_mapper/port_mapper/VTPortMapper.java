package org.flowvisor.vtopology.node_mapper.port_mapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.flowvisor.VeRTIGO;
import org.flowvisor.classifier.FVSendMsg;
import org.flowvisor.io.FVMessageAsyncStream;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.flowvisor.log.SendRecvDropStats.FVStatsType;
import org.flowvisor.message.FVFeaturesReply;
import org.flowvisor.message.FVFlowMod;
import org.flowvisor.message.FVFlowRemoved;
import org.flowvisor.message.FVPacketIn;
import org.flowvisor.message.FVPacketOut;
import org.flowvisor.message.FVPortMod;
import org.flowvisor.message.FVPortStatus;
import org.flowvisor.message.FVStatisticsReply;
import org.flowvisor.message.FVStatisticsRequest;
import org.flowvisor.message.actions.FVActionDataLayerDestination;
import org.flowvisor.message.actions.FVActionDataLayerSource;
import org.flowvisor.message.actions.FVActionNetworkLayerSource;
import org.flowvisor.message.actions.FVActionOutput;
import org.flowvisor.message.actions.FVActionVirtualLanIdentifier;
import org.flowvisor.message.lldp.LLDPTrailer;
import org.flowvisor.message.lldp.LLDPUtil;
import org.flowvisor.message.statistics.*;
import org.flowvisor.slicer.FVSlicer;
import org.flowvisor.classifier.FVClassifier;
import org.flowvisor.events.FVEventHandler;
import org.flowvisor.exceptions.BufferFull;
import org.flowvisor.exceptions.MalformedOFMessage;
import org.flowvisor.flows.FlowSpaceUtil;
import org.flowvisor.vtopology.topology_configurator.*;
import org.flowvisor.vtopology.utils.VTChangeFlowMatch;
import org.flowvisor.vtopology.utils.VTCloneStatsMsg;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPortStatus.OFPortReason;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionType;
import org.openflow.protocol.statistics.OFPortStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.openflow.util.U32;

/**
 * @name VTPortMapper
 * @author roberto.doriguzzi matteo.gerola
 * @description	Class that provides only two methods: UpLink and DownLink. Both methods change inPort 
 * and outPort values with virtual port values.
 * The Openflow Controller doesn't see the real switch port values, but virtual values. 
 * This trick allows ADVisor to define multiple virtual links on the same physical port 
 *
 */

/* Special ports "short" values
OFPP_MAX: -256
OFPP_IN_PORT: -8
OFPP_TABLE: -7
OFPP_NORMAL: -6
OFPP_FLOOD: -5
OFPP_ALL: -4
OFPP_CONTROLLER: -3
OFPP_LOCAL: -2
OFPP_NONE: -1
*/

public class VTPortMapper {
	private OFMessage msg;
	public boolean end_point; //used by the FVSlicer to decide whether call the LinkBroker (end_point==false)
	private VTConfigInterface vt_config;
	private List<String> portnames;

	public VTPortMapper(OFMessage m, VTConfigInterface config) {
		this.msg = m;
		end_point = true;
		vt_config = config;
		vt_config.Clear();
		portnames = new LinkedList<String>();
		portnames.add("eth");
		portnames.add("wlan");
	}
	
	/**
	 * @name ChangePortDescription
	 * @authors roberto.doriguzzi matteo.gerola
	 * @param OFPhysicalPort inPort, Integer portNumber         
	 * @return void
	 * @description changes the OFPhysicalPort fields containing the port number
	 */
	private void ChangePortDescription(OFPhysicalPort inPort, Integer portNumber) {
		String portName = inPort.getName();
		int index = -1;
		for(String name: portnames){
			index = portName.lastIndexOf(name);
			if(index >= 0) {
				inPort.setName(name + portNumber.toString());
				break;
			}
		}		
		inPort.setPortNumber(portNumber.shortValue());
	}

/**
 * @name rewritePacketIn
 * @authors roberto.doriguzzi matteo.gerola
 * @param byte[] packetDataIn, OFMatch match         
 * @return byte[]
 * @description changes some fields of the packetIn Data buffer
 */
	private static byte[] rewritePacketIn(byte[] packetDataIn, OFMatch match) {
		ByteBuffer data = ByteBuffer.wrap(packetDataIn);

//		for(int i=0;i<packetDataIn.length;i++)
//			System.out.println("packetDataIn[" + i+ "]: " + Long.toHexString(packetDataIn[i]));
		
		data.put(match.getDataLayerDestination());
		data.put(match.getDataLayerSource());

        data.rewind();
        byte[] packetDataOut = data.array();
        
//        for(int i=0;i<packetDataOut.length;i++)
//			System.out.println("packetDataOut[" + i+ "]: " + Long.toHexString(packetDataOut[i]));
        
		return packetDataOut;	
	}
	
	/**
	 * @name UpLinkMapping
	 * @authors roberto.doriguzzi matteo.gerola
	 * @param FVSlicer slicer, String sliceName, long switchId, FVSendMsg from         
	 * @return true in case of end point, false otherwise
	 * @description This function checks whether the switch is an end point of the virtual link. If true, physical ports 
	 * 				are remapped into virtual ports
	 */
	public Integer UpLinkMapping(FVSlicer slicer, String sliceName, FVClassifier switchInfo) {
		Integer ret = 1;
		long switchId = switchInfo.getDPID();
		
		if (this.msg.getType() == OFType.FEATURES_REPLY) {
			FVFeaturesReply reply = (FVFeaturesReply) this.msg;
			List<OFPhysicalPort> inPortList = reply.getPorts();
			List<OFPhysicalPort> outPortList = new LinkedList<OFPhysicalPort>();
//			System.out.println("--------------------------------------------");
//			System.out.println("FEATURES_REPLY IN switchId: "  + Long.toHexString(switchId) + " match: " + reply.toString());
			
			// DB initialization 
			vt_config.InitSwitchInfo(sliceName,switchId,inPortList); 
			vt_config.InstallStaticMiddlePointEntries(slicer, switchInfo, 0, OFPort.OFPP_ALL.getValue(), -1, sliceName);
			HashMap<Integer,LinkedList<Integer>> phyToVirtPortMap = vt_config.GetPhyToVirtMap(sliceName,switchId);
			
			if(phyToVirtPortMap != null) {
//				System.out.println("FEATURES_REPLY phyToVirtPortMap: " + phyToVirtPortMap);
				end_point = true;  // features replies are always sent to the controller 
	
				// port mapping
				for (OFPhysicalPort inPort: inPortList){
					Integer inPort_nr = Integer.valueOf((int)inPort.getPortNumber());
					if(phyToVirtPortMap.containsKey(inPort_nr)){
						for (Integer virtPortNumber: phyToVirtPortMap.get(inPort_nr))
						{
//							System.out.println("FEATURES REPLY - SWITCH ID: 0x" + Integer.toHexString((int)switchId) + " PHYSICAL PORT: " + inPort_nr + " VIRTUAL PORT: " + virtPortNumber);
							this.ChangePortDescription(inPort, virtPortNumber);
							outPortList.add(inPort); 
						}
					}
				}
				reply.setPorts(outPortList);
			}
			
//			System.out.println("FEATURES_REPLY OUT switchId: "  + Long.toHexString(switchId) + " match: " + reply.toString());
		}
		else if (this.msg.getType() == OFType.PACKET_IN) {
//			System.out.println("--------------------------------------------");
			FVPacketIn packetIn = (FVPacketIn) this.msg;
//			System.out.println("PACKET_IN IN sliceName: "  + sliceName);
//			System.out.println("PACKET_IN IN inPort: " + packetIn.getInPort() + " bufferid: " + packetIn.getBufferId());
					
			if(LLDPUtil.LLDPCheck(packetIn.getPacketData())) { // LLDP packet
				return 1;
			}

			OFMatch match = new OFMatch();
			match.loadFromPacket(packetIn.getPacketData(), packetIn.getInPort());
//			System.out.println("PACKET_IN IN switchId: "  + Long.toHexString(switchId) + " match: " + match.toString());
			short linkId = vt_config.vt_hashmap.getLinkId(switchId, match, packetIn.getInPort());
			this.end_point = vt_config.GetEndPoint(sliceName,switchId,linkId);
//			System.out.println("PACKET_IN IN linkId: " + linkId);
			
			if(!this.end_point) {// middlepoint switch: installing permanent entries for this specific virtual link
				vt_config.InstallStaticMiddlePointEntries(slicer, switchInfo, Integer.valueOf(linkId), packetIn.getInPort(), packetIn.getBufferId(), sliceName);				
				ret = 0;
			} else { //end point switch: remapping and forwarding
				Integer buffer_id = vt_config.vt_hashmap.updateFlowInfo(0,match.getDataLayerSource(), match.getDataLayerDestination(),linkId,VTHashMap.FLOWINFO_ACTION.PACKETIN.ordinal());
				if(buffer_id != null){
					// saving the linkid and the original buffer_id 
					vt_config.bufferIdMap.put(buffer_id, Arrays.asList(packetIn.getBufferId(),Integer.valueOf(linkId)));
					// setting the "fake" buffer_id
					packetIn.setBufferId(buffer_id);
				
					if(linkId > 0) {
						Integer virtPortId = vt_config.GetPhyToVirtMap(sliceName, switchId, linkId);
						// port mapping			
						if (packetIn.getInPort() <  U16.f(OFPort.OFPP_MAX.getValue())) {//OF "virtual ports" are not remapped
							if(virtPortId != null){
								packetIn.setInPort(virtPortId.shortValue());
							}
						}
						// writing the match with the new mac_addresses
						packetIn.setPacketData(rewritePacketIn(packetIn.getPacketData(), match));
						match.loadFromPacket(packetIn.getPacketData(), packetIn.getInPort());
//						System.out.println("PACKET_IN OUT switchId: "  + Long.toHexString(switchId) + " match: " + match.toString());
//						System.out.println("PACKET_IN OUT inPort: " + packetIn.getInPort() + " bufferid: " + packetIn.getBufferId());
					}
				}
				else ret = 0;
			}

		}
		else if (this.msg.getType() == OFType.FLOW_REMOVED) {
			FVFlowRemoved flowRem = (FVFlowRemoved) this.msg;
			OFMatch match = flowRem.getMatch();	
//			System.out.println("--------------------------------------------");
//			System.out.println("FLOW_REMOVED IN switchId: "  + Long.toHexString(switchId) + " flowRem: " + flowRem);
//			System.out.println("FLOW_REMOVED IN coockie: "  + flowRem.getCookie());
			// checking endpoint
	        short linkId = vt_config.vt_hashmap.getLinkId(switchId, match, match.getInputPort());
	        this.end_point = vt_config.GetEndPoint(sliceName,switchId,linkId);
//	        System.out.println("FLOW_REMOVED linkId: " + linkId);
	        if(linkId > 0) vt_config.cleanFlowMatchTable(sliceName, switchId, match, linkId);

			// port remapping
			if(!end_point) ret = 0;
			else if(linkId > 0){
				if (match.getInputPort() < U16.f(OFPort.OFPP_MAX.getValue())) {//OF "virtual ports" are not remapped
					Integer virtPortId = vt_config.GetPhyToVirtMap(sliceName, switchId, linkId);
					if(virtPortId != null){
						match.setInputPort(virtPortId.shortValue());
					}
				}
			}
//			System.out.println("FLOW_REMOVED OUT switchId: "  + Long.toHexString(switchId) + " flowRem: " + flowRem);
			ret = 1;
		}
		else if (this.msg.getType() == OFType.STATS_REPLY) {
			FVStatisticsReply statsRep = (FVStatisticsReply) this.msg;
			short statsType = statsRep.getStatisticType().getTypeValue();
//			System.out.println("--------------------------------------------");
//			System.out.println("STATS_REPLY IN switchId: "  + Long.toHexString(switchId) + " statsRep: " + statsRep.getStatisticType().toString());
			
			int statsLen = 0;
			// getting the port mapping
			HashMap<Integer,LinkedList<Integer>> phyToVirtPortMap = vt_config.GetPhyToVirtMap(sliceName, switchId);
			end_point = true; //stat replies are always sent to the controller since they are answers to stat requests
			
			if (statsType == OFStatisticsType.FLOW.getTypeValue()) { //match field contains the in_port
				List<OFStatistics> inStatsList = statsRep.getStatistics();
				
				FVLog.log(LogLevel.DEBUG, slicer, "STATS REPLY: " + statsRep.getStatisticType().toString());
				
				for (OFStatistics ofStats : inStatsList) {
					FVFlowStatisticsReply flowStats = (FVFlowStatisticsReply) ofStats;
					OFMatch match = flowStats.getMatch();
					LinkedList<Integer> virtPortList = phyToVirtPortMap.get((int)match.getInputPort());
					match.setInputPort((short)virtPortList.getFirst().shortValue());
				}
				
			} else if (statsType == OFStatisticsType.PORT.getTypeValue()) { //all physical ports must be remapped to virtual ports
				List<OFStatistics> inStatsList = statsRep.getStatistics();
				List<OFStatistics> outStatsList = new LinkedList<OFStatistics>();
				
				FVLog.log(LogLevel.DEBUG, slicer, "STATS REPLY: " + statsRep.getStatisticType().toString());
				
				for (OFStatistics ofStats : inStatsList){
					Integer inPort_nr = Integer.valueOf((int)((FVPortStatisticsReply) ofStats).getPortNumber());
					if(phyToVirtPortMap.containsKey(inPort_nr)){
						for (Integer virtPortNumber: phyToVirtPortMap.get(inPort_nr))
						{
							if(virtPortNumber.shortValue() != 0)
							{
								FVPortStatisticsReply portStats = VTCloneStatsMsg.ClonePortStats((FVPortStatisticsReply)ofStats);
								portStats.setPortNumber(virtPortNumber.shortValue());
								outStatsList.add((OFStatistics)portStats); 
								statsLen += portStats.getLength();
							}
						}
					}
				}
				//replacing the statistic list with the new one containing virtual port numbers
				statsRep.setStatistics(outStatsList);
				statsRep.setLengthU(OFStatisticsReply.MINIMUM_LENGTH + statsLen);
				
			} else if (statsType == OFStatisticsType.QUEUE.getTypeValue()) {
				List<OFStatistics> inStatsList = statsRep.getStatistics();
				List<OFStatistics> outStatsList = new LinkedList<OFStatistics>();
				
				FVLog.log(LogLevel.DEBUG, slicer, "STATS REPLY: " + statsRep.getStatisticType().toString());
				
				for (OFStatistics ofStats : inStatsList){
					Integer inPort_nr = Integer.valueOf((int)((FVQueueStatisticsReply) ofStats).getPortNumber());
					if(phyToVirtPortMap.containsKey(inPort_nr)){
						for (Integer virtPortNumber: phyToVirtPortMap.get(inPort_nr))
						{
							if(virtPortNumber.shortValue() != 0)
							{
								FVQueueStatisticsReply queueStats = VTCloneStatsMsg.CloneQueueStats((FVQueueStatisticsReply)ofStats);
								queueStats.setPortNumber(virtPortNumber.shortValue());
								outStatsList.add((OFStatistics)queueStats); 
								statsLen += queueStats.getLength();
							}
						}
					}
				}
				//replacing the statistic list with the new one containing virtual port numbers
				statsRep.setStatistics(outStatsList);
				statsRep.setLengthU(OFStatisticsReply.MINIMUM_LENGTH + statsLen);
			} else if (statsType == OFStatisticsType.AGGREGATE.getTypeValue()) {
				
				FVLog.log(LogLevel.DEBUG, slicer, "STATS REPLY: " + statsRep.getStatisticType().toString());
				if(statsRep.getLengthU() < 24) statsRep.setLengthU(24); //dirty patch for some NEC switches
				
			}
		}
		else if (this.msg.getType() == OFType.PORT_STATUS) {
			FVPortStatus portStatus = (FVPortStatus) this.msg;
			OFPhysicalPort inPort = portStatus.getDesc();
			ArrayList <FVEventHandler> fvHandlersList = VeRTIGO.getInstance().getHandlersCopy();
			ret = 0; //port status messages are sent from here. Nothing will be sent from the fvSlicer
			
			System.out.println("--------------------------------------------");
			System.out.println("PORT_STATUS IN switchId: " + Long.toHexString(switchId) + " port: " + inPort.getPortNumber());
			
			boolean portState = false;
			byte reason = portStatus.getReason();

			if (reason == OFPortReason.OFPPR_ADD.ordinal()) {
				System.out.println("Reason: ADD");
				portState = true;
			}
			else if (reason == OFPortReason.OFPPR_DELETE.ordinal()) {
				System.out.println("Reason: DELETE");
				portState = false;
			}
			else if (reason == OFPortReason.OFPPR_MODIFY.ordinal()) {
				if(inPort.getState() == OFPortState.OFPPS_LINK_DOWN.getValue()) portState = false;
				else portState = true;
				System.out.println("Reason: MODIFY" + " portState: " + portState);
			}
		
			// getting the mapping between the physical port that changed status and the virtual ports involved
			HashMap<Long,LinkedList<Integer>> classifierToVirtPortMap = vt_config.UpdatePortStatus(slicer.getSliceName(), switchInfo.getDPID(), inPort.getPortNumber(), portState);
			if(classifierToVirtPortMap == null) return 0;
			System.out.println("PORT_STATUS: classifierToVirtPortMap: " + classifierToVirtPortMap);
			// filling the hashmap with DPID and virtual ports involved in the port status 
			HashMap<FVClassifier,LinkedList<Integer>> slicerPortMap = new HashMap<FVClassifier,LinkedList<Integer>>();
			for(long DPID: classifierToVirtPortMap.keySet()) {
				LinkedList <Integer> portList = new LinkedList<Integer>();
				FVClassifier tmpClassifier = null;
				for(FVEventHandler handler: fvHandlersList){ // looking for the handler with DPID
					if(handler.getName().contains("classifier")){
						if(((FVClassifier)handler).getDPID() == DPID){				
							tmpClassifier = (FVClassifier) handler;
							break;
						}
					}
				}
				// for each DPID we save a list of virtual ports and the total number if virtual ports that will change status
				if(tmpClassifier != null){
					portList.clear();
					for(Integer virtPort: classifierToVirtPortMap.get(DPID)){ //remapping ports for this handler
						portList.add(virtPort);	
					}
					slicerPortMap.put(tmpClassifier, portList);
				}
			}
			System.out.println("PORT_STATUS: msgMap: " + slicerPortMap);
			
			// now we modify the msg with the correct virtual port and we set the "from" classifier
			// this process is repeated until msgMap is empty
			for( FVClassifier currentSwitch: slicerPortMap.keySet()){
				for(Integer virtPort:slicerPortMap.get(currentSwitch)) {
					inPort.setPortNumber(virtPort.shortValue());
					System.out.println("PORT_STATUS: physicalPort: " + ((FVPortStatus) this.msg).getDesc().getPortNumber());
					try {
						currentSwitch.getSlicerByName(sliceName).msgStream.testAndWrite(msg);
					} catch (BufferFull e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (MalformedOFMessage e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}

		}
				
		return ret;
	}
	
	
	/**
	 * @name DownLinkMapping
	 * @authors roberto.doriguzzi matteo.gerola
	 * @param String sliceName, long switchId         
	 * @return true in case of successful port mapping
	 */
	public boolean DownLinkMapping(FVSlicer slicer, String sliceName, long switchId) {
		

		if (this.msg.getType() == OFType.PACKET_OUT) {
			FVPacketOut packetOut = (FVPacketOut) this.msg;
//			System.out.println("--------------------------------------------");
//			System.out.println("PACKET_OUT IN switchId: " + Long.toHexString(switchId) + " " + packetOut.toString());
			
			if(packetOut.getInPort() == OFPort.OFPP_CONTROLLER.getValue() || packetOut.getInPort() == OFPort.OFPP_NONE.getValue()){
				// workaround for LLDP messages on Virtual Links			
				if(LLDPUtil.LLDPCheck(packetOut.getPacketData())) {
					OFMatch match = new OFMatch();
					match.loadFromPacket(packetOut.getPacketData(), packetOut.getInPort());			
					ArrayList<OFAction> inActions = (ArrayList<OFAction>) packetOut.getActions();
					
					// if the LLDP goes out of a virtual link, VeRTIGO drops the PACKET_OUT and generates a packet_in for the slicer at the other
					// end of the virtual link
					for(OFAction action: inActions){
						if (action.getType().equals(OFActionType.OUTPUT)){
							short action_port = ((FVActionOutput)action).getPort();
							if ((U16.f(action_port) > 100) && (U16.f(action_port) <= U16.f(OFPort.OFPP_MAX.getValue())))
							{
								vt_config.ManageVLinkLLDP(slicer.getSliceName(), switchId, Integer.valueOf(action_port), packetOut.getPacketData());
								return false;
							}
						}
						
					}
					return true;
				}
			}
			
			// retrieve the mapping between virtual ports and (physical ports, virtual links)
			HashMap<Integer,LinkedList<Integer>> VirtPortsMappings = vt_config.GetVirtPortsMappings(slicer.getSliceName(), switchId);
			
			if(VirtPortsMappings != null){
				short VirtInPort = packetOut.getInPort();
//				System.out.println("PACKET_OUT inPort: " + VirtInPort + " virtToPhyPortMap: " + VirtPortsMappings.toString());

				// first of all we replace the in_port virtual value with the corresponding physical port number
		        if (U16.f(VirtInPort) < U16.f(OFPort.OFPP_MAX.getValue())) {
		        	LinkedList<Integer> InPortMap = VirtPortsMappings.get(Integer.valueOf(VirtInPort));
		        	if(InPortMap != null)
		        		packetOut.setInPort(InPortMap.get(0).shortValue());
		        } 
		        
		        // now we remap the output ports included into the actions fields
		        ArrayList<OFAction> inActions = (ArrayList<OFAction>) packetOut.getActions();
		        ArrayList<OFAction> outActions = new ArrayList<OFAction>();
		        int packetOut_size = FVPacketOut.MINIMUM_LENGTH;
		        for(OFAction action: inActions){ //also ports in the actions must be remapped
		        	if (action.getType().equals(OFActionType.OUTPUT)){
		        		FVActionOutput actionOut = (FVActionOutput)action;
		        		HashMap<Integer,LinkedList<Integer>> outPortSet = null; 
						
						if (U16.f(actionOut.getPort()) < U16.f(OFPort.OFPP_MAX.getValue())) { //just one port in the action
							outPortSet = new HashMap<Integer,LinkedList<Integer>>();
							outPortSet.put(Integer.valueOf(actionOut.getPort()), VirtPortsMappings.get(Integer.valueOf(actionOut.getPort())));
						}
						else if (actionOut.getPort() == OFPort.OFPP_ALL.getValue() || 
								actionOut.getPort() == OFPort.OFPP_FLOOD.getValue()) { //broadcast output action
							outPortSet = new HashMap<Integer,LinkedList<Integer>>();
							outPortSet.putAll(VirtPortsMappings);
							outPortSet.remove(Integer.valueOf(VirtInPort)); //removing the virtual input port	
						}
							
						if(outPortSet != null){
							for (Entry<Integer,LinkedList<Integer>> outPort: outPortSet.entrySet()){
								Integer index = null;
								LinkedList<Integer> OutPortMap = outPort.getValue();
					        	
								if(OutPortMap != null) {
									byte [] srcMAC = new byte[6];
									byte [] dstMAC = new byte[6];
									Integer linkId = OutPortMap.get(1);
									index = vt_config.vt_hashmap.updateFlowInfo(packetOut.getBufferId(),srcMAC, dstMAC,linkId,VTHashMap.FLOWINFO_ACTION.PACKETOUT.ordinal());
									// we change the MACs only if flow is entering (linkId>0) or exiting virtual links
									if(index != null && (linkId > 0 || vt_config.bufferIdMap.get(packetOut.getBufferId()).get(1) > 0)) { 
										FVActionDataLayerSource FVsrcMACaction = new FVActionDataLayerSource();
										FVsrcMACaction.setType(OFActionType.SET_DL_SRC);
										FVsrcMACaction.setDataLayerAddress(srcMAC);									
										FVActionDataLayerDestination FVdstMACaction = new FVActionDataLayerDestination();
										FVdstMACaction.setDataLayerAddress(dstMAC);
										
										try {
											outActions.add(FVsrcMACaction.clone());
											packetOut_size += FVsrcMACaction.getLengthU();
											outActions.add(FVdstMACaction.clone());
											packetOut_size += FVdstMACaction.getLengthU();
										} catch (CloneNotSupportedException e) {
											e.printStackTrace();
										}
									}
									
									if(OutPortMap.get(0)== packetOut.getInPort()){ //comparing the output phy port with the input phy port set above
										actionOut.setPort((short) OFPort.OFPP_IN_PORT.getValue());
									}
									else{
										actionOut.setPort(OutPortMap.get(0).shortValue());
									}
			
									try {
										outActions.add(actionOut.clone());
										packetOut_size += actionOut.getLengthU();
									} catch (CloneNotSupportedException e) {
										e.printStackTrace();
									}
								}							
							}
		        		}
					}
					else{
						outActions.add(action);
						packetOut_size += action.getLengthU();
					}
					
				}
		        packetOut.setActions(outActions);
		        packetOut.setLengthU(packetOut_size);
		        packetOut.setBufferId(vt_config.bufferIdMap.get(packetOut.getBufferId()).get(0)); //restoring the saved buffer_id
		        vt_config.bufferIdMap.remove(packetOut.getBufferId());
//		        System.out.println("PACKET_OUT OUT switchId: " + Long.toHexString(switchId) + " " + packetOut.toString());
			} else return false;
		}
		else if (this.msg.getType() == OFType.FLOW_MOD) {
			FVFlowMod flowMod = (FVFlowMod) this.msg;
			OFMatch match = flowMod.getMatch();
			boolean restoreMAC = false;
			byte [] srcMAC = new byte[6];
			byte [] dstMAC = new byte[6];
			
//			System.out.println("--------------------------------------------");
//			System.out.println("FLOW_MOD IN switchId1: " + Long.toHexString(switchId) + " " + flowMod.toString());
//			System.out.println("FLOW_MOD IN inPort wildcard: " + ((match.getWildcards() & OFMatch.OFPFW_IN_PORT)));
			
			if(vt_config.bufferIdMap.get(flowMod.getBufferId()) != null) {
				// if the flow left a virtual link with tagged MACs, we need to restore the original MAC addresses
				if(vt_config.vt_hashmap.updateFlowInfo(flowMod.getBufferId(),srcMAC, dstMAC,vt_config.bufferIdMap.get(flowMod.getBufferId()).get(1),VTHashMap.FLOWINFO_ACTION.FLOWMOD.ordinal()) != null) {
					restoreMAC = true;				//	we need this flag below to build the actions
				}
			}
			
			// retrieve the mapping between virtual ports and (physical ports, virtual links)
			HashMap<Integer,LinkedList<Integer>> VirtPortsMappings = vt_config.GetVirtPortsMappings(slicer.getSliceName(), switchId);
			
			if(VirtPortsMappings != null){
				short VirtInPort = match.getInputPort();
//				System.out.println("FLOW_MOD  inPort: " + VirtInPort + " virtToPhyPortMap: " + VirtPortsMappings.toString());
				
				// first of all we replace the in_port virtual value with the corresponding physical port number				
		        if (U16.f(VirtInPort) < U16.f(OFPort.OFPP_MAX.getValue())) {	
		        	LinkedList<Integer> InPortMap = VirtPortsMappings.get(Integer.valueOf(VirtInPort));
		        	if(InPortMap != null && ((match.getWildcards() & OFMatch.OFPFW_IN_PORT) == 0)){
		        		match.setInputPort(InPortMap.get(0).shortValue());
		        	}
		        } 
		        
		        // in case of command=DELETE the controller may want to specify the output port (not meaningful for ADD and MODIFY)
		        // here we remap the output port virtual number
		        if(flowMod.getCommand() == FVFlowMod.OFPFC_DELETE || flowMod.getCommand() == FVFlowMod.OFPFC_DELETE_STRICT){
//		        	System.out.println("FLOW_MOD delete");
		        	if (U16.f(flowMod.getOutPort()) < U16.f(OFPort.OFPP_MAX.getValue())) {	
			        	LinkedList<Integer> OutPortMap = VirtPortsMappings.get(Integer.valueOf(flowMod.getOutPort()));
			        	if(OutPortMap != null){
			        		flowMod.setOutPort(OutPortMap.get(0).shortValue());
			        	}
			        }      	
		        }

		        
		        // now we remap the output ports included into the actions fields
		        ArrayList<OFAction> inActions = (ArrayList<OFAction>) flowMod.getActions();
		        ArrayList<OFAction> outActions = new ArrayList<OFAction>();
		        int flowMod_size = FVFlowMod.MINIMUM_LENGTH;
		        for(OFAction action: inActions){ //also ports in the actions must be remapped
		        	if (action.getType().equals(OFActionType.OUTPUT)){
		        		FVActionOutput actionOut = (FVActionOutput)action;
//		        		System.out.println("FLOW_MOD actionOut: " + actionOut.toString());
		        		HashMap<Integer,LinkedList<Integer>> outPortSet = null; 
						
						if (U16.f(actionOut.getPort()) < U16.f(OFPort.OFPP_MAX.getValue())) { //just one port in the action
							outPortSet = new HashMap<Integer,LinkedList<Integer>>();
							outPortSet.put(Integer.valueOf(actionOut.getPort()), VirtPortsMappings.get(Integer.valueOf(actionOut.getPort())));
//							System.out.println("FLOW_MOD outPortSet: " + outPortSet.toString());
						}
						else if (actionOut.getPort() == OFPort.OFPP_ALL.getValue() || 
								actionOut.getPort() == OFPort.OFPP_FLOOD.getValue()) { //broadcast output action
							outPortSet = new HashMap<Integer,LinkedList<Integer>>();
							outPortSet.putAll(VirtPortsMappings);
							outPortSet.remove(Integer.valueOf(VirtInPort)); //removing the virtual input port	
						}
							
						if(outPortSet != null){
							for (Entry<Integer,LinkedList<Integer>> outPort: outPortSet.entrySet()){
								LinkedList<Integer> OutPortMap = outPort.getValue();
					        	
								if(OutPortMap != null) {
									// here we send the actions to restore the MACs when the flow is tagged 
									if(restoreMAC && flowMod.getCommand() == FVFlowMod.OFPFC_ADD) {										
										FVActionDataLayerSource FVsrcMACaction = new FVActionDataLayerSource();
										FVsrcMACaction.setType(OFActionType.SET_DL_SRC);
										FVsrcMACaction.setDataLayerAddress(match.getDataLayerSource());									
										FVActionDataLayerDestination FVdstMACaction = new FVActionDataLayerDestination();
										FVdstMACaction.setDataLayerAddress(match.getDataLayerDestination());
										
										try {
											outActions.add(FVsrcMACaction.clone());
											flowMod_size += FVsrcMACaction.getLengthU();
											outActions.add(FVdstMACaction.clone());
											flowMod_size += FVdstMACaction.getLengthU();
										} catch (CloneNotSupportedException e) {
											e.printStackTrace();
										}					
									}
									
									Integer linkId = OutPortMap.get(1);
									if(linkId > 0) { // we save only flows entering vlinks
										if(flowMod.getCommand() == FVFlowMod.OFPFC_ADD){
											vt_config.saveFlowMatch(sliceName, switchId, match, linkId.shortValue());	
										}
										else if(flowMod.getCommand() == FVFlowMod.OFPFC_DELETE || flowMod.getCommand() == FVFlowMod.OFPFC_DELETE_STRICT){
											vt_config.cleanFlowMatchTable(sliceName, switchId, match, linkId.shortValue());
										}
										vt_config.ManageMiddlePointEntries(sliceName, switchId, flowMod, linkId);
									
										//comparing the output phy port with the input phy port set above
										if(OutPortMap.get(0)== match.getInputPort() && ((match.getWildcards() & OFMatch.OFPFW_IN_PORT) == 0 )){ 
											actionOut.setPort((short) OFPort.OFPP_IN_PORT.getValue());
										}
										else{
											actionOut.setPort(OutPortMap.get(0).shortValue());
										}
									}
			
									try {
										outActions.add(actionOut.clone());
										flowMod_size += actionOut.getLengthU();
									} catch (CloneNotSupportedException e) {
										e.printStackTrace();
									}
								}							
							}
		        		}
					}
					else{
						outActions.add(action);
						flowMod_size += action.getLengthU();
					}
					
				}
		        flowMod.setActions(outActions);
		        flowMod.setLengthU(flowMod_size);
		          
		        //sometimes a tagged flow (i.e. a flow exiting a virtual link that was controller through PACKET_OUTS) is 
		        //controlled through FLOW_MODS. Here we remove the tagged MACS.
		        if(vt_config.bufferIdMap.get(flowMod.getBufferId()) != null) {
			        flowMod.setBufferId(vt_config.bufferIdMap.get(flowMod.getBufferId()).get(0));  
			        if(restoreMAC) {
			        	match.setDataLayerSource(srcMAC);
						match.setDataLayerDestination(dstMAC);
			        }
			        vt_config.bufferIdMap.remove(flowMod.getBufferId());
		        }
//		        System.out.println("FLOW_MOD OUT switchId: " + Long.toHexString(switchId) + " " + flowMod.toString());
			} else return false;
		}
		else if (this.msg.getType() == OFType.PORT_MOD) {
//			FVPortMod portMod = (FVPortMod) this.msg;
//			FVLog.log(LogLevel.INFO, slicer, "PORT_MOD");
//			
//			Integer port_nr = Integer.valueOf(portMod.getPortNumber());
//			// loading virtual values into vt_config structure
//			vt_config.virtPortList.add((int)OFPort.OFPP_ALL.getValue());
//			vt_config.GetVirttoPhyPortMap(msg, slicer.getSliceName(), switchId, "",0);
//			if(U16.f(port_nr.shortValue()) < U16.f(OFPort.OFPP_MAX.getValue())){
//				if(vt_config.virtToPhyPortMap.get(port_nr) != null)
//					portMod.setPortNumber(vt_config.virtToPhyPortMap.get(port_nr).shortValue());
//				else return false;
//			}
//			else return false;
		}
		else if (this.msg.getType() == OFType.STATS_REQUEST) {
//			FVStatisticsRequest statsReq = (FVStatisticsRequest) this.msg;			
//			FVLog.log(LogLevel.DEBUG, slicer, "STATS REQUEST: " + statsReq.getStatisticType().toString());
//			
//			// loading virtual values into vt_config structure
//			vt_config.virtPortList.add((int)OFPort.OFPP_ALL.getValue());
//			vt_config.GetVirttoPhyPortMap(msg, slicer.getSliceName(), switchId, "",0);
//			
//			if(statsReq.getStatisticType() == OFStatisticsType.PORT){
//				List<OFStatistics> inStatsList = statsReq.getStatistics();
//				
//				for (OFStatistics ofStats : inStatsList){
//					FVPortStatisticsRequest portStats = (FVPortStatisticsRequest) ofStats;
//					Integer inPort_nr = Integer.valueOf((int)(portStats.getPortNumber()));	
//					if(U16.f(inPort_nr.shortValue()) < U16.f(OFPort.OFPP_MAX.getValue())){
//						if(vt_config.virtToPhyPortMap.get(inPort_nr) != null)
//							portStats.setPortNumber(vt_config.virtToPhyPortMap.get(inPort_nr).shortValue());
//						else portStats.setPortNumber(OFPort.OFPP_NONE.getValue());
//					}
//				}
//			}
//			else if(statsReq.getStatisticType() == OFStatisticsType.QUEUE){
//				List<OFStatistics> inStatsList = statsReq.getStatistics();
//				
//				for (OFStatistics ofStats : inStatsList){
//					FVQueueStatisticsRequest queueStats = (FVQueueStatisticsRequest) ofStats;
//					Integer inPort_nr = Integer.valueOf((int)(queueStats.getPortNumber()));			
//					if(U16.f(inPort_nr.shortValue()) < U16.f(OFPort.OFPP_MAX.getValue())){
//						if(vt_config.virtToPhyPortMap.get(inPort_nr) != null)
//							queueStats.setPortNumber(vt_config.virtToPhyPortMap.get(inPort_nr).shortValue());
//						else return false;
//					}
//					else return false; // not a valid port number
//				}
//	
//			}
//			else if(statsReq.getStatisticType() == OFStatisticsType.FLOW){
//				List<OFStatistics> inStatsList = statsReq.getStatistics();
//				
//				for (OFStatistics ofStats : inStatsList){
//					FVFlowStatisticsRequest flowStats = (FVFlowStatisticsRequest) ofStats;
//					Integer outPort_nr = Integer.valueOf((int)(flowStats.getOutPort()));			
//					if(U16.f(outPort_nr.shortValue()) < U16.f(OFPort.OFPP_MAX.getValue())){
//						if(vt_config.virtToPhyPortMap.get(outPort_nr) != null)
//							flowStats.setOutPort(vt_config.virtToPhyPortMap.get(outPort_nr).shortValue());
//						else flowStats.setOutPort(OFPort.OFPP_NONE.getValue());
//					}
//					OFMatch match = flowStats.getMatch();
//					if(vt_config.virtToPhyPortMap.get(match.getInputPort()) != null)
//						match.setInputPort(vt_config.virtToPhyPortMap.get(match.getInputPort()).shortValue());
//				}
//				
//				
//			}
//			else if(statsReq.getStatisticType() == OFStatisticsType.AGGREGATE){
//				List<OFStatistics> inStatsList = statsReq.getStatistics();
//				
//				for (OFStatistics ofStats : inStatsList){
//					FVAggregateStatisticsRequest aggregateStats = (FVAggregateStatisticsRequest) ofStats;
//					Integer outPort_nr = Integer.valueOf((int)(aggregateStats.getOutPort()));			
//					if(U16.f(outPort_nr.shortValue()) < U16.f(OFPort.OFPP_MAX.getValue())){
//						if(vt_config.virtToPhyPortMap.get(outPort_nr) != null)
//							aggregateStats.setOutPort(vt_config.virtToPhyPortMap.get(outPort_nr).shortValue());
//						else aggregateStats.setOutPort(OFPort.OFPP_NONE.getValue());
//					}
//					
//					OFMatch match = aggregateStats.getMatch();
//					if(vt_config.virtToPhyPortMap.get(match.getInputPort()) != null)
//						match.setInputPort(vt_config.virtToPhyPortMap.get(match.getInputPort()).shortValue());
//				}
//				
//				
//			}
//			else if(statsReq.getStatisticType() == OFStatisticsType.TABLE){
//				//nothing to remap, just a reminder...
//			}
		}
		
		return true;
	}
}

