package org.flowvisor.vtopology.topology_configurator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.flowvisor.VeRTIGO;
import org.flowvisor.classifier.FVClassifier;
import org.flowvisor.classifier.FVSendMsg;
import org.flowvisor.events.FVEventHandler;
import org.flowvisor.flows.FlowSpaceUtil;
import org.flowvisor.message.FVFlowMod;
import org.flowvisor.message.FVPacketIn;
import org.flowvisor.message.Slicable;
import org.flowvisor.message.actions.FVActionOutput;
import org.flowvisor.slicer.FVSlicer;
import org.flowvisor.vtopology.utils.VTChangeFlowMatch;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.U16;

public class VTHashMap implements Runnable {
	private HashMap<Integer,HashMap<Integer,long []>> MACMap;  // <flow_index,<linkId,MAC[MACINDEX]>>
	private HashMap<Integer,Long> TimeMap;  // <flow_index,time>
	private HashMap<Long,HashMap<String,List<Object>>> matchVlinkMap; // <switchID,<match,[time,linkId,phyPortId,virtPortId]>>
	private static HashMap<String,String> SliceMap = new HashMap<String,String>(); 
	private static HashMap<String,VTHashMap> instances = new HashMap<String,VTHashMap>();
	private int MACIndex = 0;
	private static short SliceIndex = 0;
	private String sliceName;
	private Thread cleanMaps; 
	public static long IANA_OUI=0x000000005E000000;
	protected static final short DEFAULT_PRIORITY = 100;
	protected static final short ADDRESS_LENGTH = 6;
	protected static final short EXPIRATION_TIME = 5000;
	protected static final short LOOP_TIME = 5000;
	
	private static enum MACINDEX {
		LINK_ID,
		SRCMAC,
		DSTMAC,
		SRCMAClink,
		DSTMAClink
	}
	
	public static enum FLOWINFO_ACTION {
		PACKETIN,
		PACKETOUT,
		FLOWMOD,
		DELETE
	}
	
	
	private static enum INFOINDEX {
		SWITCH_ID,
		LINK_ID,
		PHY_PORT,
		VIRT_PORT
	}
	
	public static enum ACTION {
		DELETE,
		ADD,
		MODIFY
	}
	
	private VTHashMap(FVSlicer slicer, String sliceName) {
		MACMap = new HashMap<Integer,HashMap<Integer,long []>>();
		TimeMap = new HashMap<Integer,Long>();
		matchVlinkMap = new HashMap<Long,HashMap<String,List<Object>>>();
		this.sliceName = sliceName;
		instances.put(sliceName, this);
		cleanMaps = new Thread(this, "cleanMaps");
		cleanMaps.setPriority(Thread.currentThread().getPriority() - 1); // lower priority
		cleanMaps.start(); // see run() at the bottom
	}

/**
 * @name getInstance
 * @authors roberto.doriguzzi matteo.gerola
 * @param FVSlicer slicer,String sliceName
 * @return pointer to the VTHashMap instance
 * @description this static method is used to get the pointer to the slice-specific instance of VTHashMap
 */	
	public static VTHashMap getInstance(FVSlicer slicer,String sliceName) {
		if (instances.get(sliceName) == null) {
			VTHashMap.saveSliceId(sliceName);
			instances.put(sliceName, new VTHashMap(slicer, sliceName));
		}
		return instances.get(sliceName);
 	}
	
	public void Clear() {
		this.MACMap.clear();
		this.MACIndex = 0;
	}
	
	private static long unsignedByteToLong(byte b) {
	    return (long) b & 0xFF;
	}
	
	
	public static long mac2long(byte addr[]) {
	    long address = 0L;
		if (addr != null) {
		    if (addr.length == ADDRESS_LENGTH) {
			address = unsignedByteToLong(addr[5]);
			address |= (unsignedByteToLong(addr[4]) << 8);
			address |= (unsignedByteToLong(addr[3]) << 16);
			address |= (unsignedByteToLong(addr[2]) << 24);
			address |= (unsignedByteToLong(addr[1]) << 32);
			address |= (unsignedByteToLong(addr[0]) << 40);
		    } 
		} 
		return address;
	}
	
	private static byte [] long2mac(long addr) {
	    byte [] address = new byte[ADDRESS_LENGTH];
		
		address[0] = (byte)(addr >> 40);
		address[1] = (byte)(addr >> 32);
		address[2] = (byte)(addr >> 24);
		address[3] = (byte)(addr >> 16);
		address[4] = (byte)(addr >> 8);
		address[5] = (byte)(addr);

		return address;
	}
	
	private long getFakeSrcMAC(int linkId) {
		short sliceId = VTHashMap.getSliceId(this.sliceName);
		return (long) (IANA_OUI | (sliceId << 12) | linkId);
	}
	
/**
 * @name updateFlowInfo
 * @authors roberto.doriguzzi matteo.gerola
 * @param int id, byte[] srcMAC, byte [] dstMAC, int linkId, int action     
 * @return Integer
 * @description saves/updates/deletes the MACMap hashmap entries. Only this 
 * 				method is allowed to modify the HashMap (synchronized)
 */
	public synchronized Integer updateFlowInfo (int id, byte[] srcMAC, byte [] dstMAC, int linkId, int action){
		Integer ret = null;
		java.sql.Timestamp  sqlDate = new java.sql.Timestamp(new java.util.Date().getTime());
		
//		System.out.println("updateFlowInfo PARAMETERS case: " + action + " linkId: " + linkId + " index: " + id + " srcMAC: " + Long.toHexString(mac2long(srcMAC)) + " dstMAC: " + Long.toHexString(mac2long(dstMAC)));
			
		switch(action) {
			case 0 : {//Switch->Controller PACKET_IN at the end of a virtual link
				
				if((mac2long(srcMAC) & 0x0000FFFFFF000000L) == IANA_OUI &&
				   (mac2long(dstMAC) & 0x0000FFFFFF000000L) == IANA_OUI	) {
					int index = ((int)(mac2long(dstMAC) & 0x0000000000FFFFFF));
					HashMap<Integer,long []> tempMap = this.MACMap.get(index);
					if(tempMap != null) {
						this.TimeMap.put(index, sqlDate.getTime()); 					//update timestamp
						long [] baseList =	tempMap.get(0);
						long [] linkList =	tempMap.get(linkId);
						if(baseList != null && linkList != null) {
							if(index == (linkList[MACINDEX.DSTMAClink.ordinal()]  & 0x0000000000FFFFFF)) { 							
								System.arraycopy(long2mac(baseList[MACINDEX.SRCMAC.ordinal()]), 0, srcMAC, 0, ADDRESS_LENGTH); // passing the original srcMAC to the controller
								System.arraycopy(long2mac(baseList[MACINDEX.DSTMAC.ordinal()]), 0, dstMAC, 0, ADDRESS_LENGTH); //passing the original dstMAC to the controller
								ret = index;
							}
						}
						else ret = null;
					}
					else ret = null;
				}
				else { //Beginning of the virtual link
					int index = this.MACIndex;
					long [] tempList = new long [MACINDEX.DSTMAClink.ordinal() + 1];
					while(this.MACMap.containsKey(index) || (index == 0)) {
						if(++index > 0xFFFFFF) index = 1;
						this.MACIndex = index;
					}	
					 	
					tempList[MACINDEX.LINK_ID.ordinal()] = (long)0;			//the flow is not arriving from a virtual link
					tempList[MACINDEX.SRCMAC.ordinal()] = mac2long(srcMAC); //saving the original srcMAC value
					tempList[MACINDEX.DSTMAC.ordinal()] = mac2long(dstMAC);	//saving the original dstMAC value	
					tempList[MACINDEX.SRCMAClink.ordinal()] = 0; 			//initial setting fake srcMAC to 0
					tempList[MACINDEX.DSTMAClink.ordinal()] = 0;  			//initial setting fake dstMAC to 0
					
					HashMap<Integer,long []> tempMap = new HashMap<Integer,long []>();
					tempMap.put(0, tempList);  // linkid = 0			
					this.MACMap.put(index, tempMap);
					this.TimeMap.put(index, sqlDate.getTime()); 					//update timestamp
					ret = index;
				}
					
				break;
			}
			case 1: {//Controller->Switch PACKET_OUT actions
				HashMap<Integer,long []> tempMap = this.MACMap.get(id);
				if(tempMap != null) {
					TimeMap.put(id, sqlDate.getTime()); 					//update timestamp
					
					if(linkId > 0) {// out to a virtual link 
						long [] tempList = new long [MACINDEX.DSTMAClink.ordinal() + 1];  // is "null" only for linkId>0
						tempList[MACINDEX.SRCMAClink.ordinal()] = getFakeSrcMAC(linkId); 	//this will be used as srcMACfake
						tempList[MACINDEX.DSTMAClink.ordinal()] = (IANA_OUI | id);  	//this will be used as dstMACfake
						System.arraycopy(long2mac(getFakeSrcMAC(linkId)), 0, srcMAC, 0, ADDRESS_LENGTH); 
						System.arraycopy(long2mac(IANA_OUI | id), 0, dstMAC, 0, ADDRESS_LENGTH);
						tempMap.put(linkId, tempList);
						ret = id;
					}
					else { // out to a standard link
						long [] tempList =	tempMap.get(0);
						if(tempList != null) {
							System.arraycopy(long2mac(tempList[MACINDEX.SRCMAC.ordinal()]), 0, srcMAC, 0, ADDRESS_LENGTH); 
							System.arraycopy(long2mac(tempList[MACINDEX.DSTMAC.ordinal()]), 0, dstMAC, 0, ADDRESS_LENGTH); 
							ret = id;
						}
						else ret = null;
					}
				}
				else ret = null;
				break;
			}
			case 2: {//Controller->Switch FLOW_MOD actions
				HashMap<Integer,long []> tempMap = this.MACMap.get(id);
				
				if(tempMap != null && linkId > 0) {							
					TimeMap.put(id, sqlDate.getTime()); 					//update timestamp
					long [] linkList =	tempMap.get(linkId);				 
					if(linkList != null) {
//						for(int i=0;i<MACINDEX.DSTMAClink.ordinal() + 1;i++) {
//							System.out.println("linkList: " + Long.toHexString(linkList[i]));
//						}
						
						if(id == (linkList[MACINDEX.DSTMAClink.ordinal()]  & 0x0000000000FFFFFF)) { 							
							System.arraycopy(long2mac(linkList[MACINDEX.SRCMAClink.ordinal()]), 0, srcMAC, 0, ADDRESS_LENGTH); // passing the original srcMAC to the controller
							System.arraycopy(long2mac(linkList[MACINDEX.DSTMAClink.ordinal()]), 0, dstMAC, 0, ADDRESS_LENGTH); //passing the original dstMAC to the controller
							ret = id;
						}
					}
					else ret = null;
				}
				else ret = null;
				break;
			}
			
			case 3: {//Deleting old entries
				this.MACMap.remove(id);
				this.TimeMap.remove(id);
				break;
			}
			
			default: 
				ret = null;
				break;
		}
//		System.out.println("updateFlowInfo RETURN VALUES  srcMAC: " + Long.toHexString(mac2long(srcMAC)) + " dstMAC: " + Long.toHexString(mac2long(dstMAC)));
		return ret;
	}

	
/**
 * @name saveSliceId
 * @authors roberto.doriguzzi matteo.gerola
 * @param String sliceName         
 * @return void
 * @description saves the slice Name along with an unique identifier
 */
	public synchronized static void saveSliceId(String sliceName) {
		short index = SliceIndex;
		if(!SliceMap.containsKey(sliceName)) {
			while(SliceMap.containsKey(Integer.toString(index)) || (index == 0)) {
				if(++index > 0x000FFF) index = 1;
				SliceIndex = index;
			}
			
			SliceMap.put(sliceName, Long.toString(SliceIndex));
			SliceMap.put(Long.toString(SliceIndex),sliceName);
		}
//		System.out.println("SliceMap: " + SliceMap.toString());
	}
	
/**
 * @name removeSlice
 * @authors roberto.doriguzzi matteo.gerola
 * @param String sliceName         
 * @return void
 * @description removes the entries of the slice with name "sliceName"
 */
	public synchronized static void removeSlice(String sliceName) {
		String Id = SliceMap.get(sliceName);
		SliceMap.remove(Id);
		SliceMap.remove(sliceName);
		instances.remove(sliceName);
	}
	
/**
 * @name getSliceId
 * @authors roberto.doriguzzi matteo.gerola
 * @param String sliceName         
 * @return long
 * @description returns the identifier associated to the sliceName
 */
	private static short getSliceId(String sliceName) {
		String id = SliceMap.get(sliceName);
		if(id == null) return -1;
		else return Short.valueOf(id);
	}
	
/**
 * @name getSliceId
 * @authors roberto.doriguzzi matteo.gerola
 * @param long id         
 * @return String
 * @description returns the slice Name associated to the identifier "id"
 */
	public static String getSliceName(long id) {
		String sliceName = SliceMap.get(Long.toString(id));
		if(sliceName == null) return null;
		else return sliceName;
	}


/** 
 * @name getLinkId
 * @authors roberto.doriguzzi matteo.gerola
 * @param long switchId, OFMatch match         
 * @return short
 * @description returns the linkId for flows exiting a virtual link (and sometimes also when flow crosses a middlepoint)
 */
	public short getLinkId(long switchId, OFMatch match, short inPort) {
		short ret = 0;
		byte [] srcMAC = match.getDataLayerSource();
		if((VTHashMap.mac2long(srcMAC) & 0x0000FFFFFF000000L) == IANA_OUI) { //tagged flows
			ret = (short) (VTHashMap.mac2long(srcMAC) & 0x0000000000000FFFL);
		}
		else { // non-tagged flows
			HashMap<String,List<Object>> linkPointSwitch = matchVlinkMap.get(switchId); //retrieving the information stored at the beginning of the vlink
//			System.out.println("getLinkId switchId: " + Long.toHexString(switchId));
			if(linkPointSwitch != null) {
				OFMatch savedMatch = VTChangeFlowMatch.VTChangeFM(match);
				for (Entry<String,List<Object>> matchHashMap: linkPointSwitch.entrySet()){
					OFMatch tmpMatch = new OFMatch();
					tmpMatch.fromString(matchHashMap.getKey());
					if(tmpMatch.subsumes(savedMatch)) {
						List<Object> tmpList = matchHashMap.getValue();
						if(tmpList != null) {
							if(((Integer)tmpList.get(INFOINDEX.PHY_PORT.ordinal())).shortValue() == inPort) {
								return ((Integer)tmpList.get(INFOINDEX.LINK_ID.ordinal())).shortValue();
							}
						}
					}
				}
			}
		}
		return ret;
	}
	
/**
 * @name updateMatchTable
 * @authors roberto.doriguzzi matteo.gerola
 * @param OFMatch match, List<Object> remoteDP,int action        
 * @return void
 * @description modifies the content of the match table. An entry is added when a flow enters a virtual link from an endpoint. 
 * 				The entry  which contains the switchId and inPort of the remote endpoint of the virtual link. This info is used to recognize the 
 * 				linkId when the flow arrives to the remoet endpoint 
 */
	public synchronized void updateMatchTable(OFMatch match, List<Object> info,int action) {
		OFMatch tmpMatch = VTChangeFlowMatch.VTChangeFM(match);
		if (action == ACTION.ADD.ordinal()){
			HashMap<String,List<Object>> remoteLinkPoint = matchVlinkMap.get((Long)info.get(INFOINDEX.SWITCH_ID.ordinal()));
			if(remoteLinkPoint == null) remoteLinkPoint = new HashMap<String,List<Object>>();
			LinkedList<Object> tmpList = new LinkedList<Object>();
			tmpList.add(INFOINDEX.SWITCH_ID.ordinal(),(Long)info.get(INFOINDEX.SWITCH_ID.ordinal()));				
			tmpList.add(INFOINDEX.LINK_ID.ordinal(),(Integer) info.get(INFOINDEX.LINK_ID.ordinal()));  	
			tmpList.add(INFOINDEX.PHY_PORT.ordinal(),(Integer) info.get(INFOINDEX.PHY_PORT.ordinal()));  	
			tmpList.add(INFOINDEX.VIRT_PORT.ordinal(),(Integer) info.get(INFOINDEX.VIRT_PORT.ordinal()));  			
			remoteLinkPoint.put(tmpMatch.toString(), tmpList);
			matchVlinkMap.put((Long)info.get(0), remoteLinkPoint);  //saving the current switchId with the info of the remote endpoint 
		} 
		else if (action == ACTION.DELETE.ordinal()){
			HashMap<String,List<Object>> remoteLinkPoint = matchVlinkMap.get((Long)info.get(INFOINDEX.SWITCH_ID.ordinal()));
			if(remoteLinkPoint != null){
				List<Object> tmpList = remoteLinkPoint.get(tmpMatch.toString());
				if(tmpList != null) {
					if(info.get(INFOINDEX.LINK_ID.ordinal()) == tmpList.get(INFOINDEX.LINK_ID.ordinal())) {
						remoteLinkPoint.remove(tmpMatch.toString());
						if(remoteLinkPoint.size() == 0) matchVlinkMap.remove((Long)info.get(INFOINDEX.SWITCH_ID.ordinal()));	
//						System.out.println("matchVlinkMap: " + matchVlinkMap);
					}
				}
			}
			
		} 
		else if (action == ACTION.MODIFY.ordinal()){
			
		}
	}	

/**
 * @name InstallStaticMiddlePointEntries
 * @authors roberto.doriguzzi matteo.gerola
 * @param FVSlicer slicer, FVSendMsg fromSwitch, short inPort, Integer buffer_id, HashMap <Integer,LinkedList<Integer>> linkList       
 * @return void
 * @description installs permanent flow entries for middle point switches for tagged flows. The entries are installed only for the links contained in the list
 */
	public void InstallStaticMiddlePointEntries(FVSlicer slicer, FVClassifier fromSwitch, short inPort, Integer buffer_id, HashMap <Integer,LinkedList<Integer>> linkList) {
		for(Integer linkId: linkList.keySet()) {
			LinkedList<Integer> portList = linkList.get(linkId);
			if(portList.size() == 2){
				short port0 = portList.get(0).shortValue();
				short port1 = portList.get(1).shortValue();
				
				if(port0 == inPort || port1 == inPort || inPort == OFPort.OFPP_ALL.getValue()) {
					int buffer_id0 = (port0 == inPort) ? buffer_id : -1; 
					int buffer_id1 = (port1 == inPort) ? buffer_id : -1; 
					
					OFMatch match = new OFMatch().setWildcards(OFMatch.OFPFW_ALL & ~(OFMatch.OFPFW_DL_SRC | OFMatch.OFPFW_IN_PORT));
					match.setDataLayerSource(long2mac(getFakeSrcMAC(linkId.intValue())));  //match all flows with this linkId in the srcMAC	

					match.setInputPort(port0);
					FVFlowMod msg0 = buildFlowMod(match, OFFlowMod.OFPFC_ADD, linkId, buffer_id0, port1,(short)0,(short)0,DEFAULT_PRIORITY);
					msg0.sliceFromController(fromSwitch, slicer);					
					
					match.setInputPort(port1);
					FVFlowMod msg1 = buildFlowMod(match, OFFlowMod.OFPFC_ADD, linkId, buffer_id1, port0,(short)0,(short)0,DEFAULT_PRIORITY);
					msg1.sliceFromController(fromSwitch, slicer);
				}
			}
	    }
	}
	
	
/**
 * @name ManageMiddlePointEntries
 * @authors roberto.doriguzzi matteo.gerola
 * @param Long switchId, FVFlowMod flowMod, Integer linkId,HashMap <Long,LinkedList<Integer>> MiddlePointList,HashMap <LinkedList<Long>,Integer> HopList       
 * @return void
 * @description installs/remove the flow entries for middle point switches after receiving a flow_mod from the controller for an endpoint
 */	
	public void ManageMiddlePointEntries(Long switchId, FVFlowMod flowMod, Integer linkId,HashMap <Long,LinkedList<Integer>> MiddlePointList,HashMap <LinkedList<Long>,Integer> HopList) {
		ArrayList <FVEventHandler> fvHandlersList = VeRTIGO.getInstance().getHandlersCopy();
		OFMatch match = flowMod.getMatch().clone();	
//		System.out.println("InstallMiddlePointEntries flowMod match wildcards: " + match);
		
		int max_loops = MiddlePointList.size();
		int entries_to_send = MiddlePointList.size();
//		System.out.println("MiddlePointList size: "+ MiddlePointList.size());
		do {  // this loop because middlepoints in MiddlePointList could not be in the right order
//			System.out.println(">>>>>>>>>>>>>>>>>Ciclo DO");
			
			for (Entry<Long,LinkedList<Integer>> middlePoint: MiddlePointList.entrySet()){
//				System.out.println(">>>>>>>>>>>>>>>>>Ciclo For");
				LinkedList <Long> TmpList = new LinkedList <Long>();
				TmpList.add(0,middlePoint.getKey());
				TmpList.add(1,switchId);
				Integer inPort = HopList.get(TmpList);
//				System.out.println("middlePoint.getKey(): " + middlePoint.getKey());
//				System.out.println("switchId: " + switchId);
//				System.out.println("inPort: " + inPort);
				if(inPort != null){
					
					// here we save the middlepoint and the inPort in the hashMap (we need this because sometime 
					// the flow is processed by the switch before the flowMod below)
					List <Object> tmpMiddlePoint = new ArrayList <Object>(); 
					tmpMiddlePoint.add(middlePoint.getKey());
					tmpMiddlePoint.add(linkId);
					tmpMiddlePoint.add(inPort);
					tmpMiddlePoint.add(0);
					if(flowMod.getCommand() == FVFlowMod.OFPFC_ADD) this.updateMatchTable(match, tmpMiddlePoint, VTHashMap.ACTION.ADD.ordinal());
					//if(flowMod.getCommand() == FVFlowMod.OFPFC_DELETE) this.updateMatchTable(match, tmpMiddlePoint, VTHashMap.ACTION.DELETE.ordinal());
					
					// looking for slicer and classifier that will be used to send the flowMod
					FVSlicer slicer = null;
					FVClassifier classifier = null;
					for(FVEventHandler handler: fvHandlersList) {
						if(handler.getName().contains(FlowSpaceUtil.dpidToString(middlePoint.getKey()))) {
							if(handler.getName().contains("classifier")) {
								classifier = (FVClassifier)handler;
//								System.out.println("classifier name: " + classifier.getName());
							}
							if(handler.getName().contains("slicer") && handler.getName().contains(sliceName)) {
								slicer = (FVSlicer)handler;
//								System.out.println("slicer name: " + slicer.getName());
							}
						}
					}
					
					// sending the flowMod to the middlePoint switch
					if(classifier != null && slicer != null)
					{
						short outPort = ((inPort == middlePoint.getValue().get(0)) ? middlePoint.getValue().get(1) : middlePoint.getValue().get(0)).shortValue();
											
						match.setInputPort(inPort.shortValue());
						FVFlowMod msg0 = buildFlowMod(match, flowMod.getCommand(), linkId, -1, outPort,flowMod.getIdleTimeout(),flowMod.getHardTimeout(),flowMod.getPriority());
						msg0.sliceFromController(classifier, slicer);

						// update the switchId for the next hop
						switchId = middlePoint.getKey();
						entries_to_send--;
					}
				}
				if(entries_to_send == 0) break;
			}
			max_loops--;
//			System.out.println("entries_to_send: " + entries_to_send);
//			System.out.println("max_loops: " + max_loops);
		} while (entries_to_send > 0 && max_loops > 0);
				
	}
	
/**
 * @name buildFlowMod
 * @authors roberto.doriguzzi matteo.gerola
 * @param short command, OFMatch match, short outPort         
 * @return FVFlowMod
 * @description build a OFFlowMod msg to be sent to a middlepoint switch
 */
    private FVFlowMod buildFlowMod(OFMatch match, short command, Integer linkId, int buffer_id, short outPort, short idleTO, short hardTO, short priority) {
    	
    	FVFlowMod flowMod=new FVFlowMod();
    	flowMod.setXid(0);
        flowMod.setMatch(match);
        flowMod.setCookie(linkId.shortValue());
        flowMod.setCommand(command);
        flowMod.setIdleTimeout(idleTO); //infinite timeout
        flowMod.setHardTimeout(hardTO); //infinite timeout
        flowMod.setPriority(priority);
        flowMod.setBufferId(buffer_id);
        flowMod.setOutPort((command == FVFlowMod.OFPFC_DELETE) ? outPort : OFPort.OFPP_NONE.getValue());
        flowMod.setFlags((command == FVFlowMod.OFPFC_DELETE) ? 0 : (short) (1 << 0)); // OFPFF_SEND_FLOW_REM
        
        List<OFAction> actions = new LinkedList<OFAction>();
		FVActionOutput action = new FVActionOutput();
        
		action.setMaxLength((short) 0);
		if(outPort== match.getInputPort() && ((match.getWildcards() & OFMatch.OFPFW_IN_PORT) == 0 )){ 
			action.setPort((short) OFPort.OFPP_IN_PORT.getValue());
		}
		else{
			action.setPort(outPort);
		}
		actions.add(action);
		
		flowMod.setActions(actions);
		flowMod.setLength(U16.t(flowMod.getLength()+action.getLength()));
        
        return flowMod;
    }
    
/**
 * @name run
 * @authors roberto.doriguzzi matteo.gerola     
 * @description thread that cleans the HashMaps from old values
 */    
    
    public void run() {
    	try {
    		while(true) {
    			java.sql.Timestamp  sqlDate = new java.sql.Timestamp(new java.util.Date().getTime());
    			Long current_time = sqlDate.getTime();
    			List<Integer> id_list = new LinkedList<Integer>();
    			for(Entry<Integer,Long> id_time: this.TimeMap.entrySet()) {
    				if(id_time.getValue() < (current_time - EXPIRATION_TIME)) {
    					id_list.add(id_time.getKey());
    				}
    			}    
    			for(Integer id : id_list) {
    				this.updateFlowInfo (id, null, null, 0, FLOWINFO_ACTION.DELETE.ordinal());
    			}
    			
    			Thread.sleep(LOOP_TIME);
    		}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
}
