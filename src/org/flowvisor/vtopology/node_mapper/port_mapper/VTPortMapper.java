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
import org.flowvisor.vtopology.utils.VTCloneOFPhysicalPort;
import org.flowvisor.vtopology.utils.VTCloneStatsMsg;
import org.flowvisor.vtopology.utils.VTLog;
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
	private VTConfigInterface vt_config;

	public VTPortMapper(OFMessage m, VTConfigInterface config) {
		this.msg = m;
		vt_config = config;
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
		
		data.put(match.getDataLayerDestination());
		data.put(match.getDataLayerSource());

        data.rewind();
        byte[] packetDataOut = data.array();
        
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
			VTLog.VTPortMapper("--------------------------------------------");
			VTLog.VTPortMapper("FEATURES_REPLY IN switchId: "  + Long.toHexString(switchId) + " reply: " + reply.toString());
			
			// DB initialization 
			vt_config.InitSwitchInfo(sliceName,switchId,inPortList); 
			vt_config.InstallStaticMiddlePointEntries(sliceName, slicer, switchInfo, 0, OFPort.OFPP_ALL.getValue(), -1);
			HashMap<Integer,LinkedList<Integer>> phyToVirtPortMap = vt_config.GetPhyToVirtMap(sliceName,switchId);
			
			if(phyToVirtPortMap != null) {
				// port mapping
				for (OFPhysicalPort inPort: inPortList){
					Integer inPort_nr = Integer.valueOf((int)inPort.getPortNumber());
					if(phyToVirtPortMap.containsKey(inPort_nr)){
						for (Integer virtPortNumber: phyToVirtPortMap.get(inPort_nr))
						{
							OFPhysicalPort inVirtPort = VTCloneOFPhysicalPort.ClonePortFeatures(inPort, virtPortNumber);
							outPortList.add(inVirtPort); 
						}
					}
				}
				reply.setPorts(outPortList);
			}
			
		}
		else if (this.msg.getType() == OFType.PACKET_IN) {
			VTLog.VTPortMapper("--------------------------------------------");
			FVPacketIn packetIn = (FVPacketIn) this.msg;
			VTLog.VTPortMapper("PACKET_IN IN sliceName: "  + sliceName);
			VTLog.VTPortMapper("PACKET_IN IN inPort: " + packetIn.getInPort() + " bufferid: " + packetIn.getBufferId());
				
			// LLDP packet_in are sent to the controllers "as is"
			if(LLDPUtil.LLDPCheck(packetIn.getPacketData())) { 
				VTLog.VTPortMapper("PACKET_IN LLDP switchId: " + Long.toHexString(switchId));
				return 1;
			}

			OFMatch match = new OFMatch();
			match.loadFromPacket(packetIn.getPacketData(), packetIn.getInPort());
			VTLog.VTPortMapper("PACKET_IN IN switchId: "  + Long.toHexString(switchId) + " match: " + match.toString());
			int linkId = vt_config.vt_hashmap.getLinkId(switchId, match, packetIn.getInPort());
			boolean end_point = vt_config.GetEndPoint(sliceName,switchId,linkId);
			VTLog.VTPortMapper("PACKET_IN IN linkId: " + linkId);
			
			if(!end_point) {// middlepoint switch: installing permanent entries for this specific virtual link
				vt_config.InstallStaticMiddlePointEntries(sliceName, slicer, switchInfo, Integer.valueOf(linkId), packetIn.getInPort(), packetIn.getBufferId());				
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
						VTLog.VTPortMapper("PACKET_IN OUT switchId: "  + Long.toHexString(switchId) + " match: " + match.toString());
						VTLog.VTPortMapper("PACKET_IN OUT inPort: " + packetIn.getInPort() + " bufferid: " + packetIn.getBufferId());
					}
				}
				else ret = 0;
			}

		}
		else if (this.msg.getType() == OFType.FLOW_REMOVED) {
			FVFlowRemoved flowRem = (FVFlowRemoved) this.msg;
			OFMatch match = flowRem.getMatch();	
			VTLog.VTPortMapper("--------------------------------------------");
			VTLog.VTPortMapper("FLOW_REMOVED IN switchId: "  + Long.toHexString(switchId) + " flowRem: " + flowRem);
			// checking endpoint
	        int linkId = vt_config.vt_hashmap.getLinkId(switchId, match, match.getInputPort());
	        boolean end_point = vt_config.GetEndPoint(sliceName,switchId,linkId);
	        VTLog.VTPortMapper("FLOW_REMOVED linkId: " + linkId);
	        if(linkId > 0) vt_config.removeFlowMatch(sliceName, switchId, match, linkId);

			// port remapping
			if(!end_point) ret = 0;
			else if(linkId > 0){
				if (match.getInputPort() < U16.f(OFPort.OFPP_MAX.getValue())) {//OF "virtual ports" are not remapped
					Integer virtPortId = vt_config.GetPhyToVirtMap(sliceName, switchId, linkId);
					if(virtPortId != null){
						match.setInputPort(virtPortId.shortValue());
					}
				}
				ret = 1;
			}
			VTLog.VTPortMapper("FLOW_REMOVED OUT switchId: "  + Long.toHexString(switchId) + " flowRem: " + flowRem);
		}
		else if (this.msg.getType() == OFType.STATS_REPLY) {
			FVStatisticsReply statsRep = (FVStatisticsReply) this.msg;
			short statsType = statsRep.getStatisticType().getTypeValue();
			VTLog.VTPortMapper("--------------------------------------------");
			VTLog.VTPortMapper("STATS_REPLY IN switchId: "  + Long.toHexString(switchId) + " statsRep: " + statsRep.getStatisticType().toString());
			
			int statsLen = 0;
			// getting the port mapping
			HashMap<Integer,LinkedList<Integer>> phyToVirtPortMap = vt_config.GetPhyToVirtMap(sliceName, switchId);
			
			if (statsType == OFStatisticsType.FLOW.getTypeValue()) { //match field contains the in_port
				List<OFStatistics> inStatsList = statsRep.getStatistics();
				
				for (OFStatistics ofStats : inStatsList) {
					FVFlowStatisticsReply flowStats = (FVFlowStatisticsReply) ofStats;
					OFMatch match = flowStats.getMatch();
					LinkedList<Integer> virtPortList = phyToVirtPortMap.get((int)match.getInputPort());
					match.setInputPort((short)virtPortList.getFirst().shortValue());
				}
				
			} else if (statsType == OFStatisticsType.PORT.getTypeValue()) { //all physical ports must be remapped to virtual ports
				List<OFStatistics> inStatsList = statsRep.getStatistics();
				List<OFStatistics> outStatsList = new LinkedList<OFStatistics>();
				
				for (OFStatistics ofStats : inStatsList){
					Integer phyPort = Integer.valueOf((int)((FVPortStatisticsReply) ofStats).getPortNumber());
					if(phyToVirtPortMap.containsKey(phyPort)){
						for (Integer virtPortNumber: phyToVirtPortMap.get(phyPort))
						{
							if(virtPortNumber.shortValue() != 0)
							{
								// if the controller requested stats of a single port we have to replace the physical port number with
								// the correct virtual number stored in the statsMap during the request
								if(vt_config.statsMap.containsKey(msg.getXid())) 
									if(virtPortNumber != vt_config.statsMap.get(msg.getXid()))
										continue;
								
								FVPortStatisticsReply portStats = VTCloneStatsMsg.ClonePortStats((FVPortStatisticsReply)ofStats);
								portStats.setPortNumber(virtPortNumber.shortValue());
								outStatsList.add((OFStatistics)portStats); 
								statsLen += portStats.getLength();
							}
						}
					}
				}
				
				for (OFStatistics ofStats : outStatsList){
					FVPortStatisticsReply portStats = (FVPortStatisticsReply) ofStats;
				}
				//replacing the statistic list with the new one containing virtual port numbers
				statsRep.setStatistics(outStatsList);
				statsRep.setLengthU(OFStatisticsReply.MINIMUM_LENGTH + statsLen);
				
			} else if (statsType == OFStatisticsType.QUEUE.getTypeValue()) {
				List<OFStatistics> inStatsList = statsRep.getStatistics();
				List<OFStatistics> outStatsList = new LinkedList<OFStatistics>();
				
				for (OFStatistics ofStats : inStatsList){
					Integer phyPort = Integer.valueOf((int)((FVQueueStatisticsReply) ofStats).getPortNumber());
					if(phyToVirtPortMap.containsKey(phyPort)){
						for (Integer virtPortNumber: phyToVirtPortMap.get(phyPort))
						{
							if(virtPortNumber.shortValue() != 0)
							{
								// if the controller requested stats of a single port we have to replace the physical port number with
								// the correct virtual number stored in the statsMap during the request
								if(vt_config.statsMap.containsKey(msg.getXid())) 
									if(virtPortNumber != vt_config.statsMap.get(msg.getXid()))
										continue;
								
								FVQueueStatisticsReply queueStats = VTCloneStatsMsg.CloneQueueStats((FVQueueStatisticsReply)ofStats);
								queueStats.setPortNumber(virtPortNumber.shortValue());
								outStatsList.add((OFStatistics)queueStats); 
								statsLen += queueStats.getLength();
							}
						}
					}
				}
				
				for (OFStatistics ofStats : outStatsList){
					FVQueueStatisticsReply queueStats = (FVQueueStatisticsReply) ofStats;
				}
				
				
				//replacing the statistic list with the new one containing virtual port numbers
				statsRep.setStatistics(outStatsList);
				statsRep.setLengthU(OFStatisticsReply.MINIMUM_LENGTH + statsLen);
			} else if (statsType == OFStatisticsType.FLOW.getTypeValue()) {
				List<OFStatistics> inStatsList = statsRep.getStatistics();
				
				for (OFStatistics ofStats : inStatsList){
					FVFlowStatisticsReply flowStats = (FVFlowStatisticsReply) ofStats;
					OFMatch match = flowStats.getMatch();
					Integer phyPort = Integer.valueOf(match.getInputPort());
					if(phyToVirtPortMap.containsKey(phyPort)){
						for (Integer virtPortNumber: phyToVirtPortMap.get(phyPort))
						{
							if(virtPortNumber.shortValue() != 0)
							{
								// we have to replace the physical input port number of the match with
								// the correct virtual number stored in the statsMap during the request
								if(vt_config.statsMap.containsKey(msg.getXid())) 
									if(virtPortNumber != vt_config.statsMap.get(msg.getXid()))
										continue;
								
								match.setInputPort(virtPortNumber.shortValue());
							}
						}
					}
				}
				
			} else if (statsType == OFStatisticsType.AGGREGATE.getTypeValue()) {
				
				if(statsRep.getLengthU() < 24) statsRep.setLengthU(24); //dirty patch for some NEC switches			
			}
			// cleaning the statsMap
			vt_config.statsMap.remove(msg.getXid());
		}
		else if (this.msg.getType() == OFType.PORT_STATUS) {
			// this part is slightly complicated because if a port included in a virtual link goes up or down, we have to intercept
			// the message and send two PORT_STATUS messages for the two endpoints ports of the virtual link
			FVPortStatus portStatus = (FVPortStatus) this.msg;
			OFPhysicalPort inPort = portStatus.getDesc();
			ArrayList <FVEventHandler> fvHandlersList = VeRTIGO.getInstance().getHandlersCopy();
			ret = 0; //port status messages are sent from here. Nothing will be sent from the fvSlicer
			
			VTLog.VTPortMapper("--------------------------------------------");
			VTLog.VTPortMapper("PORT_STATUS IN switchId: " + Long.toHexString(switchId) + " port: " + inPort.getPortNumber());
			
			boolean portState = false;
			byte reason = portStatus.getReason();

			if (reason == OFPortReason.OFPPR_ADD.ordinal()) {
				VTLog.VTPortMapper("PORT_STATUS IN Reason: ADD");
				portState = true;
			}
			else if (reason == OFPortReason.OFPPR_DELETE.ordinal()) {
				VTLog.VTPortMapper("PORT_STATUS IN Reason: DELETE");
				portState = false;
			}
			else if (reason == OFPortReason.OFPPR_MODIFY.ordinal()) {
				if(inPort.getState() == OFPortState.OFPPS_LINK_DOWN.getValue()) portState = false;
				else portState = true;
				VTLog.VTPortMapper("PORT_STATUS IN Reason: MODIFY" + " portState: " + portState);
			}
		
			// getting the mapping between the physical port that changed status and the virtual ports involved
			HashMap<Long,LinkedList<Integer>> classifierToVirtPortMap = vt_config.UpdatePortStatus(slicer.getSliceName(), switchInfo.getDPID(), inPort.getPortNumber(), portState);
			if(classifierToVirtPortMap == null) return 0;
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
			
			// now we modify the msg with the correct virtual port and we set the "from" classifier
			// this process is repeated until msgMap is empty
			for( FVClassifier currentSwitch: slicerPortMap.keySet()){
				for(Integer virtPort:slicerPortMap.get(currentSwitch)) {
					inPort.setPortNumber(virtPort.shortValue());
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
//			VTLog.VTPortMapper("--------------------------------------------");
//			VTLog.VTPortMapper("PACKET_OUT IN switchId: " + Long.toHexString(switchId) + " " + packetOut.toString());
			
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
							if ((U16.f(action_port) >= VTConfigInterface.baseVirtPortNumber) && (U16.f(action_port) <= U16.f(OFPort.OFPP_MAX.getValue())))
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
				VTLog.VTPortMapper("PACKET_OUT inPort: " + VirtInPort + " virtToPhyPortMap: " + VirtPortsMappings.toString());

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
								boolean changeMAC = false;
								LinkedList<Integer> OutPortMap = outPort.getValue();
					        	
								if(OutPortMap != null) {
									byte [] srcMAC = new byte[6];
									byte [] dstMAC = new byte[6];
									Integer linkId = OutPortMap.get(1);
//									VTLog.VTPortMapper("Index: " + index + " linkId: " + linkId + " inVirtPort: " + VirtInPort + " outPhyPort: " + OutPortMap.get(0));
									index = vt_config.vt_hashmap.updateFlowInfo(packetOut.getBufferId(),srcMAC, dstMAC,linkId,VTHashMap.FLOWINFO_ACTION.PACKETOUT.ordinal());
									// we change the MACs only if flow is entering (linkId>0) or exiting virtual links
									if(index != null && (linkId > 0 || VirtInPort >= VTConfigInterface.baseVirtPortNumber)) { 
										changeMAC = true;
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
										// output actions that not need MAC rewriting go at the beginning of the list
										if(changeMAC == true) outActions.add(actionOut.clone());
										else outActions.add(0,actionOut.clone()); 
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
		        List<Integer> stored_bufferId = vt_config.bufferIdMap.get(packetOut.getBufferId());
		        if(stored_bufferId != null)
		        {
//		        	VTLog.VTPortMapper("PACKET_OUT:  removed item vt_config.bufferIdMap " +  vt_config.bufferIdMap.get(packetOut.getBufferId()));
			        packetOut.setBufferId(stored_bufferId.get(0)); //restoring the saved buffer_id
			        vt_config.bufferIdMap.remove(packetOut.getBufferId());
//			        VTLog.VTPortMapper("PACKET_OUT:  vt_config.bufferIdMap" +  vt_config.bufferIdMap);
		        }
//		        VTLog.VTPortMapper("PACKET_OUT OUT switchId: " + Long.toHexString(switchId) + " " + packetOut.toString());
			} else return false;
		}
		else if (this.msg.getType() == OFType.FLOW_MOD) {
			FVFlowMod flowMod = (FVFlowMod) this.msg;
			OFMatch match = flowMod.getMatch();
			boolean restoreMAC = false;
			byte [] srcMAC = new byte[6];
			byte [] dstMAC = new byte[6];
			
			VTLog.VTPortMapper("--------------------------------------------");
			VTLog.VTPortMapper("FLOW_MOD IN switchId1: " + Long.toHexString(switchId) + " " + flowMod.toString());
//			VTLog.VTPortMapper("FLOW_MOD IN inPort wildcard: " + ((match.getWildcards() & OFMatch.OFPFW_IN_PORT)));
//			VTLog.VTPortMapper("FLOW_MOD IN flags: " + flowMod.getFlags());
			
			
			
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
				VTLog.VTPortMapper("FLOW_MOD  inPort: " + VirtInPort + " virtToPhyPortMap: " + VirtPortsMappings.toString());
				
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
		        	if (U16.f(flowMod.getOutPort()) < U16.f(OFPort.OFPP_MAX.getValue())) {	
			        	LinkedList<Integer> OutPortMap = VirtPortsMappings.get(Integer.valueOf(flowMod.getOutPort()));
			        	if(OutPortMap != null){
			        		flowMod.setOutPort(OutPortMap.get(0).shortValue());
			        	}
			        }      	
		        }

		        // now we remap the output ports included into the actions fields
		        ArrayList<OFAction> inActions = (ArrayList<OFAction>) flowMod.getActions();		        
		        if(inActions.isEmpty() == true) {
		        	// if command==OFPFC_MODIFY we don't know what was the previous output port and previous output link. 
					// If the previuos output link was a virtual link, we have to remove the entries in the middlepoint switches
					// of that link first. In this case there are no actions, this means that these packet will be dropped.
					if(flowMod.getCommand() == FVFlowMod.OFPFC_MODIFY || flowMod.getCommand() == FVFlowMod.OFPFC_MODIFY_STRICT){
						Integer oldLinkId = vt_config.removeRemoteFlowMatch(sliceName, switchId, match);
						if(oldLinkId > 0) {
							short origCommand = flowMod.getCommand();
							flowMod.setCommand(FVFlowMod.OFPFC_DELETE);
							vt_config.ManageMiddlePointEntries(sliceName, switchId, flowMod, oldLinkId);
							flowMod.setCommand(origCommand);
						}
					}
		        } else {
			        ArrayList<OFAction> outActions = new ArrayList<OFAction>();
			        int flowMod_size = FVFlowMod.MINIMUM_LENGTH;
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
									LinkedList<Integer> OutPortMap = outPort.getValue();
									if(OutPortMap != null) {
										boolean changeMAC = false;
										// here we send the actions to restore the MACs when the flow is tagged 
										if(restoreMAC && flowMod.getCommand() == FVFlowMod.OFPFC_ADD) {		
											changeMAC = true;
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
										
										// if command==OFPFC_MODIFY we don't know what was the previous output port and previous output link. 
										// If the previuos output link was a virtual link, we have to remove the entries in the middlepoint switches
										// of that link first. The new entries are added below.
										if(flowMod.getCommand() == FVFlowMod.OFPFC_MODIFY || flowMod.getCommand() == FVFlowMod.OFPFC_MODIFY_STRICT){
											Integer oldLinkId = vt_config.removeRemoteFlowMatch(sliceName, switchId, match);
											if(oldLinkId > 0) {
												short origCommand = flowMod.getCommand();
												flowMod.setCommand(FVFlowMod.OFPFC_DELETE);
												vt_config.ManageMiddlePointEntries(sliceName, switchId, flowMod, oldLinkId);
												flowMod.setCommand(origCommand);
											}
										}
										
										if(linkId > 0) {
											// here we remove the entries from the flowMatchTable and from middlepoint switches.
											if(flowMod.getCommand() == FVFlowMod.OFPFC_DELETE || flowMod.getCommand() == FVFlowMod.OFPFC_DELETE_STRICT){
												vt_config.removeRemoteFlowMatch(sliceName, switchId, match, linkId);
												vt_config.ManageMiddlePointEntries(sliceName, switchId, flowMod, linkId);
											}
										 // here we add the match to the table and we install the rules to the middlepoint switches
											if(flowMod.getCommand() == FVFlowMod.OFPFC_ADD || 
											   flowMod.getCommand() == FVFlowMod.OFPFC_MODIFY || flowMod.getCommand() == FVFlowMod.OFPFC_MODIFY_STRICT){
												vt_config.saveFlowMatch(sliceName, switchId, match, linkId);
												vt_config.ManageMiddlePointEntries(sliceName, switchId, flowMod, linkId);
											}
										}
										
										
										//comparing the output phy port with the input phy port set above
										if(OutPortMap.get(0)== match.getInputPort() && ((match.getWildcards() & OFMatch.OFPFW_IN_PORT) == 0 )){ 
											actionOut.setPort((short) OFPort.OFPP_IN_PORT.getValue());
										}
										else{
											actionOut.setPort(OutPortMap.get(0).shortValue());
										}
										
				
										try {
											// output actions that not need MAC rewriting go at the beginning of the list
											if(changeMAC == true) outActions.add(actionOut.clone());
											else outActions.add(0,actionOut.clone()); 
											flowMod_size += actionOut.getLengthU();
										} catch (CloneNotSupportedException e) {
											e.printStackTrace();
										}
									}							
								}
			        		}
							else{
								outActions.add(action);
								flowMod_size += action.getLengthU();
							}
						}
						else{
							outActions.add(action);
							flowMod_size += action.getLengthU();
						}
						
					}
			        flowMod.setActions(outActions);
			        flowMod.setLengthU(flowMod_size);
		        }
		        
		        
		        // we always need the FLOW_REMOVED messages from switches
		        if(flowMod.getCommand() == FVFlowMod.OFPFC_ADD || flowMod.getCommand() == FVFlowMod.OFPFC_MODIFY) flowMod.setFlags((short)(flowMod.getFlags() | ((short) (1 << 0))));

		        //sometimes a tagged flow (i.e. a flow exiting a virtual link that was controller through PACKET_OUTS) is 
		        //controlled through FLOW_MODS. Here we remove the tagged MACS.
		        List<Integer> stored_bufferId = vt_config.bufferIdMap.get(flowMod.getBufferId());
		        if(stored_bufferId != null) {
			        flowMod.setBufferId(stored_bufferId.get(0));  
			        if(restoreMAC) {
			        	match.setDataLayerSource(srcMAC);
						match.setDataLayerDestination(dstMAC);
			        }
			        vt_config.bufferIdMap.remove(flowMod.getBufferId());
		        }
//		        VTLog.VTPortMapper("FLOW_MOD OUT flags: " + flowMod.getFlags());
		        VTLog.VTPortMapper("FLOW_MOD OUT switchId: " + Long.toHexString(switchId) + " " + flowMod.toString());
			} else return false;
		}
		else if (this.msg.getType() == OFType.PORT_MOD) {
			FVPortMod portMod = (FVPortMod) this.msg;
			FVLog.log(LogLevel.INFO, slicer, "PORT_MOD");
			
			Integer virtPort = Integer.valueOf(portMod.getPortNumber());
			if(U16.f(virtPort.shortValue()) < U16.f(OFPort.OFPP_MAX.getValue())){
				// retrieve the mapping between virtual ports and (physical ports, virtual links)
				HashMap<Integer,LinkedList<Integer>> VirtPortsMappings = vt_config.GetVirtPortsMappings(slicer.getSliceName(), switchId);
				if(VirtPortsMappings.get(virtPort) != null) {
					LinkedList<Integer> InPortMap = VirtPortsMappings.get(virtPort);
		        	if(InPortMap != null )
		        		portMod.setPortNumber(InPortMap.get(0).shortValue());
		        	else return false;
				}
				else return false;
			}
			else return false;
		}
		else if (this.msg.getType() == OFType.STATS_REQUEST) {
			FVStatisticsRequest statsReq = (FVStatisticsRequest) this.msg;					
			HashMap<Integer,LinkedList<Integer>> VirtPortsMappings = vt_config.GetVirtPortsMappings(slicer.getSliceName(), switchId);
			
//			VTLog.VTPortMapper("------------------------------------------------");
//			VTLog.VTPortMapper("STATS_REQUEST TYPE: " + statsReq.getStatisticType());
			
			if(statsReq.getStatisticType() == OFStatisticsType.PORT){
				List<OFStatistics> inStatsList = statsReq.getStatistics();
				
				for (OFStatistics ofStats : inStatsList){
					FVPortStatisticsRequest portStats = (FVPortStatisticsRequest) ofStats;
					Integer virtPort = Integer.valueOf((int)(portStats.getPortNumber()));	
					if(U16.f(virtPort.shortValue()) < U16.f(OFPort.OFPP_MAX.getValue())){
						if(VirtPortsMappings.get(virtPort) != null) {
							LinkedList<Integer> InPortMap = VirtPortsMappings.get(virtPort);
				        	if(InPortMap != null ) {
				        		portStats.setPortNumber(InPortMap.get(0).shortValue());
				        		//in case of port != OFPP_NONE we must save the virtual port number. This number is used 
				        		//when we get the reply from the switch
				        		vt_config.statsMap.put(msg.getXid(), virtPort);
				        	}
				        	else return false;
						}
						else return false;
					}
				}
			}
			else if(statsReq.getStatisticType() == OFStatisticsType.QUEUE){
				List<OFStatistics> inStatsList = statsReq.getStatistics();
				
				for (OFStatistics ofStats : inStatsList){
					FVQueueStatisticsRequest queueStats = (FVQueueStatisticsRequest) ofStats;
					Integer virtPort = Integer.valueOf((int)(queueStats.getPortNumber()));	
					if(U16.f(virtPort.shortValue()) < U16.f(OFPort.OFPP_MAX.getValue())){
						if(VirtPortsMappings.get(virtPort) != null) {
							LinkedList<Integer> InPortMap = VirtPortsMappings.get(virtPort);
				        	if(InPortMap != null ) {
				        		queueStats.setPortNumber(InPortMap.get(0).shortValue());
				        		//in case of port != OFPP_NONE we must save the virtual port number. This number is used 
				        		//when we get the reply from the switch
				        		vt_config.statsMap.put(msg.getXid(), virtPort);
				        	}
				        	else return false;
						}
						else return false;
					}
				}
			}
			else if(statsReq.getStatisticType() == OFStatisticsType.FLOW){
				List<OFStatistics> inStatsList = statsReq.getStatistics();
				
				for (OFStatistics ofStats : inStatsList){
					FVFlowStatisticsRequest flowStats = (FVFlowStatisticsRequest) ofStats;
					Integer outVirtPort = Integer.valueOf((int)(flowStats.getOutPort()));	
					if(U16.f(outVirtPort.shortValue()) < U16.f(OFPort.OFPP_MAX.getValue())){
						if(VirtPortsMappings.get(outVirtPort) != null) {
							LinkedList<Integer> InPortMap = VirtPortsMappings.get(outVirtPort);
				        	if(InPortMap != null ) {
				        		flowStats.setOutPort(InPortMap.get(0).shortValue());
				        	}
				        	else return false;
						}
						else return false;
					}
					
					OFMatch match = flowStats.getMatch();
					Integer virtPort = Integer.valueOf(match.getInputPort());	
					if(VirtPortsMappings.get(virtPort) != null) {
						LinkedList<Integer> InPortMap = VirtPortsMappings.get(virtPort);
			        	if(InPortMap != null ) {
			        		match.setInputPort(InPortMap.get(0).shortValue());
			        		//Here we save the virtual in_port number. This number is used 
			        		//when we get the reply from the switch
			        		vt_config.statsMap.put(msg.getXid(), virtPort);
			        	}
			        	else return false;
					}
					else return false;						
				}
			}
			else if(statsReq.getStatisticType() == OFStatisticsType.AGGREGATE){
				List<OFStatistics> inStatsList = statsReq.getStatistics();
				
				for (OFStatistics ofStats : inStatsList){
					FVAggregateStatisticsRequest aggregateStats = (FVAggregateStatisticsRequest) ofStats;
					Integer outVirtPort = Integer.valueOf((int)(aggregateStats.getOutPort()));	
					if(U16.f(outVirtPort.shortValue()) < U16.f(OFPort.OFPP_MAX.getValue())){
						if(VirtPortsMappings.get(outVirtPort) != null) {
							LinkedList<Integer> InPortMap = VirtPortsMappings.get(outVirtPort);
				        	if(InPortMap != null ) {
				        		aggregateStats.setOutPort(InPortMap.get(0).shortValue());
				        	}
				        	else return false;
						}
						else return false;
					}
					
					OFMatch match = aggregateStats.getMatch();
					if(VirtPortsMappings.get(match.getInputPort()) != null) {
						LinkedList<Integer> InPortMap = VirtPortsMappings.get(match.getInputPort());
			        	if(InPortMap != null ) {
			        		match.setInputPort(InPortMap.get(0).shortValue());
			        	}
			        	else return false;
					}
					else return false;
						
				}				
			}
			else if(statsReq.getStatisticType() == OFStatisticsType.TABLE){
				//nothing to remap, just a reminder...
			}
		}
		
		return true;
	}
}

