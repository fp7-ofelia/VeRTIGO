/**
 * 
 */
package org.flowvisor.vtopology.topology_configurator;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.flowvisor.classifier.FVClassifier;
import org.flowvisor.classifier.FVSendMsg;
import org.flowvisor.config.ConfigError;
import org.flowvisor.message.FVFlowMod;
import org.flowvisor.message.FVPacketIn;
import org.flowvisor.message.FVPacketOut;
import org.flowvisor.slicer.FVSlicer;
import org.flowvisor.vtopology.utils.VTLog;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFType;
/**
 * @name VTConfigInterface
 * @authors roberto.doriguzzi, matteo.gerola
 * @description Public class used to load/save configuration values of 
 * 				Virtual Topologies 
 */
public class VTConfigInterface {
	public HashMap<Integer,List<Integer>> bufferIdMap;
	public HashMap<Integer, Integer> statsMap;  //map that keeps track of the virtual port in the stats requests
	private VTSqlDb dbQuery;
	public VTHashMap vt_hashmap;
	private String sliceName;
	public final static short baseVirtPortNumber = 101;
	
/**
 * @name Constructor
 * @info This function initializes the interface variables and the database
 * @authors roberto.doriguzzi matteo.gerola
 */
	public VTConfigInterface(String sliceName){
		this.sliceName = sliceName;
		bufferIdMap = new HashMap<Integer,List<Integer>>();
		statsMap = new HashMap<Integer,Integer>();
		dbQuery = VTSqlDb.getInstance();
		vt_hashmap = VTHashMap.getInstance(sliceName);
	}
	
	
/**
 * @name VTInitSwitchInfo
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function is used when a FeatureReply packet arrives and initializes the db for the switch
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @param List<OFPhysicalPort> phyPortList = list of the physical ports of the switch
 */
	public void InitSwitchInfo(String sliceId, long switchId,List<OFPhysicalPort> phyPortList)  {
		try {
			dbQuery.sqlDbInitSwitchInfo(sliceId, switchId, phyPortList);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RuntimeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ConfigError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
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
 */
	public HashMap<Long,LinkedList<Integer>> UpdatePortStatus(String sliceId, long switchId, int portId, boolean portStatus)  {
		HashMap<Long,LinkedList<Integer>> ret=null;
		try {
			ret = dbQuery.sqlDbUpdatePortStatus(sliceId, switchId, portId, portStatus);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = null;
		} 
		return ret;
	}	
	
/**
 * @name GetEndPoint
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function tells you whether a switch is an end-point of a link
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @param int linkId = virtual link identifier
 * @return isEndPoint = TRUE if the switch is an end point, FALSE otherwise
 */
	public boolean GetEndPoint(String sliceName,long switchId, int linkId) {
		boolean ret=false;
		try {
			ret = dbQuery.sqlGetEndPoint(sliceName,switchId,linkId);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = false;
		}		
		return ret;
	}	
	
/**
 * @name GetPhyToVirtMap
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function returns the virtual port which connects the Virtual Link "linkId" with to the switch "switchId" 
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @param int linkId = link identifier
 * @return Integer = the virtual port number
 */
	public Integer GetPhyToVirtMap(String sliceName,long switchId, int linkId) {
		Integer virtPortId = null;
		try {
			virtPortId = dbQuery.sqlGetPhyToVirtMap(sliceName, switchId, linkId); 
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			virtPortId = null;
		}		
		return virtPortId;
	}	
	
/**
 * @name GetPhyToVirtMap
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function returns the list of virtual ports mapped on each physical port
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @return HashMap<Integer,LinkedList<Integer>> = the mapping between physical and virtual ports
 */
	public HashMap<Integer,LinkedList<Integer>> GetPhyToVirtMap(String sliceName,long switchId) {
		HashMap<Integer,LinkedList<Integer>> map;
		try {
			map = dbQuery.sqlGetPhyToVirtMap(sliceName, switchId); 
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			map = null;
		}		
		return map;
	}	
		
	
		
/**
 * @name GetVirtPortsMappings
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function returns the mapping between virtual ports and (physical ports, virtual links)
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @return VirtToPhyPortMap = mapping between virtual ports and (physical ports, virtual links) 
 */
	public HashMap<Integer,LinkedList<Integer>> GetVirtPortsMappings(String sliceId, long switchId) {
		HashMap<Integer,LinkedList<Integer>> ret=null;
		try {
			ret = dbQuery.sqlGetVirtPortsMappings(sliceId, switchId);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = null;
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
	public short GetNewLinkId (String sliceId, String linkName) {
		short ret = -1;
		try {
			ret = dbQuery.sqlGetNewLinkId (sliceId, linkName, this);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = -1;
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
			
			// when a link is deleted, we also need to remove static entries
			if(status == 1) {
				RemoveStaticMiddlePointEntries(sliceId, link.linkId);
			}
			ret = dbQuery.sqlUpdateVirtualLink (sliceId, link, status, this);
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
		vt_hashmap.removeSlice(sliceId);
		
		try {
			ret = dbQuery.sqlRemoveSliceInfo (sliceId);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = false;
		}
		return ret;
	}
	
/**
 * @name InstallStaticMiddlePointEntries
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function installs static flow entries on middlepoint switches of virtual links 
 * @param FVSlicer slicer = pointer to the slicer
 * @param FVClassifier fromSwitch = classifier  
 * @param short inPort = the input port
 * @param int buffer_id = the buffer id for the flow_mod message
 * @param int linkId = the virtual link identifier. linkId==0 means all virtual links crossing the middlepoint
 * @return ret = boolean return code (TRUE: ok, FALSE: error in the function)  
 */
	public boolean InstallStaticMiddlePointEntries(String sliceId, FVSlicer slicer, FVClassifier fromSwitch, int linkId, short inPort, int buffer_id) {
		boolean ret = true;
		HashMap <Integer,LinkedList<Integer>> LinkList;
		
		try {
			LinkList = dbQuery.sqlGetMiddlePointLinks(sliceId, fromSwitch.getDPID(),linkId);
			VTLog.VTConfigInterface("InstallStaticMiddlePointEntries Slice: " + sliceId + " Switch: " + Long.toHexString(fromSwitch.getDPID()) + " LinkList: " + LinkList.toString());
			
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		
		vt_hashmap.InstallStaticMiddlePointEntries(slicer, fromSwitch, inPort, buffer_id, LinkList); 
		
		return ret;
	}	
	
/**
 * @name RemoveStaticMiddlePointEntries
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function removes static flow entries from middlepoint switches of virtual links 
 * @param String sliceId = slice name
 * @param int linkId = the virtual link identifier. linkId==0 is not permitted
 * @return ret = boolean return code (TRUE: ok, FALSE: error in the function)  
 */
	public boolean RemoveStaticMiddlePointEntries(String sliceId, int linkId) {
		if(linkId <= 0) return false;
		HashMap <Long,LinkedList<Integer>> LinkList = null;
		
		try {
			LinkList = dbQuery.sqlGetLinkMiddlePoints(sliceId, linkId); 
			
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}

		if(LinkList.size() > 0)
			vt_hashmap.RemoveStaticMiddlePointEntries(linkId, LinkList); 
		
		return true;
	}	
	
/**
 * @name InstallMiddlePointEntries
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function installs/removes flow entries on middlepoint switches of virtual links as a consequence of flow_mods arrived to endpoints from the controller
 * @param String sliceId, Long switchId, FVFlowMod flowMod, int linkId
 * 
 * @return ret = boolean return code (TRUE: ok, FALSE: error in the function)  
 */
	public boolean ManageMiddlePointEntries(String sliceId, Long switchId, FVFlowMod flowMod, int linkId) {
		boolean ret = true;
		HashMap <Long,LinkedList<Integer>> MiddlePointList;
		HashMap <LinkedList<Long>,Integer> HopList;
		
		if(linkId == 0) return false;
		
		try {
			MiddlePointList = dbQuery.sqlGetLinkMiddlePoints(sliceId, linkId);
			HopList = dbQuery.sqlGetLinkHops(sliceId, linkId); 
			VTLog.VTConfigInterface("ManageMiddlePointEntries Slice: " + sliceId + " MiddlePointList: " + MiddlePointList.toString());
			VTLog.VTConfigInterface("ManageMiddlePointEntries Slice: " + sliceId + " HopList: " + HopList.toString());
			
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		
		vt_hashmap.ManageMiddlePointEntries(switchId, flowMod, linkId, MiddlePointList, HopList); 
		
		return ret;
	}
	
/**
 * @name GetVirtualPortLinkMapping
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function returns the mapping between virtual ports and virtual links
 * @param String sliceId = name of the slice  
 * @param long switchId = switch identifier  
 * @return ret = the mapping between virtual ports and virtual links
 */
	public HashMap <Integer,Integer> GetVirtualPortLinkMapping(String sliceId, long switchId) {
		HashMap <Integer,Integer> ret = null;
		
		try {
			ret = dbQuery.sqlGetVirtualPortLinkMapping(sliceId,switchId);
			VTLog.VTConfigInterface("Slice: " + sliceId + " Switch: " + Long.toHexString(switchId) + " LinkMap: " + ret);
			
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		
		return ret;
	}
	
/**
 * @name saveFlowMatch
 * @authors roberto.doriguzzi matteo.gerola
 * @info When a flow enters a virtual link, we save its match, the linkID, the DPID of the other endpoint of the link plus the output port
 * @param String sliceId, long switchId, OFMatch match, int linkId
 * @return boolean = TRUE if we find the remote endpoint, FALSE otherwise 
 */
	public boolean saveFlowMatch(String sliceId, long switchId, OFMatch match, int linkId) {
		List <Object> ret = null;
		
		if(linkId == 0) return false;
		
		try {
			ret = dbQuery.sqlGetRemoteEndPoint(sliceId,switchId,linkId);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(ret == null) return false;
		else {
			vt_hashmap.updateMatchTable(match, ret, VTHashMap.ACTION.ADD.ordinal());
			return true;
		}
		
	}
/**
 * @name removeRemoteFlowMatch
 * @authors roberto.doriguzzi matteo.gerola
 * @info Delete the entry added to the table by with the saveFlowMatch method
 * @param String sliceId, long switchId, OFMatch match, int linkId
 * @return boolean = TRUE if we find the remote endpoint, FALSE otherwise 
 */
	public boolean removeRemoteFlowMatch(String sliceId, long switchId, OFMatch match, int linkId) {
		List <Object> ret = null;
		
		if(linkId == 0) return false;
		
		try {
			ret = dbQuery.sqlGetRemoteEndPoint(sliceId,switchId,linkId);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(ret == null) return false;
		else {
			vt_hashmap.updateMatchTable(match, ret, VTHashMap.ACTION.DELETE.ordinal());
			return true;
		}
		
	}
	
/**
 * @name removeRemoteFlowMatch
 * @authors roberto.doriguzzi matteo.gerola
 * @info Modifies the flowMatchTable when the controller sends a flowMod with command==OFPFC_MODIFY or command==OFPFC_MODIFY_STRICT.
 * @param String sliceId, long switchId, OFMatch match, int linkId
 * @return void
 */
	public Integer removeRemoteFlowMatch(String sliceId, long switchId, OFMatch match) {
		 
		List <Object> ret = null;
		LinkedList<Integer> linkIds = null;
		
		try {
			linkIds = dbQuery.sqlGetVirtualLinks(sliceId, switchId);
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} 
		
		for(Integer linkId : linkIds) {
			try {
				ret = dbQuery.sqlGetRemoteEndPoint(sliceId,switchId,linkId);
				VTLog.VTConfigInterface("removeRemoteFlowMatch remote endpoint: " + Long.toHexString((Long)ret.get(0)));
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
			if(ret != null){
				if(vt_hashmap.updateMatchTable(match, ret, VTHashMap.ACTION.DELETE.ordinal()) == true)
					return linkId;
			}
		}
		
		return 0;
	}	
	
/**
 * @name removeFlowMatch
 * @authors roberto.doriguzzi matteo.gerola
 * @info Removes the match entries with virtual link "linkId" from the flowMatchTable 
 * @param String sliceId, long switchId, OFMatch match, int linkId
 * @return void
 */
	public void removeFlowMatch(String sliceId, long switchId, OFMatch match, int linkId) {
		 
		 // deleting the entries for the switch which sent the flow removed message
		 List<Object> info = new LinkedList<Object>();
		 info.add((Object)Long.valueOf(switchId));  //middlePoint switchId
		 info.add((Object)Integer.valueOf(linkId)); //linkId
		 vt_hashmap.updateMatchTable(match, info, VTHashMap.ACTION.DELETE.ordinal());
	}


			
	public boolean ManageVLinkLLDP(String sliceId, long switchId, Integer virtPortId, byte[] bs) {
		boolean ret = true;
		try {
			ret = dbQuery.sqlManageVLinkLLDP(sliceId, switchId, virtPortId, bs);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ret = false;
		}
		return ret;
	}
	
}
