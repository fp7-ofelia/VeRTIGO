/**
 * 
 */
package org.flowvisor.vtopology.topology_configurator;

import java.util.Set;

import org.flowvisor.FlowVisor;
import org.flowvisor.flows.FlowSpaceUtil;
import org.openflow.protocol.OFMatch;
import org.flowvisor.config.FVConfig;

/**
 * @author gerola
 *
 */
public class VTTests {
	private VTConfigInterface vt_config;
	
	public VTTests() {
		
		vt_config = new VTConfigInterface();
	}
	
	public void PerformTest(int testNumber) {
		if (testNumber == 1) {
			//REGISTRAZIONE SWITCH
			vt_config.Clear();
			vt_config.phyPortList.add(1);
			vt_config.phyPortList.add(2);
			vt_config.phyPortList.add(3);
			vt_config.phyPortList.add(4);
			vt_config.phyPortList.add(5);
			vt_config.phyPortList.add(6);
			vt_config.phyPortList.add(7);
			vt_config.phyPortList.add(8);
			vt_config.phyPortList.add(9);
			vt_config.phyPortList.add(10);
			vt_config.phyPortList.add(11);
			vt_config.phyPortList.add(12);
			vt_config.InitSwitchInfo("alice",FlowSpaceUtil.parseDPID("00:00:00:00:00:00:00:01"));
			Set<Integer> keySetPortMap = vt_config.phyToVirtPortMap.keySet();
			for (int port:keySetPortMap) System.out.println("Switch 1, porta fisica: "+port + " porta virtuale: "+
					vt_config.phyToVirtPortMap.get(port).toString());
			vt_config.InitSwitchInfo("alice",FlowSpaceUtil.parseDPID("00:00:00:00:00:00:00:02"));
			keySetPortMap = vt_config.phyToVirtPortMap.keySet();
			for (int port:keySetPortMap) System.out.println("Switch 2, porta fisica: "+port + " porta virtuale: "+
					vt_config.phyToVirtPortMap.get(port).toString());
			vt_config.InitSwitchInfo("alice",FlowSpaceUtil.parseDPID("00:00:00:00:00:00:00:03"));
			keySetPortMap = vt_config.phyToVirtPortMap.keySet();
			for (int port:keySetPortMap) System.out.println("Switch 3, porta fisica: "+port + " porta virtuale: "+
					vt_config.phyToVirtPortMap.get(port).toString());
			vt_config.InitSwitchInfo("alice",FlowSpaceUtil.parseDPID("00:00:00:00:00:00:00:04"));
			keySetPortMap = vt_config.phyToVirtPortMap.keySet();
			for (int port:keySetPortMap) System.out.println("Switch 4, porta fisica: "+port + " porta virtuale: "+
					vt_config.phyToVirtPortMap.get(port).toString());
		}
		else if (testNumber == 2) {
			//CREAZIONE LINK VIRTUALI
			VTLink link1 = new VTLink();
			link1.FillHopList("00:00:00:00:00:00:00:01/1-00:00:00:00:00:00:00:02/1");
			link1.linkId=1;
			FVConfig.addVirtualLink("alice",link1);
			FlowVisor.getInstance().checkPointConfig();
			VTLink link2 = new VTLink();
			link2.linkId=2;
			link2.FillHopList("00:00:00:00:00:00:00:01/1-00:00:00:00:00:00:00:02/1,00:00:00:00:00:00:00:02/2-00:00:00:00:00:00:00:04/1");
			FVConfig.addVirtualLink("alice",link2);
			FlowVisor.getInstance().checkPointConfig();
			VTLink link3 = new VTLink();
			link3.linkId=3;
			link3.FillHopList("00:00:00:00:00:00:00:04/1-00:00:00:00:00:00:00:02/2,00:00:00:00:00:00:00:02/3-00:00:00:00:00:00:00:03/1");
			FVConfig.addVirtualLink("alice",link3);
			FlowVisor.getInstance().checkPointConfig();
		}
		else if (testNumber == 3) {
			//ELIMINAZIONE LINK VIRTUALI
			FVConfig.deleteVirtualLink("alice", 1);
			FlowVisor.getInstance().checkPointConfig();
			FVConfig.deleteVirtualLink("alice", 2);
			FlowVisor.getInstance().checkPointConfig();
			FVConfig.deleteVirtualLink("alice", 3);
			FlowVisor.getInstance().checkPointConfig();
		}
		else if (testNumber == 4) {
			System.out.println("------INIZIO SIMULAZIONE PACCHETTO-------");
			String flowMatchMod = "";
			//SIMULAZIONE PACKET-IN UNICAST			
			//FLOW-MATCH
			OFMatch match = new OFMatch();
			match.setDataLayerSource("01:01:01:01:01:01");
			match.setDataLayerDestination("02:02:02:02:02:01");
			match.setInputPort((short) 2);
			match.setDataLayerType((short)0x806);
			match.setWildcards(0);
			//UPLINK SWITCH1
			vt_config.Clear();
			vt_config.phyPortList.add((int)match.getInputPort());
			flowMatchMod = FlowMatchModifier(match);
			vt_config.GetSwitchInfo("alice", FlowSpaceUtil.parseDPID("00:00:00:00:00:00:00:01"),  flowMatchMod);
			
			//MAPPING PER CONTROLLER
			System.out.println("lo switch 1 � endpoint: "+ vt_config.isEndPoint);
			
			//DOWNLINK SWITCH1
			vt_config.Clear();
			vt_config.virtPortId=1;
			vt_config.virtPortList.add(7);
			flowMatchMod = FlowMatchModifier(match);
			vt_config.GetVirttoPhyPortMap("alice",FlowSpaceUtil.parseDPID("00:00:00:00:00:00:00:01"), flowMatchMod);
			System.out.println("Switch 1, arriva flow da controller, porta virtuale ingresso: "+vt_config.virtPortId + " porta fisica ingresso: "+
					vt_config.phyPortId);

			Set<Integer> keySetPortMap = vt_config.virtToPhyPortMap.keySet();
			for (int port:keySetPortMap) {
				System.out.println("Switch 1, arriva flow da controller, porta virtuale: "+port + " porta fisica: "+
						vt_config.virtToPhyPortMap.get(port).toString());
				int tmpPort = vt_config.virtToPhyPortMap.get(port);
				match.setInputPort((short) tmpPort);
			}
					
			
			//UPLINK SWITCH2
			vt_config.Clear();
			vt_config.phyPortList.add(1);
			flowMatchMod = FlowMatchModifier(match);
			vt_config.GetSwitchInfo("alice", FlowSpaceUtil.parseDPID("00:00:00:00:00:00:00:02"),  flowMatchMod);
			
			//MAPPING PER CONTROLLER
			System.out.println("lo switch 2 � endpoint: "+ vt_config.isEndPoint);
			if (vt_config.isEndPoint == false) {
				System.out.println("Switch 2, port fisica uscita: "+ vt_config.phyPortId);
				match.setInputPort((short) vt_config.phyPortId);
			}
			
			//UPLINK SWITCH4
			vt_config.Clear();
			vt_config.phyPortList.add(1);
			flowMatchMod = FlowMatchModifier(match);
			vt_config.GetSwitchInfo("alice", FlowSpaceUtil.parseDPID("00:00:00:00:00:00:00:04"),  flowMatchMod);
			
			//MAPPING PER CONTROLLER
			System.out.println("lo switch 4 � endpoint: "+ vt_config.isEndPoint);

			vt_config.Clear();
			vt_config.virtPortList.add(1);
			
			//DOWNLINK SWITCH4
			vt_config.virtPortId=7;
			vt_config.virtPortList.add(1);
			flowMatchMod = FlowMatchModifier(match);
			vt_config.GetVirttoPhyPortMap("alice",FlowSpaceUtil.parseDPID("00:00:00:00:00:00:00:04"), flowMatchMod);
			System.out.println("Switch 4, arriva flow da controller, porta virtuale ingresso: "+vt_config.virtPortId + " porta fisica ingresso: "+
					vt_config.phyPortId);
			
			keySetPortMap = vt_config.virtToPhyPortMap.keySet();
			for (int port:keySetPortMap) {
				System.out.println("Switch 4, arriva flow da controller, porta virtuale: "+port + " porta fisica: "+
						vt_config.virtToPhyPortMap.get(port).toString());
				int tmpPort = vt_config.virtToPhyPortMap.get(port);
				match.setInputPort((short) tmpPort);
			}
			
			System.out.println("------FINE SIMULAZIONE PACCHETTO-------");
		}
		else if (testNumber == 5) {
			/*
			vt_config.Clear();
			vt_config.phyPortStatusMap.put(1, false);
			vt_config.phyPortStatusMap.put(7, true);
			vt_config.phyPortStatusMap.put(8, true);
			vt_config.phyPortStatusMap.put(9, true);
			vt_config.UpdateSwitchInfo("alice", FlowSpaceUtil.parseDPID("00:00:00:00:00:00:00:01"));
			Set<Integer> keySetPortMap = vt_config.phyToVirtPortMap.keySet();
			for (int port:keySetPortMap) System.out.println("Switch 1, porta fisica: "+port + " porta virtuale: "+
					vt_config.phyToVirtPortMap.get(port).toString());
			vt_config.Clear();
			vt_config.phyPortStatusMap.put(4, false);
			vt_config.phyPortStatusMap.put(5, false);
			vt_config.phyPortStatusMap.put(1, true);
			vt_config.UpdateSwitchInfo("alice", FlowSpaceUtil.parseDPID("00:00:00:00:00:00:00:01"));
			keySetPortMap = vt_config.phyToVirtPortMap.keySet();
			for (int port:keySetPortMap) System.out.println("Switch 1, porta fisica: "+port + " porta virtuale: "+
					vt_config.phyToVirtPortMap.get(port).toString());
			*/
		}
		else if (testNumber == 6) {
			//test links up and down
			int linkId;
			String linkString1 = "00:00:00:00:00:00:00:01/6-00:00:00:00:00:00:00:02/6,00:00:00:00:00:00:00:02/7-00:00:00:00:00:00:00:03/7";
			boolean resCode = vt_config.GetNewLinkId("alice", linkString1);
			if (resCode == false) linkId = 1;
			else linkId = vt_config.linkId;
			VTLink vtLink1 = new VTLink(linkId);
			vtLink1.FillHopList (linkString1);			
			vt_config.UpdateVirtualLink ("alice", vtLink1, 0);
			vt_config.classifierToVirtPortMap.clear();
			
			String linkString4 = "00:00:00:00:00:00:00:01/7-00:00:00:00:00:00:00:04/7";
			resCode = vt_config.GetNewLinkId("alice", linkString4);
			if (resCode == false) linkId = 1;
			else linkId = vt_config.linkId;
			VTLink vtLink4 = new VTLink(linkId);
			vtLink4.FillHopList (linkString4);			
			vt_config.UpdateVirtualLink ("alice", vtLink4, 0);
			vt_config.classifierToVirtPortMap.clear();
			
			String linkString2 = "00:00:00:00:00:00:00:04/9-00:00:00:00:00:00:00:03/9,00:00:00:00:00:00:00:03/10-00:00:00:00:00:00:00:02/10";
			resCode = vt_config.GetNewLinkId("alice", linkString2);
			if (resCode == false) linkId = 1;
			else linkId = vt_config.linkId;
			VTLink vtLink2 = new VTLink(linkId);
			vtLink2.FillHopList (linkString2);			
			vt_config.UpdateVirtualLink ("alice", vtLink2, 0);
			vt_config.classifierToVirtPortMap.clear();
			
			String linkString3 = "00:00:00:00:00:00:00:01/7-00:00:00:00:00:00:00:04/7,00:00:00:00:00:00:00:04/8-00:00:00:00:00:00:00:03/8";
			resCode = vt_config.GetNewLinkId("alice", linkString3);
			if (resCode == false) linkId = 1;
			else linkId = vt_config.linkId;
			VTLink vtLink3 = new VTLink(linkId);
			vtLink3.FillHopList (linkString3);			
			vt_config.UpdateVirtualLink ("alice", vtLink3, 0);
			vt_config.classifierToVirtPortMap.clear();
			
			vt_config.UpdateVirtualLink ("alice", vtLink4, 1);
			vt_config.classifierToVirtPortMap.clear();
			
			vt_config.UpdateVirtualLink ("alice", vtLink1, 1);
			vt_config.classifierToVirtPortMap.clear();
			
			vt_config.UpdateVirtualLink ("alice", vtLink4, 0);
			vt_config.classifierToVirtPortMap.clear();
			
			vt_config.UpdateVirtualLink ("alice", vtLink1, 0);
			vt_config.classifierToVirtPortMap.clear();
		}
		
		else if (testNumber == 0) {
			vt_config.RemoveSliceInfo("alice");
		}
	}
	

	
	
	public String FlowMatchModifier(OFMatch match) {
		String flowMatchMod = "";
		int index = 0;
		String inPort = "in_port";
		flowMatchMod = match.toString();
		if (flowMatchMod.contains(inPort)) {
			index = flowMatchMod.indexOf(",");
			return flowMatchMod.substring(index+1);
		}
		else return flowMatchMod;
	}
}
