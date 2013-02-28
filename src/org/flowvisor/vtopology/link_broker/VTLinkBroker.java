package org.flowvisor.vtopology.link_broker;

import java.util.LinkedList;
import java.util.List;

import org.flowvisor.classifier.FVClassifier;
import org.flowvisor.classifier.FVSendMsg;
import org.flowvisor.message.FVFlowMod;
import org.flowvisor.message.FVPacketIn;
import org.flowvisor.message.FVPacketOut;
import org.flowvisor.message.actions.FVActionOutput;
import org.flowvisor.slicer.FVSlicer;
import org.flowvisor.vtopology.topology_configurator.VTConfigInterface;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.util.U16;

/**
 * @name VTLinkBroker
 * @author roberto.doriguzzi matteo.gerola
 * @description	Class used to directly control switches that are middle points of virtual links. 
 * Therefore these switches are hidden to the controller. 
 *
 */

public class VTLinkBroker {

	private FVPacketIn packetIn;
	private VTConfigInterface vt_config;
	
	public VTLinkBroker(FVPacketIn m, VTConfigInterface config) {
		this.packetIn = m;
		vt_config = config;
	}
	
	/**
	 * @name Main
	 * @authors roberto.doriguzzi matteo.gerola
	 * @param FVSendMsg fromSwitch,short outputPort         
	 * @return void
	 * @description This function builds a flow mod message that is sent to the switch. This message is used to 
	 * 				control flows arrived to switches that are not end points of virtual links. 
	 */
	
	public void Main(FVSendMsg fromSwitch) {
		
		OFMatch match = new OFMatch();
        match.loadFromPacket(this.packetIn.getPacketData(), this.packetIn.getInPort());

        // exiting when detecting invalid output ports
        if(!(vt_config.phyPortId > 0 && U16.f(vt_config.phyPortId) < U16.f(OFPort.OFPP_MAX.getValue()))) return;
        
        //the packet_in is handled in the same way it was handled by the slice controller
        //for end-point switches
        if(!vt_config.isFlowMod){
        	int inPhyPort = packetIn.getInPort();
        	FVPacketOut packetOut = new FVPacketOut();
        	packetOut.setXid(packetIn.getXid());
        	packetOut.setInPort((short)inPhyPort);
        	packetOut.setBufferId(packetIn.getBufferId());
        	List<OFAction> actions = new LinkedList<OFAction>();
			FVActionOutput action = new FVActionOutput();
			
			action.setMaxLength((short) 0);
			if (inPhyPort == vt_config.phyPortId)
				action.setPort((short) OFPort.OFPP_IN_PORT.getValue());
			else
				action.setPort((short) vt_config.phyPortId);
			actions.add(action);
			packetOut.setActions(actions);
			packetOut.setActionsLength(action.getLength());
			
			if (packetIn.getBufferId() == 0xffffffff) {  //the packet is not buffered
	            byte[] packetData = packetIn.getPacketData();
	            packetOut.setLength(U16.t(packetOut.getLength() + packetOut.getActionsLength() + packetData.length));
	            packetOut.setPacketData(packetData);
	        } else
	        	packetOut.setLength(U16.t(packetOut.getLength() + packetOut.getActionsLength()));
			
			fromSwitch.sendMsg(packetOut,fromSwitch);
        }
        else{
			FVFlowMod flowMod=new FVFlowMod();
			flowMod.setXid(packetIn.getXid());
			flowMod.setBufferId(packetIn.getBufferId());
			flowMod.setCommand((short) 0);
			//the cookie is used to recognize and block the flow_removed messages that cannot be sent to controllers as
			//they refer to flowMods installed by the LinkBroker (see VTPortMapper.java ->this.msg.getType() == OFType.FLOW_REMOVED) 
			flowMod.setCookie(0x1fffffff); 
			flowMod.setFlags((short) 1);  // we want to receive the flow_removed messages
			flowMod.setHardTimeout((short) vt_config.hardTO);
			flowMod.setIdleTimeout((short) vt_config.idleTO);
			int inPhyPort = packetIn.getInPort();
			match.setInputPort((short) inPhyPort);
			flowMod.setMatch(match);
			flowMod.setOutPort((short) OFPort.OFPP_NONE.getValue());
			flowMod.setPriority((short) 0);
			
			List<OFAction> actions = new LinkedList<OFAction>();
			FVActionOutput action = new FVActionOutput();
			
			action.setMaxLength((short) 0);
			if (inPhyPort == vt_config.phyPortId)
				action.setPort((short) OFPort.OFPP_IN_PORT.getValue());
			else
				action.setPort((short) vt_config.phyPortId);
			actions.add(action);
			
			flowMod.setActions(actions);
			flowMod.setLength(U16.t(flowMod.getLength()+action.getLength()));
			
			fromSwitch.sendMsg(flowMod, fromSwitch);
        }
	}
	
}
