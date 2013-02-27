package org.flowvisor.api;

import java.util.Collection;

import org.flowvisor.flows.FlowEntry;

public interface FVUserAPIJSON extends FVUserAPI {

	/**
	 * Lists all the flowspace this user has control over
	 *
	 * @return
	 */
	public Collection<FlowEntry> listFlowSpace();

}
