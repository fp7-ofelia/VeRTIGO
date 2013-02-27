package org.flowvisor.flows;

import junit.framework.TestCase;

import org.flowvisor.log.DevNullLogger;
import org.flowvisor.log.FVLog;
import org.openflow.protocol.OFMatch;

public class FlowTestOpTest extends TestCase {

	@Override
	protected void setUp() {
		// don't do logging in unittests
		FVLog.setDefaultLogger(new DevNullLogger());
	}

	public void testCIDRMatch() {
		FlowEntry flowEntry = new FlowEntry(new OFMatch()
				.setWildcards(OFMatch.OFPFW_ALL), new SliceAction("alice",
				SliceAction.WRITE));
		FlowIntersect intersect = new FlowIntersect(flowEntry);
		int rip = 0xaabbccdd;
		int ip = FlowTestOp.testFieldMask(intersect,
				OFMatch.OFPFW_NW_DST_SHIFT, 32, 0, rip, 0x00000000);
		TestCase.assertEquals(rip, ip);
		int dmask = intersect.getMatch().getNetworkDestinationMaskLen();
		TestCase.assertEquals(32, dmask);
		int goodBits = (~OFMatch.OFPFW_NW_DST_MASK) & OFMatch.OFPFW_ALL;
		int wildcards = intersect.getMatch().getWildcards();
		TestCase.assertEquals(goodBits, wildcards);

		rip = 0;
		ip = FlowTestOp.testFieldMask(intersect, OFMatch.OFPFW_NW_SRC_SHIFT, 0,
				0, rip, 0x00000000);
		TestCase.assertEquals(rip, ip);
		dmask = intersect.getMatch().getNetworkSourceMaskLen();
		TestCase.assertEquals(0, dmask);
		wildcards = intersect.getMatch().getWildcards();
		TestCase.assertEquals(goodBits, wildcards);
	}
}
