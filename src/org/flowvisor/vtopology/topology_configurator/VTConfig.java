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
		List<String> slices = null;
		List<String> entries;
		
		try {
			entries = FVConfig.list(FVConfig.SLICES);
			slices = new LinkedList<String>(entries);
			for (String currentSlice:slices) {
				if (!currentSlice.equals("root")) {
					VTSlice vtSlice = new VTSlice();
					vtSlice.GetVTSliceConfig(currentSlice);
					this.vtSliceList.add(vtSlice);
				}
			}
		} catch (ConfigError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}
	
	public VTSlice getVTSlice(String sliceId) {
		System.out.println("Input sliceName: " + sliceId);
		for(VTSlice slice:this.vtSliceList) {
			System.out.println("VTSlice: " + slice.sliceId);
			if(sliceId.equals(slice.sliceId)) return slice;
		}
		return null;
	}
	
}
