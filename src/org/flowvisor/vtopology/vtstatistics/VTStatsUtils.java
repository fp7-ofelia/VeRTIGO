package org.flowvisor.vtopology.vtstatistics;

import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.flowvisor.config.ConfigError;
import org.openflow.protocol.OFPort;
import org.openflow.util.HexString;

public class VTStatsUtils {
	
	public class SwitchInfo {
		public List<Long> switchId;
		public List<String> Manufacturer;
		public List<String> serialNumber;
		public List<String> datapathDescription;
		public List<List<Integer>> available_ports;
		public List<Integer> capabilities;
		public List<Integer> ofpVersion;
		public short counter;
		
		public SwitchInfo(){
			counter = 0;
			switchId = new LinkedList<Long>();
			Manufacturer = new LinkedList<String>();
			serialNumber = new LinkedList<String>();
			datapathDescription = new LinkedList<String>();
			available_ports = new LinkedList<List<Integer>>();
			capabilities = new LinkedList<Integer>();
			ofpVersion = new LinkedList<Integer>();
		}
	}
	
	public class PortInfo {
		public long switchId;
		public List<Short> phyPortId; 
		public List<Integer> portConfig; 
		public List<Integer> portFeatures; 
		public List<Integer> portState;
		public short counter;
		
		public PortInfo(){
			counter = 0;
			phyPortId = new LinkedList<Short>();
			portConfig = new LinkedList<Integer>();
			portFeatures = new LinkedList<Integer>();
			portState = new LinkedList<Integer>();
		}
	}
	
	public class PortStats {
		public long switchId;
		public long portId;
		public List<Long> txBytes; 
		public List<Long> rxBytes; 
		public List<Long> txPackets; 
		public List<Long> rxPackets;
		public List<Long> timeStamp;
		public long counter;
		
		public PortStats() {
			counter = 0;
			txBytes = new LinkedList<Long>();
			rxBytes = new LinkedList<Long>();
			txPackets = new LinkedList<Long>();
			rxPackets = new LinkedList<Long>();
			timeStamp = new LinkedList<Long>();
		}
	}
	
	public class QueueStats {
		public long switchId;
		public long portId;
		public long queueId;
		public List<Long> txBytes; 
		public List<Long> txPackets; 
		public List<Long> txErrors;
		public List<Long> timeStamp;
		public long counter;
		
		public QueueStats() {
			counter = 0;
			txBytes = new LinkedList<Long>();
			txPackets = new LinkedList<Long>();
			txErrors = new LinkedList<Long>();
			timeStamp = new LinkedList<Long>();
		}
	}
	
	private VTStatsDb statsDb;
	public SwitchInfo sInfo;
	public PortInfo pInfo;
	public PortStats pStats;
	public QueueStats qStats;
	
	public VTStatsUtils() {
		
		statsDb = VTStatsDb.getInstance();
		
		try {
			this.statsDb.sqlDbInit();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		sInfo = new SwitchInfo();
		pInfo = new PortInfo();
		pStats = new PortStats();
		qStats = new QueueStats();
	}
	
	public static long getHexDPID(String DPID) {
		long dpid = -1;
		
		if(DPID.contains(":")){
			String tmp = new String("0x");
			dpid = Long.decode(tmp.concat(DPID.replace(":", "")));
		}
		else if(DPID.startsWith("0x")) {
			dpid = Long.decode(DPID);
		}
		
		return dpid;
	}
	
	public static String getStringDPID(long DPID) {
		return HexString.toHexString(DPID);
	}
	
	public static String fillString(char fillChar, int count){
        // creates a string of 'x' repeating characters
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, fillChar);
        return new String(chars);
    }
	
	/**
	 * @name VTGetSwitchInfo(String switchId)
	 * @authors roberto.doriguzzi, matteo.gerola
	 * @info Retrieves from information about one or more switches
	 * @param String switchId = datapath_ID of the switch
	 * @return true in case of success, false if ResultSet is empty
	 */
	
	public boolean VTGetSwitchInfo(String switchId) {
		boolean ret = false;
		Long datapath_id = 0xFFFFFFFFFFFFFFFFl;
		sInfo.available_ports.clear();
		sInfo.capabilities.clear();
		sInfo.Manufacturer.clear();
		sInfo.serialNumber.clear();
		sInfo.switchId.clear();
		sInfo.datapathDescription.clear();
		sInfo.ofpVersion.clear();
		
		
		if(!switchId.contentEquals("all") && !switchId.contentEquals("any")) {
			datapath_id = getHexDPID(switchId);
		}
			
		try {
			ret = statsDb.sqlDbGetSwitchInfo(datapath_id, sInfo);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return ret;
	}
	
	/**
	 * @name VTGetPortStats(String switchId, String phyPortId, String datetime1, String datetime2)
	 * @authors roberto.doriguzzi, matteo.gerola
	 * @info Retrieves from the DB port statistics recorded between time1 and time2
	 * @param String switchId = datapath_ID of the switch
	 * @param String phyPortId = the port number
	 * @param long datetime1
	 * @param long datetime2
	 * @return true in case of success, false if ResultSet is empty
	 */
	
	public boolean VTGetPortStats(String switchId, String phyPortId, String datetime1, String datetime2) {
		boolean ret = false;
		java.util.Date date1, date2;
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
		pStats.rxBytes.clear();
		pStats.txBytes.clear();
		pStats.rxPackets.clear();
		pStats.txPackets.clear();
		pStats.timeStamp.clear();
		
		try {
			date1 = formatter.parse(datetime1);
			date2 = formatter.parse(datetime2);
			
			try {
				ret = statsDb.sqlDbGetPortStats(getHexDPID(switchId), Short.decode(phyPortId), date1.getTime(), date2.getTime(), pStats);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (ParseException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		
		
		return ret;
	}
	
	/**
	 * @name VTGetQueueStats(String switchId, String phyPortId, String queueId, String datetime1, String datetime2)
	 * @authors roberto.doriguzzi, matteo.gerola
	 * @info Retrieves from the DB queue statistics recorded between time1 and time2
	 * @param String switchId = datapath_ID of the switch
	 * @param String phyPortId = the port number
	 * @param String queueId = the queue identifier
	 * @param long datetime1
	 * @param long datetime2
	 * @return true in case of success, false if ResultSet is empty
	 */
	
	public boolean VTGetQueueStats(String switchId, String phyPortId, String queueId, String datetime1, String datetime2) {
		boolean ret = false;
		java.util.Date date1, date2;
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
		qStats.txBytes.clear();
		qStats.txPackets.clear();
		qStats.txErrors.clear();
		qStats.timeStamp.clear();
		
		try {
			date1 = formatter.parse(datetime1);
			date2 = formatter.parse(datetime2);
			
			try {
				ret = statsDb.sqlDbGetQueueStats(getHexDPID(switchId), Short.decode(phyPortId), Integer.decode(queueId), date1.getTime(), date2.getTime(), qStats);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (ParseException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		
		
		return ret;
	}
	
	/**
	 * @name VTGetPortInfo(String switchId, String phyPortId)
	 * @authors roberto.doriguzzi, matteo.gerola
	 * @info Retrieves information about one or more ports of a switch
	 * @param String switchId = datapath_ID of the switch
	 * @param String phyPortId = the port number
	 * @return true in case of success, false if ResultSet is empty
	 */
	
	public boolean VTGetPortInfo(String switchId, String phyPortId) {
		boolean ret = false;
		Short port_nr = OFPort.OFPP_ALL.getValue();
		pInfo.phyPortId.clear();
		pInfo.portConfig.clear();
		pInfo.portFeatures.clear();
		pInfo.portState.clear();
		
		
		if(!phyPortId.contentEquals("all") && !phyPortId.contentEquals("any")) {
			port_nr = Short.decode(phyPortId);
		}
			
		try {
			ret = statsDb.sqlDbGetPortInfo(getHexDPID(switchId),port_nr, pInfo);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return ret;
	}

}
