package org.flowvisor.events;

import java.util.LinkedList;


public class VTEvent extends FVEvent {
	public int phyPortId;
	public int virtPortId;
	public boolean status;
	public LinkedList<String> activeFlows;
	
	public VTEvent(FVEventHandler src, FVEventHandler dst, int phyPortId, int virtPortId, boolean status) {
		super(src, dst);
		this.phyPortId = phyPortId;
		this.virtPortId = virtPortId;
		this.status = status;
	}

	public VTEvent(FVEventHandler src, FVEventHandler dst, LinkedList<String> activeFlows) {
		super(src, dst);
		this.activeFlows = activeFlows;
	}
	
}
