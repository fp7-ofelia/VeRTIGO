package org.flowvisor.vtopology.utils;

import java.util.LinkedList;
import java.util.List;

import org.flowvisor.vtopology.topology_configurator.VTConfigInterface;
import org.openflow.protocol.OFPhysicalPort;

public class VTCloneOFPhysicalPort {
	
	public static OFPhysicalPort ClonePortFeatures(OFPhysicalPort original, Integer virtPortNumber) {
		OFPhysicalPort copy = new OFPhysicalPort();
		
		copy.setAdvertisedFeatures(original.getAdvertisedFeatures());
		copy.setConfig(original.getConfig());
		copy.setCurrentFeatures(original.getCurrentFeatures());
		copy.setHardwareAddress(original.getHardwareAddress());
		if(virtPortNumber >= VTConfigInterface.baseVirtPortNumber) copy.setName("veth" + virtPortNumber.toString());
		else copy.setName("veth" + original.getName());
		copy.setPeerFeatures(original.getPeerFeatures());
		copy.setPortNumber(virtPortNumber.shortValue());
		copy.setState(original.getState());
		copy.setSupportedFeatures(original.getSupportedFeatures());
		return copy;
	}
}
