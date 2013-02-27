/**
 * 
 */
package org.flowvisor.vtopology.topology_configurator;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.flowvisor.config.ConfigError;
import org.flowvisor.config.ConfigNotFoundError;
import org.flowvisor.config.FVConfig;

/**
 * @author gerola
 *
 */
public class VTSlice {
	public String sliceId;
	public List<VTLink> vtLinkList;
	
	public VTSlice() {
		this.sliceId = null;
		this.vtLinkList = new LinkedList<VTLink>();
	}
	
	public void GetVTSliceConfig(String currentSlice) throws RuntimeException, ConfigError {
		this.sliceId = currentSlice;
		List<String> links = null;
		String base = FVConfig.SLICES + FVConfig.FS + currentSlice;
		try {
			List<String>entries = FVConfig.list(base + FVConfig.FS + FVConfig.LINKS);
			links = new LinkedList<String>(entries);
			for (String currentLink:links) {
				VTLink adLink = new VTLink();
				adLink.GetConfigVTLink(currentSlice, Integer.parseInt(currentLink));
				this.vtLinkList.add(adLink);
				}
		}
		catch (ConfigNotFoundError e) {
			// TODO Auto-generated catch block
		}
	}
	
	public void GetLinksInfo (String currentSwitch, HashMap<Integer,HashMap<Integer,Boolean>> linkPortMap) {
		for (VTLink currentLink:this.vtLinkList) {
			for (VTHop currentHop:currentLink.vtHopList) {
				if (currentHop.srcDpid.equals(currentSwitch) && currentHop.hopId == 1) {
					HashMap<Integer,Boolean> tmpLinkInfo = new HashMap<Integer,Boolean>();
					if (linkPortMap.containsKey(currentHop.srcPort)) tmpLinkInfo = linkPortMap.get(currentHop.srcPort);
					tmpLinkInfo.put(currentLink.linkId, true);
					linkPortMap.put(currentHop.srcPort,tmpLinkInfo);
				}
				else if (currentHop.srcDpid.equals(currentSwitch)  && currentHop.hopId != 1) {
					HashMap<Integer,Boolean> tmpLinkInfo = new HashMap<Integer,Boolean>();
					if (linkPortMap.containsKey(currentHop.srcPort)) tmpLinkInfo = linkPortMap.get(currentHop.srcPort);
					tmpLinkInfo.put(currentLink.linkId, false);
					linkPortMap.put(currentHop.srcPort,tmpLinkInfo);
				}
				else if (currentHop.dstDpid.equals(currentSwitch) && currentHop.hopId == currentLink.hopsNumber) {
					HashMap<Integer,Boolean> tmpLinkInfo = new HashMap<Integer,Boolean>();
					if (linkPortMap.containsKey(currentHop.dstPort)) tmpLinkInfo = linkPortMap.get(currentHop.dstPort);
					tmpLinkInfo.put(currentLink.linkId, true);
					linkPortMap.put(currentHop.dstPort,tmpLinkInfo);
				}
				else if (currentHop.dstDpid.equals(currentSwitch) && currentHop.hopId != currentLink.hopsNumber) {
					HashMap<Integer,Boolean> tmpLinkInfo = new HashMap<Integer,Boolean>();
					if (linkPortMap.containsKey(currentHop.dstPort)) tmpLinkInfo = linkPortMap.get(currentHop.dstPort);
					tmpLinkInfo.put(currentLink.linkId, false);
					linkPortMap.put(currentHop.dstPort,tmpLinkInfo);
				}
			}
		}
	}	
	
}
