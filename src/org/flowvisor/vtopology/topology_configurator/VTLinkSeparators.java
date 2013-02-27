/**
 * 
 */
package org.flowvisor.vtopology.topology_configurator;

/**
 * @author gerola
 *
 */
public enum VTLinkSeparators {
    HOP_SEP		(","),
    SWITCH_SEP	("-"),
    PORT_SEP	("/"),
    MAC_SEP		(":");

    protected String value;

    private VTLinkSeparators(String value) {
        this.value = value;
    }

    /**
     * @return the value
     */
    public String getValue() {
        return value;
    }
}
