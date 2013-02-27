/**
 * 
 */
package org.flowvisor.vtopology.topology_configurator;

import java.util.LinkedList;
import java.util.List;

import org.flowvisor.config.ConfigError;
import org.flowvisor.config.FVConfig;

/**
 * @author gerola
 *
 */
public class VTConfig {
	public List<VTSlice> vtSliceList;
	
	public VTConfig() {
		this.vtSliceList = new LinkedList<VTSlice>();
	}
	
	public void GetConfigVTTree() throws RuntimeException, ConfigError {
		List<String> slices = null;
		List<String>entries = FVConfig.list(FVConfig.SLICES);
		slices = new LinkedList<String>(entries);
		for (String currentSlice:slices) {
			if (!currentSlice.equals("root")) {
				VTSlice vtSlice = new VTSlice();
				vtSlice.GetVTSliceConfig(currentSlice);
				this.vtSliceList.add(vtSlice);
			}
		}
	}
	
}
