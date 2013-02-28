package org.flowvisor.api;

import java.net.MalformedURLException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.flowvisor.config.ConfigError;
import org.flowvisor.config.InvalidSliceName;
import org.flowvisor.exceptions.DPIDNotFound;
import org.flowvisor.exceptions.DuplicateControllerException;
import org.flowvisor.exceptions.FlowEntryNotFound;
import org.flowvisor.exceptions.InvalidUserInfoKey;
import org.flowvisor.exceptions.MalformedControllerURL;
import org.flowvisor.exceptions.MalformedFlowChange;
import org.flowvisor.exceptions.PermissionDeniedException;
import org.flowvisor.exceptions.SliceNotFound;
import org.flowvisor.exceptions.VTKeyException;
import org.flowvisor.exceptions.VTMalformedLink;

public interface FVUserAPI {

	/**
	 * For debugging
	 *
	 * @param arg
	 *            test string
	 * @return response test string
	 */
	public String ping(String arg);

	/**
	 * Create a new slice (without flowspace)
	 *
	 * @param sliceName
	 * @param passwd
	 *            Cleartext! FIXME
	 * @param controller_url
	 *            Reference controller pseudo-url, e.g., tcp:hostname[:port]
	 * @param slice_email
	 *            As a contract for the slice
	 * @return success
	 * @throws InvalidSliceName
	 * @throws PermissionDeniedException
	 * @throws DuplicateControllerException
	 */

	public Boolean createSlice(String sliceName, String passwd,
			String controller_url, String slice_email)
			throws MalformedControllerURL, InvalidSliceName,
			PermissionDeniedException, DuplicateControllerException;

	/**
	 * Changes a key of a slice to value
	 *
	 * Only callable by a the slice owner or a transitive creator
	 *
	 * @param sliceName
	 * @param key
	 * @param value
	 * @return
	 * @throws MalformedURLException
	 * @throws InvalidSliceName
	 * @throws PermissionDeniedException
	 * @throws DuplicateControllerException
	 */

	public Boolean changeSlice(String sliceName, String key, String value)
			throws MalformedURLException, InvalidSliceName,
			PermissionDeniedException, InvalidUserInfoKey, DuplicateControllerException;

	public Map<String, String> getSliceInfo(String sliceName)
			throws PermissionDeniedException, SliceNotFound;

	/**
	 * Change the password for this slice
	 *
	 * A slice is allowed to change its own password and the password of any
	 * slice that it has (transitively) created
	 *
	 * @param sliceName
	 * @param newPasswd
	 */
	public Boolean changePasswd(String sliceName, String newPasswd)
			throws PermissionDeniedException;

	// have both names, b/c it makes the OM's life easier
	public Boolean change_password(String sliceName, String newPasswd)
			throws PermissionDeniedException;

	/**
	 * Get the list of device DPIDs (e.g., switches, routers, APs) connected to
	 * the FV
	 *
	 * @return
	 */
	public Collection<String> listDevices();

	/**
	 * Get information about a device
	 *
	 * @param dpidStr
	 *            8 colon separated hex bytes, e..g., "00:00:00:00:00:00:00:01"
	 *
	 * @return a map of key=value pairs where the value may itself be a more
	 *         complex object
	 */
	public Map<String, String> getDeviceInfo(String dpidStr)
			throws DPIDNotFound;

	/**
	 * Get the list of links between the devices in getDevices() Links are
	 * directional, so switch1 --> switch2 does not imply the reverse; they will
	 * be both listed if the link is bidirectional
	 *
	 * @return
	 */

	public Collection<Map<String, String>> getLinks();

	/**
	 * Delete the named slice
	 *
	 * Requestor only has permission to delete its own slice or the slice that
	 * it (transitively) created. Since root has transitively created all
	 * slices, root can delete all slices.
	 *
	 * @param sliceName
	 * @return Success
	 * @throws {@link SliceNotFound}, {@link PermissionDeniedException}
	 */

	public Boolean deleteSlice(String sliceName) throws SliceNotFound,
			PermissionDeniedException;

	/**
	 * Make changes to the flowspace
	 *
	 * Changes are processed in order and only after the last change is applied
	 * to the changes take affect, i.e., this is transactional
	 *
	 * FIXME: make this more codified; is XMLRPC the right thing here?
	 * Protobufs?
	 *
	 * Each Map should contain the an "operation" element; all keys and values
	 * are strings key="operation", value={CHANGE,ADD,REMOVE}
	 *
	 * remove: { "operation" : "REMOVE:", "id":"4235253" }
	 *
	 * add: { "operation" : "ADD", "priority":"100", "dpid" :
	 * "00:00:23:20:10:25:55:af" "match":"in_port=5,dl_src=00:23:20:10:10:10",
	 * "actions":"Slice=alice:4" }
	 *
	 * change: { "operation": "CHANGE" "id":"4353454", "priority":"105", // new
	 * priority "dpid" : "all", // new dpid
	 * "match":"in_port=5,dl_src=00:23:20:10:10:10", // new match
	 * "actions":"Slice=alice:4" // new actions }
	 *
	 *
	 * The changeFlowSpace() call will return a list of strings, where each
	 * element is an ID. If the operation was a REMOVE or a CHANGE, it's the ID
	 * of the removed/changed entry. If it's an ADD, it's the ID of the new
	 * entry.
	 *
	 * key="dpid", value=8 octet hexcoded string, e.g.,
	 * "00:00:23:20:10:25:55:af" the dpid string will be pushed off to
	 * FlowSpaceUtils.parseDPID()
	 *
	 * key="match", value=dpctl-style OFMatch string, see below
	 *
	 * key="actions", value=comma separated string of SliceActions suitable to
	 * call SliceAction.fromString e.g., "SliceAction:alice=4,SliceAction:bob=2
	 *
	 * FIXME: change perms flags to human readable letters, e.g.,
	 * "(r)read,(w)rite,(d)elegate"
	 *
	 * The "match" value string is a comma separated string of the form
	 * "match_field=value", e.g., "in_port=5,dl_src=00:43:af:35:22:11,tp_src=80"
	 * similar to dpctl from the OpenFlow reference switch. Any field not
	 * explicitly listed is assumed to be wildcarded.
	 *
	 * The string will get wrapped with "OFMatch[" + match_value + "]" and
	 * passed off to OFMatch.fromString("OFMatch[" + match_value + "]") and
	 * generally follows the same convention as dpctl
	 *
	 * @param list
	 *            of changes
	 * @throws MalformedFlowChange
	 * @return A list of flow entry IDs in string form
	 * @throws MalformedFlowChange
	 * @throws PermissionDeniedException
	 */
	public Collection<String> changeFlowSpace(List<Map<String, String>> changes)
			throws MalformedFlowChange, PermissionDeniedException,
			FlowEntryNotFound;

	/**
	 * Return a list of slices in the flowvisor: root only!
	 *
	 * @return
	 */
	public Collection<String> listSlices() throws PermissionDeniedException;

	/**
	 * Returns a list of strings that represents the requested config element
	 *
	 * @param nodeName
	 *            config element name
	 * @return List of strings
	 * @throws ConfigError
	 */
	public Collection<String> getConfig(String nodeName) throws ConfigError,
			PermissionDeniedException;

	/**
	 * Sets a config element by name
	 *
	 * @param nodeName
	 *            config element name
	 * @param value
	 *            string representation of value
	 * @return success
	 * @throws ConfigError
	 */
	public Boolean setConfig(String nodeName, String value) throws ConfigError,
			PermissionDeniedException;

	/**
	 * Reload last checkpointed config from disk
	 *
	 * Only available to root
	 *
	 * TODO: implement!
	 *
	 * @return success
	 */
	public Boolean revertToLastCheckpoint();

	/**
	 * Register an XMLRPC URL to be called when the topology changes.
	 *
	 * When the topology changes, FV will make a XMLRPC call to URL with
	 * parameter "cookie"
	 *
	 * @param URL
	 *            XMLRPC Address/proceedure
	 * @param cookie
	 *            opaque string with some meaningful state from the caller
	 * @return success on registering the callback
	 */
	public Boolean registerTopologyChangeCallback(String URL, String methodName, String cookie)
			throws MalformedURLException;

	/**
	 *
	 */
	public String getTopologyCallback();

	/**
	 * Unregister a previously registered callback
	 *
	 *
	 * @return true if successful, false otherwise
	 */
	public Boolean unregisterTopologyChangeCallback();

	/**
	 * Return a multiline string of the slice's stats
	 *
	 * The string is of the form:
	 *
	 * ---SENT--- $switch1 :: $type1=$count1[,$type2=$count2[...]] $switch2 ::
	 * $type1=$count1[,$type2=$count2[...]] Total ::
	 * $type1=$count1[,$type2=$count2[...]] ---DROP--- $switch1 ::
	 * $type1=$count1[,$type2=$count2[...]] $switch2 ::
	 * $type1=$count1[,$type2=$count2[...]] Total ::
	 * $type1=$count1[,$type2=$count2[...]]
	 *
	 * @param sliceName
	 *            which slice do you wants stats for
	 * @return A string of the above form
	 * @throws SliceNotFound
	 * @throws PermissionDeniedException
	 */
	public String getSliceStats(String sliceName) throws SliceNotFound,
			PermissionDeniedException;

	/**
	 * Return a multiline string of the switch's stats
	 *
	 * The string is of the form:
	 *
	 * ---SENT--- $slice1 :: $type1=$count1[,$type2=$count2[...]] $slice2 ::
	 * $type1=$count1[,$type2=$count2[...]] Total ::
	 * $type1=$count1[,$type2=$count2[...]] ---DROP--- $slice1 ::
	 * $type1=$count1[,$type2=$count2[...]] $slice2 ::
	 * $type1=$count1[,$type2=$count2[...]] Total ::
	 * $type1=$count1[,$type2=$count2[...]]
	 *
	 * @param dpid
	 *            of the switchyou wants stats for
	 * @return A string of the above form
	 * @throws DPIDNotFound
	 * @throws PermissionDeniedException
	 */

	public String getSwitchStats(String dpidStr) throws DPIDNotFound,
			PermissionDeniedException;

	/**
	 * Get a List of FlowDBEnty's converted by toBracketMap()
	 *
	 * @param dpid
	 *            a specific switch or "all" for all
	 * @return
	 */
	public Collection<Map<String, String>> getSwitchFlowDB(String dpidstr)
			throws DPIDNotFound;

	/**
	 * Return a map of the flow entries the slice requested to what the
	 * flowvisor produced
	 *
	 * @note KILL ME; this map crap is horrible, but we seemingly can't rely on
	 *       the remote side to support the extensions that serializable needs
	 *       so I have to do this by hand... need to rewrite everything here and
	 *       maybe move to SOAP or ProtoBufs
	 *
	 * @param sliceName
	 * @param dpidstr
	 * @return
	 * @throws DPIDNotFound
	 * @throws SliceNotFound
	 */
	public Map<String, List<Map<String, String>>> getSliceRewriteDB(
			String sliceName, String dpidstr) throws DPIDNotFound,
			SliceNotFound, PermissionDeniedException;
	
	/**
	 * @name enableVTPlannerStats
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	public boolean enableVTPlannerStats(String enable) 
			throws PermissionDeniedException;
	
	/**
	 * @name setVTPlannerTimers
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	public boolean setVTPlannerTimers(String timer, String expiration) 
			throws PermissionDeniedException;
	
	/**
	 * @name getVTPlannerTimers
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	public Collection<String> getVTPlannerTimers() 
			throws PermissionDeniedException;
	
	/**
	 * @name getVTPlannerPortStats
	 * @throws ConfigError 
	 * @throws RuntimeException 
	 * @authors roberto.doriguzzi matteo.gerola
	 */
	public Collection<String> getVTPlannerPortStats(String switchId, String port, String time1, String time2)
			throws RuntimeException, ConfigError;
	
	/**
	 * @name getVTPlannerQueueStats
	 * @throws ConfigError 
	 * @throws RuntimeException 
	 * @authors roberto.doriguzzi matteo.gerola
	 */
	public Collection<String> getVTPlannerQueueStats(String switchId, String port, String queue, String time1, String time2)
			throws RuntimeException, ConfigError;
	
	/**
	 * @name getVTPlannerPortInfo
	 * @throws ConfigError 
	 * @throws RuntimeException 
	 * @authors roberto.doriguzzi matteo.gerola
	 */
	public Collection<String> getVTPlannerPortInfo(String switchId, String port)
			throws RuntimeException, ConfigError;
	
	/**
	 * @name getVTPlannerSwitchInfo
	 * @throws ConfigError 
	 * @throws RuntimeException 
	 * @authors roberto.doriguzzi matteo.gerola
	 */
	public Collection<String> getVTPlannerSwitchInfo(String switchId)
			throws RuntimeException, ConfigError;
	
	/**
	 * @name setDbInfo 
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	public boolean setDbInfo(String dbType, String ipAddress , String port, String user, String passwd)
		throws PermissionDeniedException, VTKeyException;
	
	/**
	 * @throws SQLException 
	 * @name addLink
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	public boolean addLink(String sliceName, String linkStructure)
		throws PermissionDeniedException, VTMalformedLink, VTKeyException, ConfigError;

	/**
	 * @name changeLink
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	public boolean changeLink(String sliceName, String linkName, String linkStructure)
		throws PermissionDeniedException, VTMalformedLink, VTKeyException, ConfigError;

	/**
	 * @name deleteLink
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	public boolean deleteLink(String sliceName, String linkName) 
		throws PermissionDeniedException, VTKeyException, FlowEntryNotFound, ConfigError;

	/**
	 * @throws ConfigError 
	 * @throws RuntimeException 
	 * @name getVirtualLink
	 * @authors roberto.doriguzzi matteo.gerola
	 */
	public Map<Integer, Map<Integer, String>> getVirtualLinks(String sliceName)
			throws RuntimeException, ConfigError;

	
	
}
