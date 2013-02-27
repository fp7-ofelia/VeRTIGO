/**
 * 
 */
package org.flowvisor.vtopology.topology_configurator;

/**
 * @author gerola
 *
 */
public class VTLinkStatus {
	public long switchId;
	public int phyPortId;
	public int virtPortId;
	public boolean status;
	
	public VTLinkStatus() {
		this.switchId = 0;
		this.phyPortId = 0;
		this.virtPortId = 0;
		this.status = true;
	}
	
	public VTLinkStatus(long switchId, int phyPortId, int virtPortId, boolean status) {
		this.switchId = switchId;
		this.phyPortId = phyPortId;
		this.virtPortId = virtPortId;
		this.status = status;
	}
	
}
