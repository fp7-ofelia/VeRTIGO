package org.flowvisor.events;


public class VTLLDPEvent extends FVEvent {
	public int virtPortId;
	public byte[] bs;
	
	public VTLLDPEvent(FVEventHandler src, FVEventHandler dst, int virtPortId, byte[] bs) {
		super(src, dst);
		this.virtPortId = virtPortId;
		this.bs = bs;
	}
	
}
