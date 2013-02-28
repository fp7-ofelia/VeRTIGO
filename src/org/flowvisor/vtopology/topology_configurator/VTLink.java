/**
 * 
 */
package org.flowvisor.vtopology.topology_configurator;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.flowvisor.VeRTIGO;
import org.flowvisor.api.LinkAdvertisement;
import org.flowvisor.classifier.FVClassifier;
import org.flowvisor.config.ConfigError;
import org.flowvisor.config.FVConfig;
import org.flowvisor.events.FVEventHandler;
import org.flowvisor.ofswitch.TopologyController;
import org.openflow.protocol.OFPhysicalPort;

/**
 * @author gerola
 *
 */
public class VTLink {
	public int linkId;
	public int hopsNumber;
	public List<VTHop> vtHopList;
	
	public VTLink() {
		this.linkId=0;
		this.hopsNumber=0;
		this.vtHopList = new LinkedList<VTHop>();
	}
	
	public VTLink(int link) {
		this.linkId=link;
		this.hopsNumber=0;
		this.vtHopList = new LinkedList<VTHop>();
	}
	
	public VTLink(String link, int hopsNum, List<VTHop> adHopList) {
		this.linkId=0;
		this.hopsNumber=0;
		this.vtHopList = adHopList;
	}
	
	public boolean FillHopList (String linkString) {
		boolean result = true;
		String[] hopList = linkString.split(VTLinkSeparators.HOP_SEP.getValue());
		this.hopsNumber=hopList.length;
		String[] hopFields;
		String[] srcFields;
		String[] dstFields;
		int hopId;
		String srcDpid;
		int srcPort;
		String dstDpid;
		int dstPort; 
		for (int i=0; i< this.hopsNumber; i++) {
			hopFields = hopList[i].split(VTLinkSeparators.SWITCH_SEP.getValue());
			if (hopFields.length == 2) {
				srcFields = hopFields[0].split(VTLinkSeparators.PORT_SEP.getValue());
				dstFields = hopFields[1].split(VTLinkSeparators.PORT_SEP.getValue());
				if (srcFields.length == 2 && dstFields.length == 2) {
					hopId = i+1;
					srcDpid = srcFields[0];
					srcPort = Integer.parseInt(srcFields[1]); 
					dstDpid = dstFields[0]; 
					dstPort = Integer.parseInt( dstFields[1]);
					result = CheckHopFields (srcDpid, srcPort, dstDpid, dstPort);
					if (result == true) {
						VTHop avHop = new VTHop(hopId, srcDpid, srcPort, dstDpid, dstPort);
						this.vtHopList.add(avHop);
					}
				}
				else
					result = false;
			}
			else
				result = false;
		}
		return result;
	}
	
	private boolean CheckHopFields (String srcDpid, int srcPort, String dstDpid, int dstPort) {
		
		/* Here we check whether the src port belongs to srcDpid and dstPort belongs to dstDpid */		
		List<FVEventHandler> handlers = VeRTIGO.getInstance().getHandlersCopy();
		boolean srcHop = false, dstHop = false;
		for (FVEventHandler handler : handlers) {
			if (handler.getName().contains("classifier-")) {
				FVClassifier tmpClassifier = (FVClassifier) handler;
				List<OFPhysicalPort> inPortList = tmpClassifier.getSwitchInfo().getPorts();
				if (handler.getName().contains(srcDpid) && !srcHop) {
					for (OFPhysicalPort inPort: inPortList){
						if(srcPort == inPort.getPortNumber()){
							srcHop = true;
							break;
						}
					}
				}
				else if (handler.getName().contains(dstDpid) && !dstHop) {
					for (OFPhysicalPort inPort: inPortList){
						if(dstPort == inPort.getPortNumber()){
							dstHop = true;
							break;
						}
					}
				}
			}
		}
		
		if(!srcHop || !dstHop) return false;

		for (int j=0; j<2; j++) {
			String[] mac = null;
			if (j == 0)
				mac = srcDpid.split(VTLinkSeparators.MAC_SEP.getValue());
			else if (j == 1)
				mac = dstDpid.split(VTLinkSeparators.MAC_SEP.getValue());
			if (mac.length != 8)
				return false;
			else {
				for (String macField:mac)
					if (macField.length() == 2) {
						macField.toLowerCase();
						for (int i=0; i<macField.length(); i++){
							char c = macField.charAt(i);
							switch(c) {
								case '0':
								case '1':
								case '2':
								case '3':
								case '4':
								case '5':
								case '6':
								case '7':
								case '8':
								case '9':
								case 'a':
								case 'b':
								case 'c':
								case 'd':
								case 'e':
								case 'f':
								break;
									
								default:
									return false;	
							}
						}
					}
					else
						return false;
			}
		}
		return true;
	}
	

	/**
	 * @name CheckHopConsistency
	 * @authors roberto.doriguzzi matteo.gerola
     * @return true if: (i) hops have been declared in the right order
     * 					(ii) the dst port of one link is different from the src port of the next one
	 */
	
	public boolean CheckHopConsistency() {
		
		/* Here we check the hops of the virtual link against the physical links */
		TopologyController topologyController = TopologyController.getRunningInstance();
		if (topologyController != null) {
			List<Map<String, String>> list = new LinkedList<Map<String, String>>();
			for (Iterator<LinkAdvertisement> it = topologyController.getLinks()
					.iterator(); it.hasNext();) {
				LinkAdvertisement linkAdvertisement = it.next();
				list.add(linkAdvertisement.toMap());
			}
		    if(!list.isEmpty()) {
				for (VTHop vtHop:this.vtHopList) {	
					boolean result = false;
					for(Map<String, String> hop:list){
						
						if((Integer.toString(vtHop.srcPort).equals(hop.get("srcPort")) && vtHop.srcDpid.equals(hop.get("srcDPID")) &&
						   Integer.toString(vtHop.dstPort).equals(hop.get("dstPort")) && vtHop.dstDpid.equals(hop.get("dstDPID"))) ||
						   (Integer.toString(vtHop.srcPort).equals(hop.get("dstPort")) && vtHop.srcDpid.equals(hop.get("dstDPID")) &&
						   Integer.toString(vtHop.dstPort).equals(hop.get("srcPort")) && vtHop.dstDpid.equals(hop.get("srcDPID")))) {
							result = true;
							break;
						}
					}
					if(!result) return false;
				}
		    }
		}
		
		
		VTHop avHopOld = new VTHop();
		for (VTHop avHopNew:this.vtHopList) {
			if (avHopOld.hopId != 0) {
				if (!avHopNew.srcDpid.equals(avHopOld.dstDpid))
					return false;
				if (avHopNew.srcPort == avHopOld.dstPort)
					return false;
			}
			avHopOld.clone(avHopNew);
		}
		return true;
	}
		
	public void GetConfigVTLink(String sliceName, int currentLink) 
	throws RuntimeException {
		List<String> hops = null;
		try {
			String base = FVConfig.SLICES + FVConfig.FS + 
			sliceName + FVConfig.FS + FVConfig.LINKS + FVConfig.FS + currentLink ;
			List<String>entries = FVConfig.list(base + FVConfig.FS + FVConfig.HOPS);
			hops = new LinkedList<String>(entries);
			this.linkId=currentLink;
			this.hopsNumber=FVConfig.getInt(base + FVConfig.FS + FVConfig.HOPS_NUMBER);
			for (String currentHop:hops) {
				VTHop adHop = new VTHop();
				adHop.GetConfigVTHop(sliceName, currentLink, Integer.parseInt(currentHop));
				this.vtHopList.add(adHop);
			}
		} catch (ConfigError e) {
			e.printStackTrace();
			throw new RuntimeException(
					"wtf!?: no HOPS subdir for LINKS in VIRTUAL_SLICE found in config");
		}
	}
	
	public String toString() {
		String vtlinkString = "";
		for (VTHop currentHop:this.vtHopList) {
			vtlinkString += currentHop.toString(); 
			if (currentHop.hopId != this.hopsNumber)
				vtlinkString += ",";
			}
		return vtlinkString;
	}
}
