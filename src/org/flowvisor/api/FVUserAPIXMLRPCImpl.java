package org.flowvisor.api;

import java.util.Arrays;
import java.util.Collection;

import org.flowvisor.config.FVConfig;
import org.flowvisor.flows.FlowEntry;

public class FVUserAPIXMLRPCImpl extends FVUserAPIImpl implements FVUserAPIXML{

	/**
	 * Lists all the flowspace
	 *
	 * @return
	 */
	@Override
	public Collection<String> listFlowSpace() {
		String[] fs;
		synchronized (FVConfig.class) {
			Collection<FlowEntry> flowEntries = getFlowEntries();
			fs = new String[flowEntries.size()];
			int i = 0;
			for (FlowEntry flowEntry : flowEntries)
				fs[i++] = flowEntry.toString();
		}
		return Arrays.asList(fs);
	}
}
