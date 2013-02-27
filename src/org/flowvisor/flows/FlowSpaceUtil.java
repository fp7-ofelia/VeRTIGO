/**
 *
 */
package org.flowvisor.flows;

import java.io.FileNotFoundException;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.flowvisor.config.FVConfig;
import org.flowvisor.ofswitch.TopologyController;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.util.HexString;

/**
 * @author capveg
 *
 */
public class FlowSpaceUtil {
	/**
	 * Consult the FlowSpace and get a list of all slices that get connections
	 * to this switch, as specified by it's DPID
	 *
	 * This function is somewhat expensive (think DB join), so the results
	 * should be cached, and then updated when the FlowSpace signals a change
	 *
	 * @param flowMap
	 *            A map of flow entries, like that from
	 *            FVConfig.getFlowSpaceFlowMap();
	 * @param dpid
	 *            As returned in OFFeaturesReply
	 * @return A list of names of slices, i.e., "alice", "bob", etc.
	 */
	public static Set<String> getSlicesByDPID(FlowMap flowMap, long dpid) {
		Set<String> ret = new HashSet<String>();
		OFMatch match = new OFMatch();
		match.setWildcards(OFMatch.OFPFW_ALL);
		List<FlowEntry> rules = flowMap.matches(dpid, match);
		for (FlowEntry rule : rules) {
			for (OFAction action : rule.getActionsList()) {
				SliceAction sliceAction = (SliceAction) action; // the flowspace
				// should only
				// contain
				// SliceActions
				ret.add(sliceAction.sliceName);
			}
		}

		if (TopologyController.isConfigured())
			ret.add(TopologyController.TopoUser);
		return ret;
	}

	/**
	 * Return the flowspace controlled by this slice
	 *
	 * Note that this correctly removes the "holes" caused by higher priority
	 * flowspace entries
	 *
	 * @param sliceName
	 * @return
	 */

	public static FlowMap getSliceFlowSpace(String sliceName) {
		OFMatch match = new OFMatch();
		FlowMap ret = new LinearFlowMap();
		match.setWildcards(OFMatch.OFPFW_ALL);
		FlowMap flowSpace = FVConfig.getFlowSpaceFlowMap();
		List<FlowIntersect> intersections = flowSpace.intersects(
				FlowEntry.ALL_DPIDS, match);
		for (FlowIntersect inter : intersections) {
			FlowEntry rule = inter.getFlowEntry();
			FlowEntry neoRule = null;
			neoRule = rule.clone();
			neoRule.setRuleMatch(inter.getMatch());
			neoRule.setActionsList(new ArrayList<OFAction>());
			for (OFAction action : rule.getActionsList()) {
				// the flowspace should only contain SliceActions
				SliceAction sliceAction = (SliceAction) action;
				if (sliceAction.getSliceName().equals(sliceName)) {
					neoRule.getActionsList().add(sliceAction.clone());
					ret.addRule(neoRule);
				}
			}
		}
		return ret;
	}

	/**
	 * Consult the flowspace and return the set of ports that this slice is
	 * supposed to use on this switch
	 *
	 * This function is somewhat expensive (think DB join), so the results
	 * should be cached, and then updated when the FlowSpace signals a change
	 *
	 * OFPort.OFPP_ALL (0xfffc) is used to describe that all ports are supposed
	 * to be used. If all ports are valid, then OFPP_ALL will be the only port
	 * returned.
	 *
	 * @param dpid
	 *            the switch identifier (from OFFeaturesReply)
	 * @param slice
	 *            The slices name, e.g., "alice"
	 * @return Set of ports
	 */
	public static Set<Short> getPortsBySlice(long dpid, String slice,
			FlowMap flowmap) {
		boolean allPorts = false;
		Set<Short> ret = new HashSet<Short>();
		OFMatch match = new OFMatch();

		if (TopologyController.isConfigured()
				&& slice.equals(TopologyController.TopoUser)) {
			allPorts = true; // topology controller has access to everything
		} else {
			// SYNCH flowmap HERE

			match.setWildcards(OFMatch.OFPFW_ALL);

			List<FlowEntry> rules = flowmap.matches(dpid, match);
			for (FlowEntry rule : rules) {
				for (OFAction action : rule.getActionsList()) {
					SliceAction sliceAction = (SliceAction) action; // the
					// flowspace
					// should only
					// contain
					// SliceActions
					if (sliceAction.sliceName.equals(slice)) {
						OFMatch ruleMatch = rule.getRuleMatch();
						if ((ruleMatch.getWildcards() & OFMatch.OFPFW_IN_PORT) != 0)
							allPorts = true;
						else
							ret.add(ruleMatch.getInputPort());
					}
				}
			}
		}
		if (allPorts) { // if we got one "match all ports", just replace
			// everything
			ret.clear(); // with OFPP_ALL
			ret.add(OFPort.OFPP_ALL.getValue());
		}
		return ret;
	}

	/**
	 * Mini-frontend for querying FlowSpace
	 *
	 * @param args
	 * @throws FileNotFoundException
	 */

	public static void main(String args[]) throws FileNotFoundException {
		if ((args.length != 2) && (args.length != 3)) {
			System.err
					.println("Usage: FLowSpaceUtil config.xml <dpid> [slice]");
			System.exit(1);
		}

		FVConfig.readFromFile(args[0]);
		long dpid = FlowSpaceUtil.parseDPID(args[1]);

		switch (args.length) {
		case 2:
			Set<String> slices = FlowSpaceUtil.getSlicesByDPID(
					FVConfig.getFlowSpaceFlowMap(), dpid);
			System.out.println("The following slices have access to dpid="
					+ args[1]);
			for (String slice : slices)
				System.out.println(slice);
			break;
		case 3:
			Set<Short> ports = FlowSpaceUtil.getPortsBySlice(dpid, args[2],
					FVConfig.getFlowSpaceFlowMap());
			System.out.println("Slice " + args[2] + " on switch " + args[1]
					+ " has access to port:");
			if (ports.size() == 1
					&& ports.contains(Short.valueOf(OFPort.OFPP_ALL.getValue())))
				System.out.println("ALL PORTS");
			else
				for (Short port : ports)
					System.out.println("Port: " + port);
		}

	}

	/**
	 * Get the FlowMap that is the intersection of the Master FlowSpace and this
	 * dpid
	 *
	 * @param dpid
	 *            As returned from OFFeatureReply
	 * @return A valid flowmap (never null)
	 */

	public static FlowMap getSubFlowMap(long dpid) {
		// assumes that new OFMatch() matches everything
		synchronized (FVConfig.class) {
			return FlowSpaceUtil.getSubFlowMap(FVConfig.getFlowSpaceFlowMap(),
					dpid, new OFMatch());
		}
	}

	/**
	 * Get the FlowMap that is the intersection of this FlowMap and the given
	 * flowSpace that is, any rule in the source flowmap that matches any part
	 * of dpid and match is added to the returned flowmap
	 *
	 * @param flowMap
	 *            Source flow map
	 * @param dpid
	 *            datapathId from OFFeaturesReply
	 * @param match
	 *            a valid OFMatch() struture
	 * @return a valid flowMap (never null)
	 */

	public static FlowMap getSubFlowMap(FlowMap flowMap, long dpid,
			OFMatch match) {
		FlowMap neoFlowMap = new LinearFlowMap();
		List<FlowEntry> flowEntries = flowMap.matches(dpid, match);
		for (FlowEntry flowEntry : flowEntries) {
			FlowEntry neoFlowEntry = flowEntry.clone();
			neoFlowEntry.setDpid(dpid);
			neoFlowMap.addRule(neoFlowEntry);
		}

		return neoFlowMap;
	}

	public static String toString(List<OFAction> actionsList) {
		String actions = "";
		if (actionsList == null)
			return actions;
		for (OFAction action : actionsList) {
			if (!actions.equals(""))
				actions += ",";
			actions += action.toString();
		}
		return actions;
	}

	/**
	 * Convert a string to a DPID "*","all","all_dpids" --> ALL_DPIDS constant
	 * if there is a ':", treat as a hex string else assume it's decimal
	 *
	 * @param dpidStr
	 * @return a dpid
	 */
	public static long parseDPID(String dpidStr) {
		if (dpidStr.equals("*") || dpidStr.toLowerCase().equals("any")
				|| dpidStr.toLowerCase().equals("all")
				|| dpidStr.toLowerCase().equals("all_dpids"))
			return FlowEntry.ALL_DPIDS;
		if (dpidStr.indexOf(':') != 0)
			return HexString.toLong(dpidStr);
		else
			// maybe long in decimal?
			return Long.valueOf(dpidStr);
	}

	public static String dpidToString(long dpid) {
		if (dpid == FlowEntry.ALL_DPIDS)
			return FlowEntry.ALL_DPIDS_STR;
		return HexString.toHexString(dpid);
	}

	/**
	 * Remove all of the flowSpace associated with a slice
	 *
	 * Does NOT send updates to classifiers
	 *
	 * DOES lock the flowSpace for synchronization
	 *
	 * @param sliceName
	 */
	public static void deleteFlowSpaceBySlice(String sliceName) {
		FlowMap flowSpace = FVConfig.getFlowSpaceFlowMap();
		SliceAction sliceAction = null;
		synchronized (flowSpace) {
			for (Iterator<FlowEntry> flowIter = flowSpace.getRules().iterator(); flowIter
					.hasNext();) {
				FlowEntry flowEntry = flowIter.next();
				for (Iterator<OFAction> actIter = flowEntry.getActionsList()
						.iterator(); actIter.hasNext();) {
					OFAction ofAction = actIter.next();
					if (!(ofAction instanceof SliceAction))
						continue;
					sliceAction = (SliceAction) ofAction;
					if (sliceName.equals(sliceAction.getSliceName()))
						actIter.remove(); // remove this action from the entry
				}
				if (flowEntry.getActionsList().size() == 0)
					flowIter.remove(); // remove the entry if no more actions
			}
		}
	}

	public static String connectionToString(SocketChannel sock) {
		try {
			Socket ss = sock.socket();
			return ss.getLocalAddress().toString() + ":" + ss.getLocalPort()
					+ "-->" + ss.getRemoteSocketAddress().toString();
		} catch (NullPointerException e) {
			return "NONE";
		}
	}
}
