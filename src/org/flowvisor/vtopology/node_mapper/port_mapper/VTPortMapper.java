package org.flowvisor.vtopology.node_mapper.port_mapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.flowvisor.FlowVisor;
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
import org.flowvisor.message.actions.FVActionOutput;
import org.flowvisor.message.statistics.*;
import org.flowvisor.slicer.FVSlicer;
import org.flowvisor.classifier.FVClassifier;
import org.flowvisor.events.FVEventHandler;
import org.flowvisor.exceptions.BufferFull;
import org.flowvisor.exceptions.MalformedOFMessage;
import org.flowvisor.vtopology.link_broker.VTLinkBroker;
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
import org.openflow.protocol.action.OFActionType;
import org.openflow.protocol.statistics.OFPortStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;
import org.openflow.util.U16;

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
*
*/

public class VTPortMapper {
	private OFMessage msg;
	public boolean end_point; //used by the FVSlicer to decide whether call the LinkBroker (end_point==false)
	private VTConfigInterface vt_config;
	private List<String> portnames;
	private ConcurrentHashMap<FVClassifier,LinkedList<Integer>> msgMap;
	public Integer nr_msgs;

	public VTPortMapper(OFMessage m, VTConfigInterface config) {
		this.msg = m;
		end_point = true;
		vt_config = config;
		portnames = new LinkedList<String>();
		portnames.add("eth");
		portnames.add("wlan");
		msgMap = new ConcurrentHashMap<FVClassifier,LinkedList<Integer>>();
		msgMap.clear();
		nr_msgs = 0; 
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
	 * @name UpLinkMapping
	 * @authors roberto.doriguzzi matteo.gerola
	 * @param FVSlicer slicer, String sliceName, long switchId, FVSendMsg from         
	 * @return true in case of end point, false otherwise
	 * @description This function checks whether the switch is an end point of the virtual link. If true, physical ports 
	 * 				are remapped into virtual ports
	 */
	public FVClassifier UpLinkMapping(FVSlicer slicer, String sliceName, FVClassifier switchInfo) {
		FVClassifier ret = switchInfo;
		long switchId = switchInfo.getDPID();
		
		if (this.msg.getType() == OFType.FEATURES_REPLY) {
			FVFeaturesReply reply = (FVFeaturesReply) this.msg;
			List<OFPhysicalPort> inPortList = reply.getPorts();
			List<OFPhysicalPort> outPortList = new LinkedList<OFPhysicalPort>();
			nr_msgs = 1;
			// DB initialization 
			for (OFPhysicalPort inPort: inPortList){
				vt_config.phyPortList.add((int)inPort.getPortNumber());
			}			
			vt_config.InitSwitchInfo(sliceName,switchId);
			
			end_point = true;  // features replies are always sent to the controller

			// port mapping
			for (OFPhysicalPort inPort: inPortList){
				Integer inPort_nr = Integer.valueOf((int)inPort.getPortNumber());
				if(vt_config.phyToVirtPortMap.containsKey(inPort_nr)){
					for (Integer virtPortNumber: vt_config.phyToVirtPortMap.get(inPort_nr))
					{
						System.out.println("FEATURES REPLY - SWITCH ID: 0x" + Integer.toHexString((int)switchId) + " PHYSICAL PORT: " + inPort.getPortNumber() + " VIRTUAL PORT: " + virtPortNumber);
						this.ChangePortDescription(inPort, virtPortNumber);
						outPortList.add(inPort); 
					}
				}
			}
			
			reply.setPorts(outPortList);
		}
		else if (this.msg.getType() == OFType.PACKET_IN) {
			FVPacketIn packetIn = (FVPacketIn) this.msg;
			OFMatch match = new OFMatch();
			nr_msgs = 1;
			// checking endpoint
			match.loadFromPacket(packetIn.getPacketData(), packetIn.getInPort());
			vt_config.phyPortList.addFirst((int)packetIn.getInPort());

			if(match.getDataLayerType() == 0x806 || match.getDataLayerType() == 0x800){
				vt_config.StorePktInFlowInfo(VTChangeFlowMatch.VTChangeFM(match.clone()), packetIn.getBufferId(), sliceName, switchId);
			}
			vt_config.GetSwitchInfo(sliceName,switchId,VTChangeFlowMatch.VTChangeFM(match.clone()));
			
			end_point = vt_config.isEndPoint;
			
			// port mapping
			if(end_point){
				if (packetIn.getInPort() <  U16.f(OFPort.OFPP_MAX.getValue())) {//OF "virtual ports" are not remapped
					LinkedList<Integer> virtPortList = vt_config.phyToVirtPortMap.get((int)packetIn.getInPort());
					if(virtPortList != null){
						packetIn.setInPort((short)virtPortList.getFirst().shortValue());
					}
				}
			}
		}
		else if (this.msg.getType() == OFType.FLOW_REMOVED) {
			FVFlowRemoved flowRem = (FVFlowRemoved) this.msg;
			OFMatch match = flowRem.getMatch();	
			nr_msgs = 1;
	        // if the flow_removed message contains a certain cookie, the flow was generated by the Link Broker module, i.e.
	        // it cannot be forwarded to controllers and the msg must be dropped
	        if(flowRem.getCookie() != 0x1fffffff){
				// checking endpoint
		        vt_config.phyPortList.addFirst((int)match.getInputPort());
				vt_config.GetSwitchInfo(sliceName,switchId,VTChangeFlowMatch.VTChangeFM(match.clone()));
				end_point = vt_config.isEndPoint;
	
				// port remapping
				if(end_point){
					if (match.getInputPort() < U16.f(OFPort.OFPP_MAX.getValue())) {//OF "virtual ports" are not remapped
						LinkedList<Integer> virtPortList = vt_config.phyToVirtPortMap.get((int)match.getInputPort());
						match.setInputPort((short)virtPortList.getFirst().shortValue());
					}
				}
				
				// updating flow database
				vt_config.RemoveFlowInfo(sliceName,switchId,VTChangeFlowMatch.VTChangeFM(match.clone())); 
	        }
	        else nr_msgs = 0;
		}
		else if (this.msg.getType() == OFType.STATS_REPLY) {
			FVStatisticsReply statsRep = (FVStatisticsReply) this.msg;
			short statsType = statsRep.getStatisticType().getTypeValue();
			int statsLen = 0;
			nr_msgs = 1;
			
			// getting the port mapping
			vt_config.phyPortList.clear();
			vt_config.phyPortList.addFirst((int)OFPort.OFPP_ALL.getValue());
			vt_config.GetSwitchInfo(sliceName,switchId,"");
			end_point = true; //stat replies are always sent to the controller since they are answers to stat requests
			
			if (statsType == OFStatisticsType.FLOW.getTypeValue()) { //match field contains the in_port
				List<OFStatistics> inStatsList = statsRep.getStatistics();
				
				FVLog.log(LogLevel.DEBUG, slicer, "STATS REPLY: " + statsRep.getStatisticType().toString());
				
				for (OFStatistics ofStats : inStatsList) {
					FVFlowStatisticsReply flowStats = (FVFlowStatisticsReply) ofStats;
					OFMatch match = flowStats.getMatch();
					LinkedList<Integer> virtPortList = vt_config.phyToVirtPortMap.get((int)match.getInputPort());
					match.setInputPort((short)virtPortList.getFirst().shortValue());
				}
				
			} else if (statsType == OFStatisticsType.PORT.getTypeValue()) { //all physical ports must be remapped to virtual ports
				List<OFStatistics> inStatsList = statsRep.getStatistics();
				List<OFStatistics> outStatsList = new LinkedList<OFStatistics>();
				
				FVLog.log(LogLevel.DEBUG, slicer, "STATS REPLY: " + statsRep.getStatisticType().toString());
				
				for (OFStatistics ofStats : inStatsList){
					Integer inPort_nr = Integer.valueOf((int)((FVPortStatisticsReply) ofStats).getPortNumber());
					if(vt_config.phyToVirtPortMap.containsKey(inPort_nr)){
						for (Integer virtPortNumber: vt_config.phyToVirtPortMap.get(inPort_nr))
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
					if(vt_config.phyToVirtPortMap.containsKey(inPort_nr)){
						for (Integer virtPortNumber: vt_config.phyToVirtPortMap.get(inPort_nr))
						{
							if(virtPortNumber.shortValue() != 0)
							{
								FVQueueStatisticsReply queueStats = VTCloneStatsMsg.CloneQueueStats((FVQueueStatisticsReply)ofStats);
								queueStats.setPortNumber(virtPortNumber.shortValue());
								System.out.println("switch: " + switchInfo.getDPID() + " phyport: " + inPort_nr + " virtport: " + queueStats.getPortNumber());
								outStatsList.add((OFStatistics)queueStats); 
								statsLen += queueStats.getLength();
							}
						}
					}
				}
				//replacing the statistic list with the new one containing virtual port numbers
				statsRep.setStatistics(outStatsList);
				statsRep.setLengthU(OFStatisticsReply.MINIMUM_LENGTH + statsLen);
				for (OFStatistics ofStats : outStatsList){
					System.out.println("outStatsList: " + ((FVPortStatisticsReply)ofStats).getPortNumber());
				}
			} else if (statsType == OFStatisticsType.AGGREGATE.getTypeValue()) {
				
				FVLog.log(LogLevel.DEBUG, slicer, "STATS REPLY: " + statsRep.getStatisticType().toString());
				if(statsRep.getLengthU() < 24) statsRep.setLengthU(24); //dirty patch for some NEC switches
				
			}
		}
		else if (this.msg.getType() == OFType.PORT_STATUS) {
			FVPortStatus portStatus = (FVPortStatus) this.msg;
			OFPhysicalPort inPort = portStatus.getDesc();
			ArrayList <FVEventHandler> fvHandlersList = FlowVisor.getInstance().getHandlersCopy();
				
			if(msgMap.isEmpty())
			{
				boolean portState = false;
				byte reason = portStatus.getReason();
	
				if (reason == OFPortReason.OFPPR_ADD.ordinal()) {
					portState = true;
				}
				else if (reason == OFPortReason.OFPPR_DELETE.ordinal()) {
					portState = false;
				}
				else if (reason == OFPortReason.OFPPR_MODIFY.ordinal()) {
					if(inPort.getState() == OFPortState.OFPPS_LINK_DOWN.getValue()) portState = false;
					else portState = true;
				}
			
				//System.out.println("port mapper PORT_STATUS DPID: " + switchInfo.getDPID() + " PhysPort: " + inPort.getPortNumber() + " state: " + portState);
				// getting the mapping between the physical port that changed status and the virtual ports involved
				vt_config.UpdatePortStatus(slicer.getSliceName(), switchInfo.getDPID(), inPort.getPortNumber(), portState);
	
				// filling the hashmap with DPID and virtual ports involved in the port status 
				for(long DPID: vt_config.classifierToVirtPortMap.keySet()) {
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
					//System.out.println("tmpClassifier: " + tmpClassifier.getName() + " PID: " + tmpClassifier.getDPID());
					// for each DPID we save a list of virtual ports and the total number if virtual ports that will change status
					if(tmpClassifier != null){
						portList.clear();
						for(Integer virtPort: vt_config.classifierToVirtPortMap.get(DPID)){ //remapping ports for this handler
							//System.out.println("port mapper PORT_STATUS DPID: " + DPID + " VirtualPort: " + virtPort);
							portList.add(virtPort);	
							nr_msgs++;
						}
						msgMap.put(tmpClassifier, portList);
					}
				}
			}
			
			// now we modify the msg with the correct virtual port and we set the "from" classifier
			// this process is repeated until msgMap is empty
			for( FVClassifier currentInfo: msgMap.keySet()){
				List<Integer> portList = msgMap.get(currentInfo);
				FVPortStatus ps = ((FVPortStatus)msg);
				OFPhysicalPort port = ps.getDesc();
				if(!portList.isEmpty()){
					ret = currentInfo;
					Integer virtPort = portList.get(0);
					this.ChangePortDescription(port, virtPort);
					portList.remove(0);
					break;
				}
				else msgMap.remove(currentInfo);
			}

		}
		else nr_msgs = 1;
				
		return ret;
	}
	
	
	/**
	 * @name DownLinkMapping
	 * @authors roberto.doriguzzi matteo.gerola
	 * @param String sliceName, long switchId         
	 * @return true in case of successful port mapping
	 */
	public boolean DownLinkMapping(FVSlicer slicer, String sliceName, long switchId) {
		
		/* mettere la virtual in port nella variabile results.virtPortId, la relativa porta fisica
		 * verrà messa nella results.phyPortId. Le porte delle azioni verranno mappate nella results.virtToPhyPortMap 
		 */
		
		if (this.msg.getType() == OFType.PACKET_OUT) {
			
			FVPacketOut packetOut = (FVPacketOut) this.msg;
			OFMatch match;
			
			// checking endpoint. packet_out that are not coming from the controller have a empty 
			// flow_match, therefore we need to load the flow_match of the corresponding packet_in
			
			vt_config.isFlowMod = false;
			if(packetOut.getInPort() == OFPort.OFPP_CONTROLLER.getValue()){
				match = new OFMatch();
				match.loadFromPacket(packetOut.getPacketData(), packetOut.getInPort());	
			}
			else {
				if(vt_config.GetPktInFlowInfo(packetOut.getBufferId(), sliceName, switchId)) 
					match = vt_config.flowMatch;
				else return false;

			}
			
			ArrayList<OFAction> inActions = new ArrayList<OFAction>();
			inActions = (ArrayList<OFAction>) packetOut.getActions();

			
			// loading virtual values into the "results" structure
			if(U16.f(packetOut.getInPort()) < U16.f(OFPort.OFPP_MAX.getValue()))
				vt_config.virtPortId = packetOut.getInPort();	// the input port must be remapped
			else vt_config.virtPortId = 0;
			
			for(OFAction action: inActions){ //also ports in the actions must be remapped
				if (action.getType().equals(OFActionType.OUTPUT)){
					short action_port = ((FVActionOutput)action).getPort();
					if (U16.f(action_port) <= U16.f(OFPort.OFPP_MAX.getValue()) 	||
						U16.f(action_port) == U16.f(OFPort.OFPP_FLOOD.getValue())	||
						U16.f(action_port) == U16.f(OFPort.OFPP_ALL.getValue()))
						vt_config.virtPortList.add((int)action_port);
				}
				
			}
			
			// here we pass the virtual port values through the results instance to get the mapping with physical ports
			// then we rebuild the packetOut message with the physical port values
			if(vt_config.GetVirttoPhyPortMap(slicer.getSliceName(), switchId, VTChangeFlowMatch.VTChangeFM(match.clone()))){
				short VirtPort = packetOut.getInPort();
				
				// first of all we replace the in_port virtual value with the corresponding physical port number
		        if (U16.f(VirtPort) < U16.f(OFPort.OFPP_MAX.getValue())) {
		        	packetOut.setInPort(vt_config.phyPortId);
		        } 
		        
		     // now we remap the output ports included into the actions fields
		        ArrayList<OFAction> outActions = new ArrayList<OFAction>();
		        int packetOut_size = FVPacketOut.MINIMUM_LENGTH;
		        for(OFAction action: inActions){ //also ports in the actions must be remapped
		        	FVActionOutput actionOut = (FVActionOutput)action;

					if (action.getType().equals(OFActionType.OUTPUT)){
						if (actionOut.getPort() == OFPort.OFPP_ALL.getValue() || 
							actionOut.getPort() == OFPort.OFPP_FLOOD.getValue()) //broadcast output action
						{
							
							for (Entry<Integer, Integer> outPort: vt_config.virtToPhyPortMap.entrySet()){
								if(outPort.getValue()== packetOut.getInPort()){
									actionOut.setPort((short) OFPort.OFPP_IN_PORT.getValue());
								}
								else{
									actionOut.setPort(outPort.getValue().shortValue());
								}

								try {
									outActions.add(actionOut.clone());
									packetOut_size += actionOut.getLengthU();
								} catch (CloneNotSupportedException e) {
									e.printStackTrace();
								}
							}
						}
						else if (U16.f(actionOut.getPort()) < U16.f(OFPort.OFPP_MAX.getValue()))
						{//unicast output actions		
							int virtport = ((FVActionOutput)action).getPort();
							Integer phyport = vt_config.virtToPhyPortMap.get(virtport);
							
							if(phyport != null) // check whether the controller is using a valid port number
							{		
								if(phyport == (int)match.getInputPort()){
									actionOut.setPort((short) OFPort.OFPP_IN_PORT.getValue());
								}
								else{
									actionOut.setPort(phyport.shortValue());
								}
								outActions.add(action);
								packetOut_size += action.getLengthU();
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
		        
		        
			} else return false;	
		}
		else if (this.msg.getType() == OFType.FLOW_MOD) {
			FVFlowMod flowMod = (FVFlowMod) this.msg;
			OFMatch match = new OFMatch();
			match = flowMod.getMatch();
			
			vt_config.isFlowMod = true;
			vt_config.hardTO = flowMod.getHardTimeout();
			vt_config.idleTO = flowMod.getIdleTimeout();
			 
			ArrayList<OFAction> inActions = new ArrayList<OFAction>();
			inActions = (ArrayList<OFAction>) flowMod.getActions();
			int action_counter = 0;
			// loading virtual values into vt_config structure
			vt_config.virtPortId = match.getInputPort();	// the input port must be remapped
			for(OFAction action: inActions){ //also ports in the actions must be remapped
				if (action.getType().equals(OFActionType.OUTPUT)){
					vt_config.virtPortList.add((int)(((FVActionOutput)action).getPort()));
				}
				action_counter++;
			}
			
			if(match.getWildcards() == -1) return true;
			if(vt_config.virtPortId == 0 && action_counter == 0) return true; //nothing to remap
			
			// here we pass the virtual port values through the results instance to get the mapping with physical ports
			// then we rebuild the packetOut message with the physical port values
			
			if(vt_config.GetVirttoPhyPortMap(slicer.getSliceName(), switchId, VTChangeFlowMatch.VTChangeFM(match.clone()))){
				// first of all we replace the in_port virtual value with the corresponding physical port number
		        if (match.getInputPort() < U16.f(OFPort.OFPP_MAX.getValue())) {
					match.setInputPort((short)vt_config.phyPortId);
		        }
		        
		        // now we remap the output ports included into the actions fields
		        ArrayList<OFAction> outActions = new ArrayList<OFAction>();
		        int flowMod_size = FVFlowMod.MINIMUM_LENGTH;
		        for(OFAction action: inActions){ //also ports in the actions must be remapped
		        	FVActionOutput actionOut = (FVActionOutput)action;
		        	
					if (action.getType().equals(OFActionType.OUTPUT)){
						if (actionOut.getPort() == OFPort.OFPP_ALL.getValue() || 
							actionOut.getPort() == OFPort.OFPP_FLOOD.getValue()) //broadcast output action
						{
							
							for (Entry<Integer, Integer> outPort: vt_config.virtToPhyPortMap.entrySet()){
								if(outPort.getValue() == match.getInputPort()){
									actionOut.setPort((short) OFPort.OFPP_IN_PORT.getValue());
								}
								else{
									actionOut.setPort(outPort.getValue().shortValue());
								}
								
								try {
									outActions.add(actionOut.clone());
									flowMod_size += actionOut.getLengthU();
								} catch (CloneNotSupportedException e) {
									e.printStackTrace();
								}
							}
						}
						else{//unicast output action
							int virtport = ((FVActionOutput)action).getPort();
							Integer phyport = vt_config.virtToPhyPortMap.get(virtport);
							
							if(phyport != null) // check whether the controller is using a valid port number
							{	
								if(phyport == (int)match.getInputPort()){
									actionOut.setPort((short) OFPort.OFPP_IN_PORT.getValue());
								}
								else{
									actionOut.setPort(phyport.shortValue());
								}
								outActions.add(action);
								flowMod_size += action.getLengthU();
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
			}
			else return false;
		}
		else if (this.msg.getType() == OFType.PORT_MOD) {
			FVPortMod portMod = (FVPortMod) this.msg;
			FVLog.log(LogLevel.INFO, slicer, "PORT_MOD");
			
			Integer port_nr = Integer.valueOf(portMod.getPortNumber());
			// loading virtual values into vt_config structure
			vt_config.virtPortList.add((int)OFPort.OFPP_ALL.getValue());
			vt_config.GetVirttoPhyPortMap(slicer.getSliceName(), switchId, "");
			if(U16.f(port_nr.shortValue()) < U16.f(OFPort.OFPP_MAX.getValue())){
				if(vt_config.virtToPhyPortMap.get(port_nr) != null)
					portMod.setPortNumber(vt_config.virtToPhyPortMap.get(port_nr).shortValue());
				else return false;
			}
			else return false;
		}
		else if (this.msg.getType() == OFType.STATS_REQUEST) {
			FVStatisticsRequest statsReq = (FVStatisticsRequest) this.msg;			
			FVLog.log(LogLevel.DEBUG, slicer, "STATS REQUEST: " + statsReq.getStatisticType().toString());
			
			// loading virtual values into vt_config structure
			vt_config.virtPortList.add((int)OFPort.OFPP_ALL.getValue());
			vt_config.GetVirttoPhyPortMap(slicer.getSliceName(), switchId, "");
			System.out.println("virtToPhyPortMap: " + vt_config.virtToPhyPortMap.toString());
			
			if(statsReq.getStatisticType() == OFStatisticsType.PORT){
				List<OFStatistics> inStatsList = statsReq.getStatistics();
				
				for (OFStatistics ofStats : inStatsList){
					FVPortStatisticsRequest portStats = (FVPortStatisticsRequest) ofStats;
					Integer inPort_nr = Integer.valueOf((int)(portStats.getPortNumber()));	
					if(U16.f(inPort_nr.shortValue()) < U16.f(OFPort.OFPP_MAX.getValue())){
						if(vt_config.virtToPhyPortMap.get(inPort_nr) != null)
							portStats.setPortNumber(vt_config.virtToPhyPortMap.get(inPort_nr).shortValue());
						else portStats.setPortNumber(OFPort.OFPP_NONE.getValue());
					}
				}
			}
			else if(statsReq.getStatisticType() == OFStatisticsType.QUEUE){
				List<OFStatistics> inStatsList = statsReq.getStatistics();
				
				for (OFStatistics ofStats : inStatsList){
					FVQueueStatisticsRequest queueStats = (FVQueueStatisticsRequest) ofStats;
					Integer inPort_nr = Integer.valueOf((int)(queueStats.getPortNumber()));			
					if(U16.f(inPort_nr.shortValue()) < U16.f(OFPort.OFPP_MAX.getValue())){
						if(vt_config.virtToPhyPortMap.get(inPort_nr) != null)
							queueStats.setPortNumber(vt_config.virtToPhyPortMap.get(inPort_nr).shortValue());
						else return false;
					}
					else return false; // not a valid port number
				}
	
			}
			else if(statsReq.getStatisticType() == OFStatisticsType.FLOW){
				List<OFStatistics> inStatsList = statsReq.getStatistics();
				
				for (OFStatistics ofStats : inStatsList){
					FVFlowStatisticsRequest flowStats = (FVFlowStatisticsRequest) ofStats;
					Integer outPort_nr = Integer.valueOf((int)(flowStats.getOutPort()));			
					if(U16.f(outPort_nr.shortValue()) < U16.f(OFPort.OFPP_MAX.getValue())){
						if(vt_config.virtToPhyPortMap.get(outPort_nr) != null)
							flowStats.setOutPort(vt_config.virtToPhyPortMap.get(outPort_nr).shortValue());
						else flowStats.setOutPort(OFPort.OFPP_NONE.getValue());
					}
					OFMatch match = flowStats.getMatch();
					if(vt_config.virtToPhyPortMap.get(match.getInputPort()) != null)
						match.setInputPort(vt_config.virtToPhyPortMap.get(match.getInputPort()).shortValue());
				}
				
				
			}
			else if(statsReq.getStatisticType() == OFStatisticsType.AGGREGATE){
				List<OFStatistics> inStatsList = statsReq.getStatistics();
				
				for (OFStatistics ofStats : inStatsList){
					FVAggregateStatisticsRequest aggregateStats = (FVAggregateStatisticsRequest) ofStats;
					Integer outPort_nr = Integer.valueOf((int)(aggregateStats.getOutPort()));			
					if(U16.f(outPort_nr.shortValue()) < U16.f(OFPort.OFPP_MAX.getValue())){
						if(vt_config.virtToPhyPortMap.get(outPort_nr) != null)
							aggregateStats.setOutPort(vt_config.virtToPhyPortMap.get(outPort_nr).shortValue());
						else aggregateStats.setOutPort(OFPort.OFPP_NONE.getValue());
					}
					
					OFMatch match = aggregateStats.getMatch();
					if(vt_config.virtToPhyPortMap.get(match.getInputPort()) != null)
						match.setInputPort(vt_config.virtToPhyPortMap.get(match.getInputPort()).shortValue());
				}
				
				
			}
			else if(statsReq.getStatisticType() == OFStatisticsType.TABLE){
				//nothing to remap, just a reminder...
			}
		}
		
		return true;
	}
}
