package org.flowvisor.vtopology.topology_configurator;

import org.flowvisor.config.ConfigError;
import org.flowvisor.config.FVConfig;

public class VTHop {
	public int hopId;
	public String srcDpid;
	public int srcPort;
	public String dstDpid;
	public int dstPort;
	
	public VTHop() {
		hopId=0;
		srcDpid=null;
		srcPort=0;
		dstDpid=null;
		dstPort=0;
	}
	
	public VTHop(int Id, String sDpid, int sPort, String dDpid, int dPort) {
		hopId=Id;
		srcDpid=sDpid;
		srcPort=sPort;
		dstDpid=dDpid;
		dstPort=dPort;
	}
	
	public void clone(VTHop srcHop) {
		hopId=srcHop.hopId;
		srcDpid=srcHop.srcDpid;
		srcPort=srcHop.srcPort;
		dstDpid=srcHop.dstDpid;
		dstPort=srcHop.dstPort;
	}
	
	public void GetConfigVTHop(String sliceName, int linkId, int currentHop)
	throws RuntimeException {
		try {
			String base = FVConfig.SLICES + FVConfig.FS + 
			sliceName + FVConfig.FS + FVConfig.LINKS + FVConfig.FS + Integer.toString(linkId) + 
			FVConfig.FS + FVConfig.HOPS + FVConfig.FS + Integer.toString(currentHop);
			this.hopId=currentHop;
			this.srcDpid=FVConfig.getString(base + FVConfig.FS + FVConfig.HOP_SRC_DPID);
			this.srcPort=FVConfig.getInt(base + FVConfig.FS + FVConfig.HOP_SRC_PORT);
			this.dstDpid=FVConfig.getString(base + FVConfig.FS + FVConfig.HOP_DST_DPID);
			this.dstPort=FVConfig.getInt(base + FVConfig.FS + FVConfig.HOP_DST_PORT);
		} catch (ConfigError e) {
			e.printStackTrace();
			throw new RuntimeException(
					"error in getting hops info");
		}
	}
	
	public String toString() {
		String vtHopString = null;
		vtHopString = this.srcDpid + "/" + this.srcPort + "-" + this.dstDpid + "/" + this.dstPort;
		return vtHopString;
	}
	
}
