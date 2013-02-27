package org.flowvisor.vtopology.utils;

import org.flowvisor.message.FVStatisticsReply;
import org.flowvisor.message.statistics.FVPortStatisticsReply;
import org.flowvisor.message.statistics.FVQueueStatisticsReply;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;

public class VTCloneStatsMsg {
	
	/**
	 * @name ClonePortStats
	 * @authors roberto.doriguzzi matteo.gerola
	 * @param FVPortStatisticsReply original         
	 * @return FVPortStatisticsReply
	 * @description clones a PORT STATS REPLY message
	 */
	public static FVPortStatisticsReply ClonePortStats(FVPortStatisticsReply original) {
		FVPortStatisticsReply copy = new FVPortStatisticsReply();
		
		copy.setCollisions(original.getCollisions());
		copy.setPortNumber(original.getPortNumber());
		copy.setReceiveBytes(original.getReceiveBytes());
		copy.setReceiveCRCErrors(original.getReceiveCRCErrors());
		copy.setReceiveDropped(original.getReceiveDropped());
		copy.setreceiveErrors(original.getreceiveErrors());
		copy.setReceiveFrameErrors(original.getReceiveFrameErrors());
		copy.setReceiveOverrunErrors(original.getReceiveOverrunErrors());
		copy.setreceivePackets(original.getreceivePackets());
		copy.setTransmitBytes(original.getTransmitBytes());
		copy.setTransmitDropped(original.getTransmitDropped());
		copy.setTransmitErrors(original.getTransmitErrors());
		copy.setTransmitPackets(original.getTransmitPackets());
		
		return copy;
	}
	
	/**
	 * @name CloneQueueStats
	 * @authors roberto.doriguzzi matteo.gerola
	 * @param FVQueueStatisticsReply original         
	 * @return FVQueueStatisticsReply
	 * @description clones a QUEUE STATS REPLY message
	 */
	public static FVQueueStatisticsReply CloneQueueStats(FVQueueStatisticsReply original) {
		FVQueueStatisticsReply copy = new FVQueueStatisticsReply();
		
		copy.setPortNumber(original.getPortNumber());
		copy.setTransmitBytes(original.getTransmitBytes());
		copy.setTransmitErrors(original.getTransmitErrors());
		copy.setTransmitPackets(original.getTransmitPackets());
		copy.setQueueId(original.getQueueId());
		
		return copy;
	}

}
