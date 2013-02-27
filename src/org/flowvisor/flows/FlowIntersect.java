/**
 *
 */
package org.flowvisor.flows;

import org.openflow.protocol.OFMatch;

/**
 * Describe the intersection between two FlowEntry's.
 *
 * Contains an MatchType and if MatchType != NONE, then a FlowEntry structure
 * that describes the overlap
 *
 * @author capveg
 *
 */
public class FlowIntersect {
	MatchType matchType;
	FlowEntry flowEntry;
	public boolean maybeSubset;
	public boolean maybeSuperset;

	public FlowIntersect(FlowEntry flowEntry) {
		this.matchType = MatchType.UNKNOWN;
		this.flowEntry = flowEntry;
		this.maybeSubset = true;
		this.maybeSuperset = true;
	}

	public MatchType getMatchType() {
		return this.matchType;
	}

	public FlowIntersect setMatchType(MatchType matchType) {
		this.matchType = matchType;
		return this;
	}

	public FlowEntry getFlowEntry() {
		return flowEntry;
	}

	public void setFlowEntry(FlowEntry flowEntry) {
		this.flowEntry = flowEntry;
	}

	public OFMatch getMatch() {
		return this.flowEntry.getRuleMatch();
	}

	public void setMatch(OFMatch match) {
		this.flowEntry.setRuleMatch(match);
	}

	public long getDpid() {
		return this.flowEntry.getDpid();
	}

	public void setDpid(long dpid) {
		this.flowEntry.setDpid(dpid);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "FlowIntersect[matchType=" + matchType + ",flowEntry="
				+ flowEntry + "]";
	}

}
