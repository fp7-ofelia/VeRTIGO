/**
 *
 */
package org.flowvisor.events;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.flowvisor.classifier.FVClassifier;
import org.flowvisor.classifier.FVSendMsg;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.flowvisor.message.FVMessageFactory;
import org.flowvisor.vtopology.vtstatistics.VTStatsDb;
import org.flowvisor.vtopology.vtstatistics.VTStatsUtils;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.openflow.protocol.OFPortStatus.OFPortReason;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFPortStatisticsReply;
import org.openflow.protocol.statistics.OFPortStatisticsRequest;
import org.openflow.protocol.statistics.OFQueueStatisticsReply;
import org.openflow.protocol.statistics.OFQueueStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;
import org.openflow.util.U16;

/**
 * @authors roberto.doriguzzi matteo.gerola
 * @description Sends and manages traffic statistics 
 */
public class VTStoreStatisticsEvent extends FVTimerEvent {
	long lastPongTime;
	public int stats_xid = 0x11;
	private final FVMessageFactory offactory;
	FVEventLoop loop;
	private final FVSendMsg sendMsg;
	private VTStatsDb statsDb;
	private FVClassifier classifier;
	private OFFeaturesReply sw;
	
	public VTStoreStatisticsEvent(FVEventHandler handler, FVSendMsg sendMsg,
			FVEventLoop loop) {
		super(0, handler, handler, null);
		this.classifier = (FVClassifier) handler;
		this.sw  = this.classifier.getSwitchInfo();
		this.loop = loop;
		this.sendMsg = sendMsg;
		this.lastPongTime = System.currentTimeMillis();
		this.offactory = new FVMessageFactory();
				
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
		
	}


	public void sendStatsRequest() {
		
		// port statistics
		sendPortStatsRequest();
		// queue statistics
		//sendQueueStatsRequest();		
		
	}
	
	/**
	 * @name insertSwitchDescription(OFStatisticsReply msg)
	 * @authors roberto.doriguzzi, matteo.gerola
	 * @info Inserts switch infos into the DB
	 * @param OFStatisticsReply msg
	 * @return void
	 */
	private void insertSwitchDescription(OFStatisticsReply msg)
	{
		List<OFStatistics> inStatsList = msg.getStatistics();
		String available_ports = new String();
		List<OFPhysicalPort> phyports = sw.getPorts();
		long switchId = this.classifier.getDPID();
		
		available_ports = "";
		for(OFPhysicalPort ports: phyports){
			available_ports += ports.getPortNumber() + ",";
			try {
				statsDb.sqlDbInsertPortInfo(switchId, ports.getPortNumber(), ports.getConfig(), ports.getCurrentFeatures(), ports.getState());
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		OFDescriptionStatistics descStats = (OFDescriptionStatistics) inStatsList.get(0); 
		
		try {
			statsDb.sqlDbInsertSwitchInfo(switchId, descStats.getManufacturerDescription(), descStats.getSerialNumber(), descStats.getDatapathDescription(),available_ports, sw.getCapabilities(), sw.getVersion());
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	/**
	 * @name updateSwitchDescription(OFStatisticsReply msg)
	 * @authors roberto.doriguzzi, matteo.gerola
	 * @info Updates switch infos into the DB when one port changes status
	 * @param OFStatisticsReply msg
	 * @return void
	 */
	private void updateSwitchDescription(short port_nr, boolean reason)
	{
		VTStatsUtils su = new VTStatsUtils();
		
		try {
			if(statsDb.sqlDbGetSwitchInfo(sw.getDatapathId(), su.sInfo)) {
				List<Integer> port_list = su.sInfo.available_ports.get(0);
				
				if(reason) {
					boolean existing = false;
					for (Iterator<Integer> it = port_list.iterator(); it.hasNext();) {
						Integer port = it.next();
						if (port.shortValue() == port_nr) {
							existing = true;
							break;
						}
					}
					if(!existing) port_list.add(Integer.valueOf(port_nr));
				}
				else
				for (Iterator<Integer> it = port_list.iterator(); it.hasNext();) {
					Integer port = it.next();
					if (port.shortValue() == port_nr) {
						it.remove();
						break;
					}
				}	
				
				String available_ports = new String();
				long switchId = this.classifier.getDPID();	
				available_ports = "";
				for(Integer ports: port_list){
					available_ports += ports.shortValue() + ",";
				}
				
				try {
					statsDb.sqlDbInsertSwitchInfo(switchId, su.sInfo.Manufacturer.get(0), su.sInfo.serialNumber.get(0), su.sInfo.datapathDescription.get(0),available_ports, su.sInfo.capabilities.get(0), su.sInfo.ofpVersion.get(0));
				} 
				catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				}
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
			
	}
	
	/**
	 * @name updatePortInfo(OFMessage msg)
	 * @authors roberto.doriguzzi, matteo.gerola
	 * @info Updates status of ports
	 * @param OFStatisticsReply msg
	 * @return void
	 */
	public void updatePortInfo(OFMessage msg) {
		if(msg.getType() == OFType.PORT_STATUS) {
			OFPortStatus portStatus = (OFPortStatus) msg;
			OFPhysicalPort inPort = portStatus.getDesc();
			
			byte reason = portStatus.getReason();
			try {
				if (reason == OFPortReason.OFPPR_ADD.ordinal()) {
					statsDb.sqlDbInsertPortInfo(sw.getDatapathId(), inPort.getPortNumber(), inPort.getConfig(), inPort.getCurrentFeatures(), inPort.getState());
					updateSwitchDescription(inPort.getPortNumber(), true);
				}
				else if (reason == OFPortReason.OFPPR_DELETE.ordinal()) {
					statsDb.sqlDbDeletePortInfo(sw.getDatapathId(), inPort.getPortNumber());
					updateSwitchDescription(inPort.getPortNumber(), false);
				}
				else if (reason == OFPortReason.OFPPR_MODIFY.ordinal()) {
					if(inPort.getState() == OFPortState.OFPPS_LINK_DOWN.getValue()) {
						statsDb.sqlDbDeletePortInfo(sw.getDatapathId(), inPort.getPortNumber());
						updateSwitchDescription(inPort.getPortNumber(), false);
					}
					else {
						statsDb.sqlDbInsertPortInfo(sw.getDatapathId(), inPort.getPortNumber(), inPort.getConfig(), inPort.getCurrentFeatures(), inPort.getState());
						updateSwitchDescription(inPort.getPortNumber(), true);
					}
				}
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
	}	
	
	/**
	 * @name insertPortStatistics(OFStatisticsReply msg)
	 * @authors roberto.doriguzzi, matteo.gerola
	 * @info Inserts port statistics into the DB
	 * @param OFStatisticsReply msg
	 * @return void
	 */
	private void insertPortStatistics(OFStatisticsReply msg)
	{
		List<OFStatistics> inStatsList = msg.getStatistics();
		
		for (OFStatistics ofStats : inStatsList){
			OFPortStatisticsReply portStats = (OFPortStatisticsReply) ofStats;			
			
			try {
				statsDb.sqlDbInsertPortStats(sw.getDatapathId(), portStats.getPortNumber(), portStats.getTransmitBytes(), portStats.getReceiveBytes(),portStats.getTransmitPackets(),portStats.getreceivePackets());
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
	
	/**
	 * @name insertQueueStatistics(OFStatisticsReply msg)
	 * @authors roberto.doriguzzi, matteo.gerola
	 * @info Inserts port statistics into the DB
	 * @param OFStatisticsReply msg
	 * @return void
	 */
	private void insertQueueStatistics(OFStatisticsReply msg)
	{
		List<OFStatistics> inStatsList = msg.getStatistics();
		
		for (OFStatistics ofStats : inStatsList){
			//OFQueueStatisticsReply queueStats = (OFQueueStatisticsReply) ofStats;			
			OFPortStatisticsReply portStats = (OFPortStatisticsReply) ofStats;
			try {
				statsDb.sqlDbInsertQueueStats(sw.getDatapathId(), portStats.getPortNumber(), (int)(portStats.getTransmitBytes()%10), portStats.getTransmitBytes(), portStats.getTransmitPackets(),portStats.getTransmitErrors());
				//statsDb.sqlDbInsertQueueStats(sw.getDatapathId(), queueStats.getPortNumber(), queueStats.getQueueId(), queueStats.getTransmitBytes(), queueStats.getTransmitPackets(),queueStats.getTransmitErrors());
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
	
	/**
	 * @name storeStatistics(OFMessage msg)
	 * @authors roberto.doriguzzi, matteo.gerola
	 * @info Main function that manages the STATS REPLY messages coming from the switches
	 * @param OFStatisticsReply msg
	 * @return void
	 */
	public void storeStatistics(OFMessage msg) {
		if(msg.getType() == OFType.STATS_REPLY) {
			OFStatisticsReply reply = (OFStatisticsReply)msg;
			
			if(reply.getStatisticType() == OFStatisticsType.DESC){
				insertSwitchDescription((OFStatisticsReply)msg);
			} 
			else if(reply.getStatisticType() == OFStatisticsType.PORT){
				insertPortStatistics((OFStatisticsReply)msg);
			} 
			else if (reply.getStatisticType() == OFStatisticsType.QUEUE){
				insertQueueStatistics((OFStatisticsReply)msg);
			}
			
		}
		
	}	
	
	/**
	 * @name sendDescStatsRequest()
	 * @authors roberto.doriguzzi, matteo.gerola
	 * @info Sends the request for the switch description
	 * @return void
	 */
	public void sendDescStatsRequest() {
		OFStatisticsRequest statsReq = (OFStatisticsRequest) offactory
				.getMessage(OFType.STATS_REQUEST);
		statsReq.setStatisticType(OFStatisticsType.DESC);
		statsReq.setXid(stats_xid);
		this.sendMsg.sendMsg(statsReq, this.sendMsg);
	}
	
	/**
	 * @name sendPortStatsRequest()
	 * @authors roberto.doriguzzi, matteo.gerola
	 * @info Sends the request for port statistics
	 * @return void
	 */
	private void sendPortStatsRequest() {
		OFStatisticsRequest statsReq = (OFStatisticsRequest) offactory
				.getMessage(OFType.STATS_REQUEST);
		int requestLength = statsReq.getLengthU();
		statsReq.setXid(stats_xid);
		statsReq.setStatisticType(OFStatisticsType.PORT);
		OFPortStatisticsRequest portStats = new OFPortStatisticsRequest();
		portStats.setPortNumber(OFPort.OFPP_NONE.getValue());
		statsReq.setStatistics(Collections.singletonList((OFStatistics)portStats));
		statsReq.setLengthU(requestLength  + portStats.getLength());				
		this.sendMsg.sendMsg(statsReq, this.sendMsg);
	}
	
	/**
	 * @name sendQueueStatsRequest()
	 * @authors roberto.doriguzzi, matteo.gerola
	 * @info Sends the request for queue statistics
	 * @return void
	 */
	private void sendQueueStatsRequest() {
		OFStatisticsRequest statsReq = (OFStatisticsRequest) offactory
				.getMessage(OFType.STATS_REQUEST);
		int requestLength = statsReq.getLengthU();
		statsReq.setXid(stats_xid);
		statsReq.setStatisticType(OFStatisticsType.QUEUE);
		OFQueueStatisticsRequest queueStats = new OFQueueStatisticsRequest();
		queueStats.setPortNumber(OFPort.OFPP_ALL.getValue());
		queueStats.setQueueId(0xffffffff); //OpenFlowj does not define OFPQ_ALL
		statsReq.setStatistics(Collections.singletonList((OFStatistics)queueStats));
		statsReq.setLengthU(requestLength  + queueStats.getLength());				
		this.sendMsg.sendMsg(statsReq, this.sendMsg);
	}

	public void registerPong() {
		lastPongTime = System.currentTimeMillis();
	}


	public void scheduleNextCheck() {
		this.setExpireTime(System.currentTimeMillis() + statsDb.sqlDbGetTimer());
		loop.addTimer(this);
	}
	
	public void rescheduleNextCheck() {
		if(loop.removeTimer(this)) {
			this.setExpireTime(System.currentTimeMillis() + statsDb.sqlDbGetTimer());
			loop.addTimer(this);
		}
		
	}
}
