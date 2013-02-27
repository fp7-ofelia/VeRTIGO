/**
 * 
 */
package org.flowvisor.vtopology.topology_configurator;

import java.util.LinkedList;
import java.util.List;

import org.flowvisor.config.ConfigError;
import org.flowvisor.config.FVConfig;

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
		int maxSwitchPortNumber = 48;
		if (srcPort < 0 || dstPort < 0 || srcPort > maxSwitchPortNumber || dstPort > maxSwitchPortNumber)
			return false;
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
	
	public boolean CheckHopConsistency() {
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
