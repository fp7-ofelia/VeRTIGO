/**
 *
 */
package org.flowvisor.api;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.flowvisor.VeRTIGO;
import org.flowvisor.api.FlowChange.FlowChangeOp;
import org.flowvisor.classifier.FVClassifier;
import org.flowvisor.config.BracketParse;
import org.flowvisor.config.ConfigError;
import org.flowvisor.config.FVConfig;
import org.flowvisor.config.InvalidSliceName;
import org.flowvisor.events.FVEventHandler;
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
import org.flowvisor.flows.FlowDBEntry;
import org.flowvisor.flows.FlowEntry;
import org.flowvisor.flows.FlowMap;
import org.flowvisor.flows.FlowRewriteDB;
import org.flowvisor.flows.FlowSpaceUtil;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.flowvisor.log.SendRecvDropStats;
import org.flowvisor.ofswitch.TopologyController;
import org.flowvisor.slicer.FVSlicer;
import org.flowvisor.vtopology.topology_configurator.VTConfigInterface;
import org.flowvisor.vtopology.topology_configurator.VTHop;
import org.flowvisor.vtopology.topology_configurator.VTLink;
import org.flowvisor.vtopology.topology_configurator.VTSlice;
import org.flowvisor.vtopology.vtstatistics.VTStatsDb;
import org.flowvisor.vtopology.vtstatistics.VTStatsUtils;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.util.HexString;
import org.openflow.util.U16;

/**
 * This is the actual UserAPI that gets wrapped via XMLRPC In theory
 * ("God willin' and the creek dun rise"), XMLRPC calls will call these function
 * directly
 *
 * @author capveg
 *
 */
public class FVUserAPIImpl extends BasicJSONRPCService implements FVUserAPI {
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * For debugging
	 *
	 * @param arg
	 *            test string
	 * @return response test string
	 */
	public String ping(String arg) {
		String user = APIUserCred.getUserName();
		return "PONG(" + user + "): FV version=" + VeRTIGO.FLOWVISOR_VERSION
				+ "::" + arg;
	}


	protected Collection<FlowEntry> getFlowEntries() {
		String sliceName = APIUserCred.getUserName();
		FVLog.log(LogLevel.DEBUG, null, "API listFlowSpace() by: " + sliceName);
		FlowMap flowMap;
		synchronized (FVConfig.class) {
			if (FVConfig.isSupervisor(sliceName))
				flowMap = FVConfig.getFlowSpaceFlowMap();
			else
				flowMap = FlowSpaceUtil.getSliceFlowSpace(sliceName);
			return flowMap.getRules();
		}
	}

	/**
	 * Create a new slice (without flowspace)
	 *
	 * Slices that contain the field separator are rewritten with underscores
	 *
	 * @param sliceName
	 *            Cannot contain FVConfig.FS == '!'
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
	@Override
	public Boolean createSlice(String sliceName, String passwd,
			String controller_url, String slice_email)
			throws MalformedControllerURL, InvalidSliceName,
			PermissionDeniedException, DuplicateControllerException {
		// FIXME: make sure this user has perms to do this OP
		// for now, all slices can create other slices
		// FIXME: for now, only handle tcp, not ssl controller url
		String[] list = controller_url.split(":");
		if (!FVConfig.isSupervisor(APIUserCred.getUserName()))
			throw new PermissionDeniedException(
					"only superusers can create new slices");
		if (list.length < 2)
			throw new MalformedControllerURL(
					"controller url needs to be of the form "
							+ "proto:hostname[:port], e.g., tcp:yourhost.foo.com:6633, not: "
							+ controller_url);
		if (!list[0].equals("tcp"))
			throw new MalformedControllerURL(
					"Flowvisor currently only supports 'tcp' proto, not: "
							+ list[0]);
		int controller_port;
		if (list.length >= 3)
			controller_port = Integer.valueOf(list[2]);
		else
			controller_port = FVConfig.OFP_TCP_PORT;
		// createSlice is synchronized()

		// We need to make sure this slice doesn't already exist
		List<String> slices = null;
		synchronized (FVConfig.class) {
			try {
				slices = FVConfig.list(FVConfig.SLICES);
			} catch (ConfigError e) {
				e.printStackTrace();
				throw new RuntimeException("no SLICES subdir found in config");
			}
			for (Iterator<String> sliceIter = slices.iterator(); sliceIter
					.hasNext();) {
				String existingSlice = sliceIter.next();
				if (sliceName.equals(existingSlice)) {
					throw new PermissionDeniedException(
							"Cannot create slice with existing name.");
				}
			}
		}

		FVConfig.createSlice(sliceName, list[1], controller_port, passwd,
				slice_email, APIUserCred.getUserName());
		VeRTIGO.getInstance().checkPointConfig();
		return true;
	}

	/**
	 * Change the password for this slice
	 *
	 * A slice is allowed to change its own password and the password of any
	 * slice that it has (transitively) created
	 *
	 * @param sliceName
	 * @param newPasswd
	 */
	@Override
	public Boolean changePasswd(String sliceName, String newPasswd)
			throws PermissionDeniedException {
		String changerSlice = APIUserCred.getUserName();
		if (!APIAuth.transitivelyCreated(changerSlice, sliceName)
				&& !FVConfig.isSupervisor(changerSlice))
			throw new PermissionDeniedException("Slice " + changerSlice
					+ " does not have perms to change the passwd of "
					+ sliceName);
		String salt = APIAuth.getSalt();
		String crypt = APIAuth.makeCrypt(salt, newPasswd);
		sliceName = FVConfig.sanitize(sliceName);
		// set passwd is synchronized
		FVConfig.setPasswd(sliceName, salt, crypt);
		VeRTIGO.getInstance().checkPointConfig();
		return true;
	}

	@Override
	public Boolean changeSlice(String sliceName, String key, String value)
			throws MalformedURLException, InvalidSliceName,
			PermissionDeniedException, InvalidUserInfoKey, DuplicateControllerException {
		String changerSlice = APIUserCred.getUserName();
		if (!APIAuth.transitivelyCreated(changerSlice, sliceName)
				&& !FVConfig.isSupervisor(changerSlice))
			throw new PermissionDeniedException("Slice " + changerSlice
					+ " does not have perms to change the passwd of "
					+ sliceName);
		/**
		 * this is the list of things a user is allowed to change about
		 * themselves. Critically, it should not include "creator" string as
		 * this would allow security issues.
		 */
		String baseNoKey = FVConfig.SLICES + FVConfig.FS + sliceName + FVConfig.FS;
		String base = baseNoKey + key;
		try {
			if (key.equals("contact_email"))
				FVConfig.setString(base, value);
			else if (key.equals("controller_hostname")){
				// make sure there isn't already a slice with this hostname and port
				// that this slice uses
				if (isSecondSliceSharingController(sliceName, value, FVConfig.getInt(baseNoKey + "controller_port"))){
					throw new DuplicateControllerException(value, FVConfig.getInt(baseNoKey + "controller_port"), sliceName, "changed");
				}
				FVConfig.setString(base, value);
			}
			else if (key.equals("controller_port")){
				// Make sure that there isn't already a slice on this port that uses
				// the same hostname that this slice uses
				if (isSecondSliceSharingController(sliceName, FVConfig.getString(baseNoKey + "controller_hostname"), Integer.parseInt(value))){
					throw new DuplicateControllerException(FVConfig.getString(baseNoKey + "controller_hostname"),
							Integer.parseInt(value), sliceName, "changed");
				}
				FVConfig.setInt(base, Integer.valueOf(value));
			}
			else
				throw new InvalidUserInfoKey("invalid key: " + key
						+ "-- only contact_email and "
						+ "controller_{hostname,port} can be changed");
			VeRTIGO.getInstance().checkPointConfig();
		} catch (ConfigError e) {
			// this should probably never happen b/c of above checks
			throw new InvalidUserInfoKey(e.toString());
		}

		return true;
	}

	private Boolean isSecondSliceSharingController(String thisSlice, String hostname, int port){
		Collection<String> sliceList;
		try {
			sliceList = listSlices();
		} catch (PermissionDeniedException e1) {
			return false;
		}
		for(String otherSlice : sliceList){
			if(otherSlice.equals(thisSlice)){
				// This is actually the same slice, ignore
				continue;
			}
			String base = FVConfig.SLICES + FVConfig.FS + otherSlice + FVConfig.FS;
			try {
				if(FVConfig.getString(base + "controller_hostname").equalsIgnoreCase(hostname)){
					if(FVConfig.getInt(base + "controller_port") == port){
						return true;
					}
				}
			} catch (ConfigError e) {
				// Guess it wasn't a match. just ignore
			}
		}

		return false;
	}

	@Override
	public Boolean change_password(String sliceName, String newPasswd)
			throws PermissionDeniedException {
		return changePasswd(sliceName, newPasswd);
		// just call changePasswd(); keeping the two names made things easier
		// for Jad /shrug
	}

	/**
	 * For now, create a circular, bidirectional loop between existing switches
	 * FIXME need to actually infer and calc real topology
	 */

	@Override
	public Collection<Map<String, String>> getLinks() {
		FVLog.log(LogLevel.DEBUG, null,
				"API getLinks() by: " + APIUserCred.getUserName());
		TopologyController topologyController = TopologyController
				.getRunningInstance();
		if (topologyController == null)
			return getFakeLinks();
		List<Map<String, String>> list = new LinkedList<Map<String, String>>();
		for (Iterator<LinkAdvertisement> it = topologyController.getLinks()
				.iterator(); it.hasNext();) {
			LinkAdvertisement linkAdvertisement = it.next();
			list.add(linkAdvertisement.toMap());
		}
		return list;
	}

	protected List<Map<String, String>> getFakeLinks() {
		FVLog.log(LogLevel.ALERT, null,
				"API: topology server not running: faking getLinks()");
		List<String> devices = listDevices();
		List<Map<String, String>> list = new LinkedList<Map<String, String>>();
		for (int i = 0; i < devices.size(); i++) {
			// forward direction
			LinkAdvertisement link = new LinkAdvertisement();
			link.srcDPID = devices.get(i);
			link.dstDPID = devices.get((i + 1) % devices.size());
			link.srcPort = 0;
			link.dstPort = 1;
			link.setAttribute("fakeLink", "true");
			list.add(link.toMap());
		}
		return list;
	}

	@Override
	public List<String> listDevices() {
		FVLog.log(LogLevel.DEBUG, null,
				"API listDevices() by: " + APIUserCred.getUserName());
		VeRTIGO fv = VeRTIGO.getInstance();
		// get list from main flowvisor instance
		List<String> dpids = new ArrayList<String>();
		String dpidStr;

		/*
		 * if (TopologyController.isConfigured()) { for (Long dpid :
		 * TopologyController.getRunningInstance() .listDevices()) { dpidStr =
		 * HexString.toHexString(dpid); if (!dpids.contains(dpidStr))
		 * dpids.add(dpidStr); else FVLog.log(LogLevel.WARN, TopologyController
		 * .getRunningInstance(), "duplicate dpid detected: " + dpidStr); } }
		 * else {
		 */
		// only list a device is we have a features reply for it
		for (FVEventHandler handler : fv.getHandlersCopy()) {
			if (handler instanceof FVClassifier) {
				OFFeaturesReply featuresReply = ((FVClassifier) handler)
						.getSwitchInfo();
				if (featuresReply != null) {
					dpidStr = FlowSpaceUtil.dpidToString(featuresReply
							.getDatapathId());
					if (!dpids.contains(dpidStr))
						dpids.add(dpidStr);
					else
						FVLog.log(LogLevel.WARN, handler,
								"duplicate dpid detected: " + dpidStr);
				}
			}
		}
		// }
		return dpids;

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.flowvisor.api.FVUserAPI#getDeviceInfo()
	 */
	@Override
	public Map<String, String> getDeviceInfo(String dpidStr)
			throws DPIDNotFound {
		Map<String, String> map = new HashMap<String, String>();
		long dpid = HexString.toLong(dpidStr);
		FVClassifier fvClassifier = null;
		for (FVEventHandler handler : VeRTIGO.getInstance().getHandlersCopy()) {
			if (handler instanceof FVClassifier) {
				OFFeaturesReply featuresReply = ((FVClassifier) handler)
						.getSwitchInfo();
				if (featuresReply != null
						&& featuresReply.getDatapathId() == dpid) {
					fvClassifier = (FVClassifier) handler;
					break;
				}
			}
		}
		if (fvClassifier == null)
			throw new DPIDNotFound("dpid does not exist: " + dpidStr + " ::"
					+ String.valueOf(dpid));
		OFFeaturesReply config = fvClassifier.getSwitchInfo();
		map.put("dpid", FlowSpaceUtil.dpidToString(dpid));
		if (config != null) {
			map.put("nPorts", String.valueOf(config.getPorts().size()));
			String portList = "";
			String portNames = "";
			int p;
			for (Iterator<OFPhysicalPort> it = config.getPorts().iterator(); it
					.hasNext();) {
				OFPhysicalPort port = it.next();
				p = U16.f(port.getPortNumber());
				portList += p;
				portNames += port.getName() + "(" + p + ")";
				if (it.hasNext()) {
					portList += ",";
					portNames += ",";
				}
			}
			map.put("portList", portList);
			map.put("portNames", portNames);
		} else {
			FVLog.log(LogLevel.WARN, null, "null config for: " + dpidStr);
		}
		map.put("remote", String.valueOf(fvClassifier.getConnectionName()));
		return map;
	}

	@Override
	public Boolean deleteSlice(String sliceName) throws SliceNotFound,
			PermissionDeniedException {
		String changerSlice = APIUserCred.getUserName();
		if (!APIAuth.transitivelyCreated(changerSlice, sliceName)) {
			FVLog.log(LogLevel.WARN, null, "API deletSlice(" + sliceName
					+ ") failed by: " + APIUserCred.getUserName());
			throw new PermissionDeniedException("Slice " + changerSlice
					+ " does not have perms to change the passwd of "
					+ sliceName);
		}
		synchronized (FVConfig.class) {
			try {
				// this is also synchronized against FVConfig.class
				FVConfig.deleteSlice(sliceName);
			} catch (Exception e) {
				throw new SliceNotFound("slice does not exist: " + sliceName);
			}
			FVLog.log(LogLevel.DEBUG, null, "API removeSlice(" + sliceName
					+ ") by: " + APIUserCred.getUserName());
			FlowSpaceUtil.deleteFlowSpaceBySlice(sliceName);
			FVConfig.sendUpdates(FVConfig.FLOWSPACE);
			// signal that FS has changed
			VeRTIGO.getInstance().checkPointConfig();
			
			/**
			 * @authors roberto.doriguzzi matteo.gerola
			 */
			VTConfigInterface vtConfig = new VTConfigInterface();
			vtConfig.RemoveSliceInfo(sliceName);
		}
		return true;
	}

	/**
	 * Implements {@link org.flowvisor.api.FVUserAPI#changeFlowSpace}
	 *
	 * Allow this change if it affectst the flowspace delagated to this slice.
	 *
	 * @throws PermissionDeniedException
	 *
	 */

	@Override
	public Collection<String> changeFlowSpace(List<Map<String, String>> changes)
			throws MalformedFlowChange, PermissionDeniedException,
			FlowEntryNotFound {
		String user = APIUserCred.getUserName();
		List<String> returnIDs = new LinkedList<String>();

		// TODO: implement the "delegate" bit; for now only root can change FS
		if (!FVConfig.isSupervisor(APIUserCred.getUserName()))
			throw new PermissionDeniedException(
					"only superusers can add/remove/change the flowspace");

		synchronized (FVConfig.class) { // prevent multiple API clients from
			// stomping
			// on each other
			FlowMap flowSpace = FVConfig.getFlowSpaceFlowMap();
			String logMsg;
			for (int i = 0; i < changes.size(); i++) {
				FlowChange change = FlowChange.fromMap(changes.get(i));
				FlowChangeOp operation = change.getOperation();
				logMsg = "user " + user + " " + operation;
				if (operation != FlowChangeOp.ADD) {
					logMsg += " id=" + change.getId();
					flowSpace.removeRule(change.getId());
					returnIDs.add(String.valueOf(change.getId()));
				}
				if (operation != FlowChangeOp.REMOVE) {
					logMsg += " for dpid="
							+ FlowSpaceUtil.dpidToString(change.getDpid())
							+ " match=" + change.getMatch() + " priority="
							+ change.getPriority() + " actions="
							+ FlowSpaceUtil.toString(change.getActions());

					FlowEntry flowEntry = new FlowEntry(change.getDpid(),
							change.getMatch(), change.getPriority(),
							change.getActions());

					if (operation == FlowChangeOp.ADD)
						returnIDs.add(String.valueOf(flowEntry.getId()));
					else
						flowEntry.setId(change.getId()); // keep id on change
					flowSpace.addRule(flowEntry);

				}
				FVLog.log(LogLevel.INFO, null, logMsg);
			}
			// update the indexes at the end, not with each rule
			VeRTIGO.getInstance().checkPointConfig();
			FVLog.log(LogLevel.INFO, null,
					"Signalling FlowSpace Update to all event handlers");
			FVConfig.sendUpdates(FVConfig.FLOWSPACE); // signal that FS has
			// changed
		}
		return returnIDs;
	}

	@Override
	public Collection<String> listSlices() throws PermissionDeniedException {
		/*
		 * relaxed security; anyone can get a list of slices if
		 * (!FVConfig.isSupervisor(APIUserCred.getUserName())) throw new
		 * PermissionDeniedException( "listSlices only available to root");
		 */
		List<String> slices = null;
		synchronized (FVConfig.class) {
			try {
				// this is synchronized
				List<String> entries = FVConfig.list(FVConfig.SLICES);
				slices = new LinkedList<String>(entries);
			} catch (ConfigError e) {
				e.printStackTrace();
				throw new RuntimeException(
						"wtf!?: no SLICES subdir found in config");
			}
		}
		return slices;
	}

	@Override
	public Map<String, String> getSliceInfo(String sliceName)
			throws PermissionDeniedException, SliceNotFound {

		/*
		 * relaxed security -- anyone can read slice info for now String user =
		 * APIUserCred.getUserName(); if (!FVConfig.isSupervisor(user) &&
		 * !APIAuth.transitivelyCreated(user, sliceName)) throw new
		 * PermissionDeniedException(
		 * "not superuser or transitive slice creator");
		 */
		if (!(doesSliceExist(sliceName))){
			throw new SliceNotFound("Slice does not exist: " + sliceName);
		}

		HashMap<String, String> map = new HashMap<String, String>();
		String base = FVConfig.SLICES + FVConfig.FS + sliceName + FVConfig.FS;

		synchronized (FVConfig.class) {
			try {
				map.put("contact_email",
						FVConfig.getString(base + "contact_email"));
				map.put("controller_hostname",
						FVConfig.getString(base + "controller_hostname"));
				map.put("controller_port", String.valueOf(FVConfig.getInt(base
						+ "controller_port")));
				map.put("creator", FVConfig.getString(base + "creator"));
			} catch (ConfigError e) {
				FVLog.log(LogLevel.CRIT, null, "malformed slice: " + e);
				e.printStackTrace();
			}
		}
		long dpid;
		int connection = 1;

		// TODO: come back an architect this so we can walk the list of slicers,
		// not the list of classifiers, and then slicers
		for (Iterator<FVEventHandler> it = VeRTIGO.getInstance()
				.getHandlersCopy().iterator(); it.hasNext();) {
			FVEventHandler eventHandler = it.next();
			if (eventHandler instanceof FVClassifier) {
				FVClassifier classifier = (FVClassifier) eventHandler;
				if (!classifier.isIdentified()) // only print switches have have
					// been identified
					continue;
				dpid = classifier.getDPID();
				FVSlicer fvSlicer = classifier.getSlicerByName(sliceName);
				if (fvSlicer != null) {
					map.put("connection_" + connection++,
							FlowSpaceUtil.dpidToString(dpid) + "-->"
									+ fvSlicer.getConnectionName());
				}

			}
		}

		return map;
	}

	/*
	 * @return true if slice exists, otherwise false
	 * @param sliceName name of slice to check for existance
	 */
	public static boolean doesSliceExist(String sliceName){
		List<String> slices = new ArrayList<String>();
		try {
			slices = FVConfig.list(FVConfig.SLICES);
		} catch (ConfigError e) {
			e.printStackTrace();
		}
		return slices.contains(sliceName);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.flowvisor.api.FVUserAPI#revertToLastCheckpoint()
	 */
	@Override
	public Boolean revertToLastCheckpoint() {
		// TODO: implement!
		return false;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.flowvisor.api.FVUserAPI#getConfig(java.lang.String)
	 */
	@Override
	public Collection<String> getConfig(String nodeName) throws ConfigError,
			PermissionDeniedException {
		String user = APIUserCred.getUserName();
		if (!FVConfig.isSupervisor(user)) {
			FVLog.log(LogLevel.WARN, null, "blocked getConfig for user " + user
					+ " on config " + nodeName);
			throw new PermissionDeniedException(
					"only superusers can call getConfig()");
		}
		FVLog.log(LogLevel.DEBUG, null, "getConfig for user " + user
				+ " on config " + nodeName);
		// this is synchronized against FVConfig
		return FVConfig.getConfig(nodeName);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.flowvisor.api.FVUserAPI#setConfig(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public Boolean setConfig(String nodeName, String value) throws ConfigError,
			PermissionDeniedException {
		String user = APIUserCred.getUserName();
		if (!FVConfig.isSupervisor(user)) {
			FVLog.log(LogLevel.WARN, null, "blocked setConfig for user " + user
					+ " on config " + nodeName + " to " + value);
			throw new PermissionDeniedException(
					"only superusers can call setConfig()");
		}
		// this is synchronized against FVConfig
		FVConfig.setConfig(nodeName, value);
		FVConfig.sendUpdates(nodeName);
		VeRTIGO.getInstance().checkPointConfig();
		FVLog.log(LogLevel.DEBUG, null, "setConfig for user " + user
				+ " on config " + nodeName + " to " + value);

		return true;
	}

	@Override
	public Boolean registerTopologyChangeCallback(String URL, String methodName, String cookie)
			throws MalformedURLException {
		// this will throw MalformedURL back to the client if the URL is bad
		new URL(URL);
		TopologyController tc = TopologyController.getRunningInstance();
		if (tc != null) {
			tc.registerCallBack(APIUserCred.getUserName(), URL, methodName, cookie);
			return true;
		} else
			return false; // topology server not running
	}

	public String getTopologyCallback(){

		TopologyController tc=TopologyController.getRunningInstance();
		String URL="";//="No callback defined yet"
		if (tc!=null){
			URL=tc.getTopologyCallback(APIUserCred.getUserName());
		}

		if (URL==null || URL.equals("")){
			return "No callback defined yet";
		}
		else{
			return URL;
		}

	}

	@Override
	public Boolean unregisterTopologyChangeCallback() {
		TopologyController tc = TopologyController.getRunningInstance();
		if (tc != null) {
			tc.unregisterCallBack(APIUserCred.getUserName());
			return true;
		} else
			return false; // topology server not running
	}

	@Override
	public String getSliceStats(String sliceName) throws SliceNotFound,
			PermissionDeniedException {

		if (!(doesSliceExist(sliceName))){
			throw new SliceNotFound("Slice does not exist: " + sliceName);
		}

		FVSlicer fvSlicer = null;
		for (Iterator<FVEventHandler> it = VeRTIGO.getInstance()
				.getHandlersCopy().iterator(); it.hasNext();) {
			FVEventHandler eventHandler = it.next();
			if (eventHandler instanceof FVClassifier) {
				FVClassifier classifier = (FVClassifier) eventHandler;
				if (!classifier.isIdentified()) // only print switches have have
					// been identified
					continue;
				fvSlicer = classifier.getSlicerByName(sliceName);
				if (fvSlicer != null) {
					break;
				}
			}
		}

		if (fvSlicer == null)
			return SendRecvDropStats.NO_STATS_AVAILABLE_MSG;

		return fvSlicer.getStats().combinedString();
	}

	@Override
	public String getSwitchStats(String dpidStr) throws DPIDNotFound,
			PermissionDeniedException {
		long dpid = FlowSpaceUtil.parseDPID(dpidStr);
		for (Iterator<FVEventHandler> it = VeRTIGO.getInstance()
				.getHandlersCopy().iterator(); it.hasNext();) {
			FVEventHandler eventHandler = it.next();
			if (eventHandler instanceof FVClassifier) {
				FVClassifier classifier = (FVClassifier) eventHandler;
				if (classifier.getDPID() == dpid)
					return classifier.getStats().combinedString();
			}
		}
		throw new DPIDNotFound("dpid not found: " + dpidStr);
	}

	@Override
	public Collection<Map<String, String>> getSwitchFlowDB(String dpidStr)
			throws DPIDNotFound {
		boolean found = false;
		long dpid = FlowSpaceUtil.parseDPID(dpidStr);
		List<Map<String, String>> ret = new LinkedList<Map<String, String>>();
		for (Iterator<FVEventHandler> it = VeRTIGO.getInstance()
				.getHandlersCopy().iterator(); it.hasNext();) {
			FVEventHandler eventHandler = it.next();
			if (eventHandler instanceof FVClassifier) {
				FVClassifier classifier = (FVClassifier) eventHandler;
				if (dpid == classifier.getDPID() || dpid == FlowEntry.ALL_DPIDS) {
					synchronized (classifier) {
						for (Iterator<FlowDBEntry> it2 = classifier.getFlowDB()
								.iterator(); it2.hasNext();) {
							ret.add(it2.next().toBracketMap());
						}
					}
					found = true;
				}
			}
		}
		if (!found)
			throw new DPIDNotFound("dpid not found: " + dpidStr);
		return ret;
	}

	@Override
	public Map<String, List<Map<String, String>>> getSliceRewriteDB(
			String sliceName, String dpidStr) throws DPIDNotFound,
			SliceNotFound, PermissionDeniedException {
		long dpid = FlowSpaceUtil.parseDPID(dpidStr);
		FVSlicer fvSlicer = lookupSlicer(sliceName, dpid);
		Map<String, List<Map<String, String>>> ret = new HashMap<String, List<Map<String, String>>>();
		FlowRewriteDB flowRewriteDB = fvSlicer.getFlowRewriteDB();
		synchronized (flowRewriteDB) {
			for (FlowDBEntry original : flowRewriteDB.originals()) {
				Map<String, String> originalMap = original.toBracketMap();
				List<Map<String, String>> rewrites = new LinkedList<Map<String, String>>();
				for (FlowDBEntry rewrite : flowRewriteDB.getRewrites(original)) {
					rewrites.add(rewrite.toBracketMap());
				}
				ret.put(BracketParse.encode(originalMap), rewrites);
			}
		}
		return ret;
	}

	/**
	 *
	 * @param sliceName
	 * @param dpid
	 * @return a valid fvSlicer (never null)
	 * @throws DPIDNotFound
	 * @throws SliceNotFound
	 */

	private FVSlicer lookupSlicer(String sliceName, long dpid)
			throws DPIDNotFound, SliceNotFound {

		FVClassifier fvClassifier = lookupClassifier(dpid); // throws dpid not
															// found
		synchronized (fvClassifier) {
			FVSlicer fvSlicer = fvClassifier.getSlicerByName(sliceName);
			if (fvSlicer == null)
				throw new SliceNotFound(sliceName);
			return fvSlicer;
		}
	}

	/**
	 * Returns a valid fvClassifier
	 *
	 * @param dpid
	 * @return never null
	 * @throws DPIDNotFound
	 */
	private FVClassifier lookupClassifier(long dpid) throws DPIDNotFound {
		for (Iterator<FVEventHandler> it = VeRTIGO.getInstance()
				.getHandlersCopy().iterator(); it.hasNext();) {
			FVEventHandler eventHandler = it.next();
			if (eventHandler instanceof FVClassifier) {
				FVClassifier classifier = (FVClassifier) eventHandler;
				if (dpid == classifier.getDPID())
					return classifier;
			}
		}
		throw new DPIDNotFound("No such switch: " + dpid);
	}


	/**
	 * @name enableVTPlannerStats
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	public boolean enableVTPlannerStats(String enable) 
	throws PermissionDeniedException{
		
		synchronized (FVConfig.class) {
			FVConfig.enableVTPlannerStats(enable);
			VeRTIGO.getInstance().checkPointConfig();
		}
		return true;
	}
	
	/**
	 * @name setVTPlannerTimers
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	public boolean setVTPlannerTimers(String timer, String expiration) 
	throws PermissionDeniedException{
		VTStatsDb statsDb = VTStatsDb.getInstance();
		Long long_timer = statsDb.sqlDbGetTimer();
		Long long_expiration = statsDb.sqlDbGetExpirationTime();
		
		try {
			statsDb.sqlDbInit();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// parse the values
		try{
			if(timer.endsWith("s")) long_timer = Long.parseLong(timer.replace("s",""));
			else if(timer.endsWith("m")) long_timer = Long.parseLong(timer.replace("m",""))*60;
			else if(timer.endsWith("h")) long_timer = Long.parseLong(timer.replace("h",""))*3600;
			else if(timer.endsWith("d")) long_timer = Long.parseLong(timer.replace("d",""))*86400;
			else if(timer.endsWith("w")) long_timer = Long.parseLong(timer.replace("w",""))*604800;
			else long_timer = Long.parseLong(timer);
			
			if(expiration.endsWith("s")) long_expiration = Long.parseLong(expiration.replace("s",""));
			else if(expiration.endsWith("m")) long_expiration = Long.parseLong(expiration.replace("m",""))*60;
			else if(expiration.endsWith("h")) long_expiration = Long.parseLong(expiration.replace("h",""))*3600;
			else if(expiration.endsWith("d")) long_expiration = Long.parseLong(expiration.replace("d",""))*86400;
			else if(expiration.endsWith("w")) long_expiration = Long.parseLong(expiration.replace("w",""))*604800;
			else long_expiration = Long.parseLong(expiration);
		}  catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// set the new timers into the statistic database
		statsDb.sqlDbSetTimer(long_timer*1000);
		statsDb.sqlDbSetExpirationTime(long_expiration*1000);
		
		
		// apply the new timers to each classifier
		ArrayList <FVEventHandler> fvHandlersList = VeRTIGO.getInstance().getHandlersCopy();
		for(FVEventHandler handler: fvHandlersList){ // looking for the handler with DPID
			if(handler.getName().contains("classifier")){
				((FVClassifier)handler).rescheduleVTStatistics();
			}
		}
		
		// save the new values to the configuration file
		synchronized (FVConfig.class) {
			FVConfig.setVTPlannerStatsTimers(timer, expiration);
			VeRTIGO.getInstance().checkPointConfig();
		}
		return true;
	}
	
	/**
	 * @name getVTPlannerTimers
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	public Collection<String> getVTPlannerTimers() 
	throws PermissionDeniedException{
		List<String> statsRecord = new LinkedList<String>();
		
		statsRecord.add("\nVTPlanner Statistics Timers (s=seconds, m=minutes, h=hours, d=days, w=weeks)");
		
		try {
			statsRecord.add("\nSampling period: " + FVConfig.getString(FVConfig.VTPLANNER_STATS_TIMER));
			statsRecord.add("Expiration time: " + FVConfig.getString(FVConfig.VTPLANNER_STATS_EXPIR));
		} catch (ConfigError e) {
			return null;
		}
		
		return statsRecord;
	}
	
	/**
	 * @name getVTPlannerPortStats
	 * @description gets Port stats within a specified slot of time
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	
	@Override
	public Collection<String> getVTPlannerPortStats(String switchId, String port, String datetime1, String datetime2) 
	throws RuntimeException, ConfigError {
		List<String> statsRecord = new LinkedList<String>();
		
		statsRecord.add("\nSWITCH_ID: " + switchId + " PORT_NUMBER: " + port + "\n");
		statsRecord.add("TIME\t\t\t\t\tBYTES_RX\t\tBYTES_TX\t\tPACKETS_RX\t\tPACKETS_TX");
		
		VTStatsUtils su = new VTStatsUtils();

		if(su.VTGetPortStats(switchId, port, datetime1, datetime2)){
			for(int i=0; i<su.pStats.counter;i++) {
				Date date = new Date(su.pStats.timeStamp.get(i));
				statsRecord.add(date.toString() + "\t\t" + String.format("%010d",su.pStats.rxBytes.get(i)) + "\t\t" + 
														   String.format("%010d",su.pStats.txBytes.get(i)) + "\t\t" + 
														   String.format("%010d",su.pStats.rxPackets.get(i)) + "\t\t" + 
														   String.format("%010d",su.pStats.txPackets.get(i)));
				
			}
		}
		
		return statsRecord;
	}
	
	/**
	 * @name getVTPlannerQueueStats
	 * @description gets Queue stats within a specified slot of time
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	
	@Override
	public Collection<String> getVTPlannerQueueStats(String switchId, String port, String queue, String datetime1, String datetime2) 
	throws RuntimeException, ConfigError {
		List<String> statsRecord = new LinkedList<String>();
		
		statsRecord.add("\nSWITCH_ID: " + switchId + " PORT_NUMBER: " + port + " QUEUE_ID: " + queue + "\n");
		statsRecord.add("TIME\t\t\t\t\tBYTES_TX\t\tPACKETS_TX\t\tERRORS_TX");
		
		VTStatsUtils su = new VTStatsUtils();

		if(su.VTGetQueueStats(switchId, port, queue, datetime1, datetime2)){
			for(int i=0; i<su.qStats.counter;i++) {
				Date date = new Date(su.qStats.timeStamp.get(i));
				statsRecord.add(date.toString() + "\t\t" + String.format("%010d",su.qStats.txBytes.get(i)) + "\t\t" + 
														   String.format("%010d",su.qStats.txPackets.get(i)) + "\t\t" + 
														   String.format("%010d",su.qStats.txErrors.get(i)));
				
			}
		}
		
		return statsRecord;
	}
	
	/**
	 * @name getVTPlannerPortInfo
	 * @description gets Port info. 
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	
	@Override
	public Collection<String> getVTPlannerPortInfo(String switchId, String port) 
	throws RuntimeException, ConfigError {
		List<String> infoRecord = new LinkedList<String>();
		
		infoRecord.add("\nSWITCH_ID: " + switchId + " PORT NUMBER: " + port + "\n");
		infoRecord.add("PORT\t\tCONFIG\t\tFEATURES\tSTATE");
		
		VTStatsUtils su = new VTStatsUtils();
		if(su.VTGetPortInfo(switchId, port)){
			for(int i=0; i<su.pInfo.counter;i++) {
				infoRecord.add(su.pInfo.phyPortId.get(i) + "\t\t" + 
								"0x" + Integer.toHexString(su.pInfo.portConfig.get(i)) + "\t\t" + 
								"0x" + Integer.toHexString(su.pInfo.portFeatures.get(i)) + "\t\t" + 
								"0x" + Integer.toHexString(su.pInfo.portState.get(i)));
				
			}
		}

		
		return infoRecord;
	}
	
	/**
	 * @name getVTPlannerSwitchInfo
	 * @description gets Switch info. 
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	
	@Override
	public Collection<String> getVTPlannerSwitchInfo(String switchId) 
	throws RuntimeException, ConfigError {
		int maxLength = 23; // DPID string length
		List<String> infoRecord = new LinkedList<String>();
		
		VTStatsUtils su = new VTStatsUtils();
		if(su.VTGetSwitchInfo(switchId)){
			
			for(int i=0; i<su.sInfo.counter;i++) {
				if(su.sInfo.Manufacturer.get(i).length() > maxLength) maxLength = su.sInfo.Manufacturer.get(i).length();
				if(su.sInfo.serialNumber.get(i).length() > maxLength) maxLength = su.sInfo.serialNumber.get(i).length();
				if(su.sInfo.datapathDescription.get(i).length() > maxLength) maxLength = su.sInfo.datapathDescription.get(i).length();
			}
			maxLength+=2; //additional spaces for the largest column 
			
			infoRecord.add("\nDATAPATH_ID" + VTStatsUtils.fillString(' ', maxLength-String.format("DATAPATH_ID").length()) + 
						   "DPATH_DESCRIPTION" + VTStatsUtils.fillString(' ', maxLength-String.format("DPATH_DESCRIPTION").length()) +
						   "MANUFACTURER" + VTStatsUtils.fillString(' ', maxLength-String.format("MANUFACTURER").length()) + 
						   "SERIAL_NUMBER" + VTStatsUtils.fillString(' ', maxLength-String.format("SERIAL_NUMBER").length()) + 
						   "CAPABILITIES" + VTStatsUtils.fillString(' ', maxLength-String.format("CAPABILITIES").length()) + 
						   "OF_VERSION" + VTStatsUtils.fillString(' ', maxLength-String.format("OF_VERSION").length()) + 
						   "PORTS\n");
			
			for(int i=0; i<su.sInfo.counter;i++) {
				infoRecord.add(VTStatsUtils.getStringDPID(su.sInfo.switchId.get(i)) + VTStatsUtils.fillString(' ', maxLength-VTStatsUtils.getStringDPID(su.sInfo.switchId.get(i)).length()) + 
								su.sInfo.datapathDescription.get(i) + VTStatsUtils.fillString(' ', maxLength-su.sInfo.datapathDescription.get(i).length()) +
								su.sInfo.Manufacturer.get(i) + VTStatsUtils.fillString(' ', maxLength-su.sInfo.Manufacturer.get(i).length()) + 
								su.sInfo.serialNumber.get(i) + VTStatsUtils.fillString(' ', maxLength-su.sInfo.serialNumber.get(i).length()) + 
								Integer.toBinaryString(su.sInfo.capabilities.get(i)) + VTStatsUtils.fillString(' ', maxLength-Integer.toBinaryString(su.sInfo.capabilities.get(i)).length()) + 
								Integer.toHexString(su.sInfo.ofpVersion.get(i)) + VTStatsUtils.fillString(' ', maxLength-Integer.toHexString(su.sInfo.ofpVersion.get(i)).length()) + 
								su.sInfo.available_ports.get(i));
				
			}
		}

		
		return infoRecord;
	}
	
	/**
	 * @name setDbInfo
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	public boolean setDbInfo(String dbType, String ipAddress, String port, String user, String passwd) 
	throws PermissionDeniedException, VTKeyException {
		if (!dbType.equals("mysql"))
			throw new VTKeyException("Database not allowed");
			
		if (!FVConfig.isSupervisor(APIUserCred.getUserName()))
			throw new PermissionDeniedException("only superusers can configure VeRTIGO");

		synchronized (FVConfig.class) {
			FVConfig.setDbInfo(dbType, ipAddress, port, user, passwd);
			VeRTIGO.getInstance().checkPointConfig();
		}
		return true;
	}


	/**
	 * @throws SQLException 
	 * @name addLink
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	public boolean addLink(String sliceName, String linkString)
	throws PermissionDeniedException, VTMalformedLink, VTKeyException, ConfigError {
		String changerSlice = APIUserCred.getUserName();
		if (!APIAuth.transitivelyCreated(changerSlice, sliceName)) {
			FVLog.log(LogLevel.WARN, null, "API addVirtualLink (slice: " + sliceName + ", link: "+linkString
					+ ") failed by: " + APIUserCred.getUserName());
			throw new PermissionDeniedException("addVirtualLink (slice: " + sliceName + ", link: "+linkString
					+ ") failed by: " + APIUserCred.getUserName());
		}
		
		synchronized (FVConfig.class) {
			try {
				FVConfig.list(FVConfig.SLICES + FVConfig.FS + sliceName + FVConfig.FS + FVConfig.LINKS);
			} catch (ConfigError e) {
				e.printStackTrace();
				throw new RuntimeException("no LINKS subdir found in config");
			}

			// Find new linkId

			VTConfigInterface vtConfig = new VTConfigInterface();
			short linkId = vtConfig.GetNewLinkId(sliceName, linkString);
			if (linkId == 0) return false;
			
			VTLink vtLink = new VTLink(linkId);
			
			if (!vtLink.FillHopList (linkString))
				throw new VTMalformedLink("\n\nWrong virtual link specification. Possible causes are:\n" +
						" (i) Wrong dpid or port number\n" +
						"(ii) Syntax error. A virtual link must be described with a comma-separated sequence of dpid/port. E.g.: \n" +
						"     aa:aa:aa:aa:aa:aa:aa/1-bb:bb:bb:bb:bb:bb:bb:bb/1," +
						"bb:bb:bb:bb:bb:bb:bb:bb/2-cc:cc:cc:cc:cc:cc:cc:cc/1");
			
			
			if (!vtLink.CheckHopConsistency())
				throw new VTMalformedLink("\n\nWrong virtual link specification. Possible causes are:\n" +
						" (i) Hops have not been inserted in the right order\n" +
						"(ii) Hops composing the virtual link do not exist in the physical topology");
			
			
			//TODO: add a check to avoid links duplication
			FVConfig.addVirtualLink(sliceName, vtLink); 
			
			//Update vtopology database
			vtConfig.UpdateVirtualLink(sliceName, vtLink, 0);
			
			VeRTIGO.getInstance().checkPointConfig();
		}
		return true;
	}


	/**
	 * @name changeLink
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	public boolean changeLink(String sliceName, String linkName,
			String linkStructure) throws PermissionDeniedException, VTMalformedLink, VTKeyException, ConfigError {
		String changerSlice = APIUserCred.getUserName();
		if (!APIAuth.transitivelyCreated(changerSlice, sliceName)) {
			FVLog.log(LogLevel.WARN, null, "API changeVirtualLink (slice: " + sliceName + ", link: "+linkName
					+ ") failed by: " + APIUserCred.getUserName());
			throw new PermissionDeniedException("changeVirtualLink (slice: " + sliceName + ", link: "+linkName
					+ ") failed by: " + APIUserCred.getUserName());
		}
		
		synchronized (FVConfig.class) {
			try {
				FVConfig.list(FVConfig.SLICES + FVConfig.FS + sliceName + FVConfig.FS + FVConfig.LINKS);
			} catch (ConfigError e) {
				e.printStackTrace();
				throw new RuntimeException("no LINKS subdir found in config");
			}
			
			VTLink vtLink = new VTLink(Integer.parseInt(linkName));
			
			if (!vtLink.FillHopList (linkStructure))
				throw new VTMalformedLink("\n\nWrong virtual link specification. Possible causes are:\n" +
						" (i) Wrong dpid or port number\n" +
						"(ii) Syntax error. A virtual link must be described with a comma-separated sequence of dpid/port. E.g.: \n" +
						"     aa:aa:aa:aa:aa:aa:aa/1-bb:bb:bb:bb:bb:bb:bb:bb/1," +
						"bb:bb:bb:bb:bb:bb:bb:bb/2-cc:cc:cc:cc:cc:cc:cc:cc/1");
			
			
			if (!vtLink.CheckHopConsistency())
				throw new VTMalformedLink("\n\nWrong virtual link specification. Possible causes are:\n" +
						" (i) Hops have not been inserted in the right order\n" +
						"(ii) Hops composing the virtual link do not exist in the physical topology");
			
			try {
				FVConfig.changeVirtualLink(sliceName, vtLink);
			} catch (Exception e) {
				throw new RuntimeException("link " + linkName + "  does not exist in virtual slice:" + sliceName);
			}
			
			FVLog.log(LogLevel.DEBUG, null, "API changeVirtualLink(" + sliceName + ":" + linkName
					+ ") by: " + APIUserCred.getUserName());
			
			//Update vtopology database
			VTConfigInterface vtConfig = new VTConfigInterface();
			vtConfig.UpdateVirtualLink(sliceName, vtLink, 2);
			
			VeRTIGO.getInstance().checkPointConfig();
		}
		return true;
	}


	/**
	 * @name deleteLink
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	public boolean deleteLink(String sliceName, String linkName)
	throws PermissionDeniedException, VTKeyException, FlowEntryNotFound, ConfigError {
		String changerSlice = APIUserCred.getUserName();
		if (!APIAuth.transitivelyCreated(changerSlice, sliceName)) {
			FVLog.log(LogLevel.WARN, null, "API deleteVirtualLink (slice: " + sliceName + ", link: "+linkName
					+ ") failed by: " + APIUserCred.getUserName());
			throw new PermissionDeniedException("deleteVirtualLink (slice: " + sliceName + ", link: "+linkName
					+ ") failed by: " + APIUserCred.getUserName());
		}
		
		synchronized (FVConfig.class) {
			try {
				FVConfig.list(FVConfig.SLICES + FVConfig.FS + sliceName + FVConfig.FS + FVConfig.LINKS);
			} catch (ConfigError e) {
				e.printStackTrace();
				throw new RuntimeException("no LINKS subdir found in config");
			}
			
			VTLink vtLink = new VTLink();
			vtLink.GetConfigVTLink(sliceName, Integer.parseInt(linkName));
			
			//delete virtual link
			try {
				FVConfig.deleteVirtualLink(sliceName, Integer.parseInt(linkName));
			} catch (Exception e) {
				throw new RuntimeException("link " + linkName + "  does not exist in virtual slice:" + sliceName);
			}
			FVLog.log(LogLevel.DEBUG, null, "API deleteVirtualLink(" + sliceName + ":" + linkName
					+ ") by: " + APIUserCred.getUserName());
			
			//Update vtopology database
			VTConfigInterface vtConfig = new VTConfigInterface();
			vtConfig.UpdateVirtualLink(sliceName, vtLink, 1);
			
			VeRTIGO.getInstance().checkPointConfig();
		}
		return true;
	}
	
	
	@Override
	public Map<Integer,Map<Integer, String>> getVirtualLinks(String sliceName) 
	throws RuntimeException, ConfigError {
		FVLog.log(LogLevel.DEBUG, null,
				"API getVirtualLinks() by: " + APIUserCred.getUserName());
		Map<Integer,Map<Integer, String>> virtualLinkMap = new HashMap<Integer,Map<Integer, String>>();
		
		String tmp = new String();
		
		VTSlice currentSlice = new VTSlice();
		currentSlice.GetVTSliceConfig(sliceName);
		for (VTLink currentLink : currentSlice.vtLinkList) {
			Map<Integer, String> HopMap = new HashMap<Integer, String>();
			HopMap.clear();
			for (VTHop currentHop : currentLink.vtHopList) {
				tmp = currentHop.srcDpid + "/" + currentHop.srcPort + "-" + currentHop.dstDpid + "/" + currentHop.dstPort;
				HopMap.put(currentHop.hopId, tmp);
			}
			virtualLinkMap.put(currentLink.linkId,HopMap);	
		}
		return virtualLinkMap;
	}
	
}
