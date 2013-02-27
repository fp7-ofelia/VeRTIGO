/**
 * 
 */
package org.flowvisor.vtopology.topology_configurator;

/**
 * @author gerola
 *
 */
public enum VTSwitchPortsSeparators {
    SINGLEPORT_SEP	(","),
    MULTIPORT_SEP	("-");

    protected String value;

    private VTSwitchPortsSeparators(String value) {
        this.value = value;
    }

    /**
     * @return the value
     */
    public String getValue() {
        return value;
    }
}
