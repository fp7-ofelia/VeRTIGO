/**
 * 
 */
package org.flowvisor.vtopology.utils;

import java.util.HashMap;
import java.util.Set;
import org.openflow.protocol.OFMatch;
import org.openflow.util.HexString;

/**
 * @name VTChangeFlowMatch
 * @authors roberto.doriguzzi, matteo.gerola
 * @description This public class is used to allow virtual topology database in the process of storing 
 * flowmatches. It converts the fm in string, and potentially stores not the original fm, but a new version, 
 * if some actions could modify it. 
 */
public class VTChangeFlowMatch {

/**
 * @name VTChangeFM
 * @info This function return the flowmatch as a string without the inport
 * @authors roberto.doriguzzi matteo.gerola
 * @params OFMatch
 * @return String: OFMatch without inPort, converted to String
 */
	static public String VTChangeFM(OFMatch match) {
		match.setWildcards(match.getWildcards() | OFMatch.OFPFW_IN_PORT);
		
		//in case of ARP packets we ignore some fields
		if(match.getDataLayerType() == 0x806){
			match.setWildcards(match.getWildcards() | (OFMatch.OFPFW_NW_DST_ALL | OFMatch.OFPFW_NW_SRC_ALL));
			match.setWildcards(match.getWildcards() | (OFMatch.OFPFW_NW_TOS));
			match.setWildcards(match.getWildcards() | (OFMatch.OFPFW_TP_DST | OFMatch.OFPFW_TP_SRC));
		}
		return match.toString();
	}

	
/**
 * @name VTChangeFM
 * @info This function return the flowmatch as a string without the inport
 * @authors roberto.doriguzzi matteo.gerola
 * @params OFMatch
 * @params HashMap<Integer,String>: this map stores all the packet fields changed by actions, 
 * with corresponding values (converted in strings)
 * @return String: OFMatch without inPort, converted to String
 */
	static public String VTChangeFM(OFMatch match, HashMap<Integer,String> changes) {
		Set<Integer> keySetPortMap = changes.keySet();
		for(int flowField:keySetPortMap){
			if (flowField == OFMatch.OFPFW_DL_SRC)
				match.setDataLayerSource(changes.get(flowField));
			else if (flowField == OFMatch.OFPFW_DL_DST)
				match.setDataLayerDestination(changes.get(flowField));
			else if (flowField == OFMatch.OFPFW_DL_TYPE)
				match.setDataLayerType(Short.valueOf(changes.get(flowField)));
			else if (flowField == OFMatch.OFPFW_DL_VLAN)
				match.setDataLayerVirtualLan(Short.valueOf(changes.get(flowField)));
			else if (flowField == OFMatch.OFPFW_DL_VLAN_PCP)
				match.setDataLayerVirtualLanPriorityCodePoint(Byte.valueOf(changes.get(flowField)));
			else if (flowField == OFMatch.OFPFW_NW_PROTO)
				match.setNetworkProtocol(Byte.valueOf(changes.get(flowField)));
			else if (flowField == OFMatch.OFPFW_NW_SRC_ALL)
				match.setNetworkSource(Integer.valueOf(changes.get(flowField)));
			else if (flowField == OFMatch.OFPFW_NW_DST_ALL)
				match.setNetworkDestination(Integer.valueOf(changes.get(flowField)));
			else if (flowField == OFMatch.OFPFW_NW_TOS)
				match.setNetworkTypeOfService(Byte.valueOf(changes.get(flowField)));
			else if (flowField == OFMatch.OFPFW_TP_SRC)
				match.setTransportSource(Short.valueOf(changes.get(flowField)));
			else if (flowField == OFMatch.OFPFW_TP_DST)
				match.setTransportDestination(Short.valueOf(changes.get(flowField)));
		}
		match.setWildcards(match.getWildcards() | OFMatch.OFPFW_IN_PORT);
		return match.toString();
	}
		
}
