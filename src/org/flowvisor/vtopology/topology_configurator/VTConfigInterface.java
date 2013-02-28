/**
 * 
 */
package org.flowvisor.vtopology.topology_configurator;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;

import org.flowvisor.config.ConfigError;
import org.flowvisor.message.FVFlowMod;
import org.flowvisor.message.FVPacketOut;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
/**
 * @name VTConfigInterface
 * @authors roberto.doriguzzi, matteo.gerola
 * @description Public class used to load/save configuration values of 
 * 				Virtual Topologies 
 */
public class VTConfigInterface {
	public boolean isEndPoint; 	// property of a switch: end point of a virtual link
	public short phyPortId;
	public short virtPortId;
	public int linkId;
	public OFMatch flowMatch;
	public LinkedList<Integer> virtPortList;
	public LinkedList<Integer> phyPortList;
	public HashMap<Integer,Integer> virtToPhyPortMap;
	public HashMap<Integer,LinkedList<Integer>> phyToVirtPortMap;
	public HashMap<Integer,Integer> controlChannelMap; 	
	public boolean isFlowMod;
	public int idleTO;
	public int hardTO;
	public HashMap<Long,LinkedList<Integer>> classifierToVirtPortMap;
	private VTSqlDb dbQuery;

	
/**
 * @name Constructor
 * @info This function initializes the interface variables and the database
 * @authors roberto.doriguzzi matteo.gerola
 */
	public VTConfigInterface(){
		isEndPoint = false;
		phyPortId = 0;
		virtPortId = 0;
		linkId = 0;
		flowMatch = new OFMatch();
		virtPortList = new LinkedList<Integer>();
		phyPortList = new LinkedList<Integer>();
		virtToPhyPortMap = new HashMap<Integer,Integer>();
		controlChannelMap = new HashMap<Integer,Integer>();
		phyToVirtPortMap = new HashMap<Integer,LinkedList<Integer>>();
		classifierToVirtPortMap = new HashMap<Long,LinkedList<Integer>>();
		dbQuery = VTSqlDb.getInstance();
	}

	
/**
 * @name clear
 * @info This function clears all the variables
 * @authors roberto.doriguzzi matteo.gerola
 */
	public void Clear(){
		isEndPoint=false;
		phyPortId = 0;
		virtPortId = 0;
		linkId = 0;
		virtPortList.clear();
		phyPortList.clear();
		virtToPhyPortMap.clear();
		controlChannelMap.clear();
		phyToVirtPortMap.clear();
		classifierToVirtPortMap.clear();
		isFlowMod = false;
		idleTO = 0;
		hardTO = 0;
	}
	
	
/**
 * @name InitDB
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function provide basic database functionalities: connect, check db existence, table creation, user authentication
 */
	public void InitDB()  {
		try {
			dbQuery.sqlDbInit();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	
	
/**
 * @name VTInitSwitchInfo
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function is used when a FeatureReply packet arrives. VT initialises the db for the switch, adding virtual 
 * ports (access or link) associated to the physical ports provided
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @param phyPortList = list of the physical ports of the switch in this slice
 * @return phyToVirtPortMap = mapping between physical and virtual ports
 * @return ret = boolean return code (TRUE: ok, FALSE: error in the function)  
 */
	public boolean InitSwitchInfo(String sliceId, long switchId)  {
		boolean ret=true;
		try {
			ret = dbQuery.sqlDbInitSwitchInfo(sliceId, switchId, this);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = false;
		} catch (RuntimeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = false;
		} catch (ConfigError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = false;
		}		
		return ret;
	}

	
/**
 * @name UpdateSwitchInfo
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function is used when there are changes in physical port status
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @param int portId = port identifier
 * @param boolean portStatus = new port status (0 = down, 1 = up)
 * @return classifierToVirtPortMap = mapping between switchId and virtual ports
 * @return ret = boolean return code (TRUE: ok, FALSE: error in the function)  
 */
	public boolean UpdatePortStatus(String sliceId, long switchId, int portId, boolean portStatus)  {
		boolean ret=true;
		try {
			ret = dbQuery.sqlDbUpdatePortStatus(sliceId, switchId, portId, portStatus, this);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = false;
		} 
		return ret;
	}	
	
/**
 * @name GetLinkId
 * @authors roberto.doriguzzi matteo.gerola
 * @info Returns the identifier of the virtual link crossed by the flow.  
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @param String flowMatch = packet match
 * @return ret = the virtual link identifier  
 */
	public boolean GetLinkId(String sliceId, long switchId, String flowMatch, int inputPort) {
		boolean ret=false;
		try {
			linkId = dbQuery.sqlDbGetLinkId(sliceId, switchId,flowMatch, inputPort, this);
			ret = true;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = false;
		}		
		return ret;
	}	
	
	
/**
 * @name GetSwitchInfo
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function is used when a packet from a switch is directed to a controller. 
 * A list of physical ports is mapped in virtual ports. 
 * It also returns informations about the switch position in the link (endPoint, if not => send pkt to the link_broker)
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @param String flowMatch = packet match
 * @return isEndPoint = TRUE if the switch is an end point, FALSE otherwise
 * @return phyToVirtPortMap = mapping between physical and virtual ports (only if isEnd Point == TRUE)
 * @return phyPortId = physical output port (only if isEnd Point == FALSE)
 * @return ret = boolean return code (TRUE: ok, FALSE: error in the function)  
 */
	public boolean GetSwitchInfo(String sliceId, long switchId, String flowMatch) {
		boolean ret=true;
		try {
			ret = dbQuery.sqlDbGetSwitchInfo(sliceId, switchId,flowMatch, this);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = false;
		}		
		return ret;
	}
	
		
/**
 * @name GetVirttoPhyPortMap
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function is used to convert a list of virtual ports sent from a controller to a switch
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @param String flowMatch = packet match (L2+L3) 
 * @param int priority = flow entry priority (only for flow_mod messages)
 * @param virtPortId = input virtual port
 * @param virtPortList = list of the output virtual ports of the switch in this slice
 * @return phyPortId = input physical port     
 * @return virtToPhyPortMap = mapping between virtual ports and physical ports
 * @return ret = boolean return code (TRUE: ok, FALSE: error in the function)  
 */
	public boolean GetVirttoPhyPortMap(OFMessage msg, String sliceId, long switchId, String flowMatch, int priority) {
		boolean ret=true;
		try {
			ret = dbQuery.sqlGetVirttoPhyPortMap(sliceId, switchId,flowMatch, priority, this);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			System.out.println("EXCEPTION CATCH ------------" + Long.toHexString(switchId) + "------------");
			if (msg.getType() == OFType.PACKET_OUT) {
				FVPacketOut packetOut = (FVPacketOut) msg;
				System.out.println("PACKET_OUT: " + packetOut.toString());
			
			}
			else if (msg.getType() == OFType.FLOW_MOD) {
				FVFlowMod flowMod = (FVFlowMod) msg;
				System.out.println("FLOW_MOD: " + flowMod.toString());
			
			}
			e.printStackTrace();
			ret = false;
		}		
		return ret;
	}
	
	
/**
 * @name StorePktInFlowInfo
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function is used to store a flowMatch associated with an unique bufferId
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @param String flowMatch = packet match (L2+L3) 
 * @param int bufferId = packet In identifier
 */
	public boolean StorePktInFlowInfo(String flowMatch, int bufferId, String sliceId, long switchId) {
		boolean ret=true;
		try {
			ret = dbQuery.sqlStorePktInFlowInfo(flowMatch, bufferId, sliceId, switchId);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = false;
		}		
		return ret;
	}
	
	
/**
 * @name GetPktInFlowInfo
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function get a flowMatch from a bufferId
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @param int bufferId = packet In identifier
 * @return String flowMatch = packet match (L2+L3) 
 */
	public boolean GetPktInFlowInfo(int bufferId, String sliceId, long switchId) {
		boolean ret=true;
		try {
			ret = dbQuery.sqlGetPktInFlowInfo(bufferId, sliceId, switchId, this);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = false;
		}		
		return ret;
	}
	
		
/**
 * @name GetControlChannelMap
 * @authors roberto.doriguzzi matteo.gerola
 * @return ret = boolean return code (TRUE: ok, FALSE: error in the function)  
 */
	public boolean GetControlChannelMap(String sliceId, long switchId, String flow_match) {
		boolean ret=true;
		
		return ret;
	}

	
/**
 * @name GetNewLinkId
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function (used by FVUserAPIImpl) return the current max linkId used within a slice
 * @param String sliceId = name of the slice     
 * @return linkId = current linkId maximum value in the slice
 * @return ret = boolean return code (TRUE: ok, FALSE: error in the function)  
 */
	public boolean GetNewLinkId (String sliceId, String linkName) {
		boolean ret = true;
		try {
			ret = dbQuery.sqlGetNewLinkId (sliceId, linkName, this);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = false;
		}
		return ret;
	}


/**
 * @name UpdateVirtualLink
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function update the db when a link is updated from config
 * @param String sliceId = name of the slice     
 * @param VTLink link = link class
 * @param int status = status of the link (0: added, 1: deleted, 2: modified)
 * @return ret = boolean return code (TRUE: ok, FALSE: error in the function)  
 */
	public boolean UpdateVirtualLink (String sliceId, VTLink link, int status) {
		boolean ret = true;
		try {
			ret = dbQuery.sqlUpdateVirtualLink (sliceId, link, status, this);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = false;
		}
		return ret;
	}

/**
 * @name RemoveFlowInfo
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function remove all the information stored in the database regarding a flow, 
 * when a flow_rem arrived to flowvisor
 * @param String sliceId = name of the slice 
 * @param long switchId = dpid of the switch
 * @param String flowMatch = packet match (L2+L3)  
 * @return ret = boolean return code (TRUE: ok, FALSE: error in the function)  
 */
	public boolean RemoveFlowInfo(String sliceId, long switchId, String flow_match) {
		boolean ret = true;
		try {
			ret = dbQuery.sqlRemoveFlowInfo (sliceId, switchId, flow_match);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = false;
		}
		return ret;
	}	
	
/**
 * @name RemoveSliceInfo
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function remove all the information stored in the database regarding a slice, 
 * when it's removed from the configuration
 * @param String sliceId = name of the slice  
 * @return ret = boolean return code (TRUE: ok, FALSE: error in the function)  
 */
	public boolean RemoveSliceInfo(String sliceId) {
		boolean ret = true;
		try {
			ret = dbQuery.sqlRemoveSliceInfo (sliceId);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = false;
		}
		return ret;
	}
	
	public boolean ManageVLinkLLDP(String sliceId, long switchId, byte[] bs) {
		boolean ret = true;
		try {
			ret = dbQuery.sqlManageVLinkLLDP(sliceId, switchId, bs, this);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = false;
		}
		return ret;
	}
	
}
