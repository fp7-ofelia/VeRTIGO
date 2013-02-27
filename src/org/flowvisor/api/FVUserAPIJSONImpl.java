package org.flowvisor.api;

import java.util.Collection;

import org.flowvisor.flows.FlowEntry;


public class FVUserAPIJSONImpl extends FVUserAPIImpl implements FVUserAPIJSON {

	@Override
	public Collection<FlowEntry> listFlowSpace() {
		return getFlowEntries();
	}
}
