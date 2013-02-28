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
 * @description This public class is used to modify the flow match
 */
public class VTChangeFlowMatch {

/**
 * @name VTChangeFM
 * @info This function returns a clone of the match without the input port. The original match is not modified
 * @authors roberto.doriguzzi matteo.gerola
 * @params OFMatch
 * @return OFMatch: a copy (cloned) of the original match without inPort and without L3-L4 fields in case of specific wildcards
 */
	static public OFMatch VTChangeFM(OFMatch m) {
		OFMatch match = m.clone();
		match.setWildcards(match.getWildcards() | OFMatch.OFPFW_IN_PORT);
		
		// TP ports are included in the lookup only if IP_PROTO is not a wildcard or the IP packet is a TCP or UDP
		if(((match.getWildcards() & OFMatch.OFPFW_NW_PROTO) > 0) || 
		   ((match.getNetworkProtocol() != (byte)6) && (match.getNetworkProtocol() != (byte)17))) {
			 match.setWildcards(match.getWildcards() | (OFMatch.OFPFW_TP_DST | OFMatch.OFPFW_TP_SRC));
		}
			
		// L3-L4 fields are included in the lookup only if the DL_TYPE is not a wildcard or for VLAN, ARP and IP packets
		if(((match.getWildcards() & OFMatch.OFPFW_DL_TYPE) > 0) || (match.getDataLayerType() != 0x800)){
			match.setWildcards(match.getWildcards() | (OFMatch.OFPFW_NW_DST_ALL | OFMatch.OFPFW_NW_SRC_ALL));
			match.setWildcards(match.getWildcards() | (OFMatch.OFPFW_NW_TOS));
			match.setWildcards(match.getWildcards() | (OFMatch.OFPFW_NW_PROTO));
			match.setWildcards(match.getWildcards() | (OFMatch.OFPFW_TP_DST | OFMatch.OFPFW_TP_SRC));
		}
		// if the packet is a VLAN (DL_TYPE=0x8100) the VLAN_PCP is included in the lookup
		else if(match.getDataLayerType() != 0x8100) { //VLAN packet
			match.setWildcards(match.getWildcards() & ~(OFMatch.OFPFW_DL_VLAN));
			match.setWildcards(match.getWildcards() & ~(OFMatch.OFPFW_DL_VLAN_PCP));
		}
		else if(match.getDataLayerType() != 0x806) { //ARP packet
			match.setWildcards(match.getWildcards() | (OFMatch.OFPFW_NW_DST_ALL | OFMatch.OFPFW_NW_SRC_ALL));
			match.setWildcards(match.getWildcards() | (OFMatch.OFPFW_NW_PROTO));
		}
		return match;
	}

	
/**
 * @name VTChangeFM
 * @info This function returns a clone of the match without the input port. The original match is not modified
 * @authors roberto.doriguzzi matteo.gerola
 * @params OFMatch
 * @params HashMap<Integer,String>: this map stores all the packet fields changed by actions, 
 * with corresponding values (converted in strings)
 * @return OFMatch: OFMatch without inPort
 */
	static public OFMatch VTChangeFM(OFMatch m, HashMap<Integer,String> changes) {
		OFMatch match = m.clone();
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
		return match;
	}
		
}
