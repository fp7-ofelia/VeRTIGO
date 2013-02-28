/**
 *
 */
package org.flowvisor.config;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.Console;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.flowvisor.VeRTIGO;
import org.flowvisor.api.APIAuth;
import org.flowvisor.events.FVEventHandler;
import org.flowvisor.exceptions.DuplicateControllerException;
import org.flowvisor.flows.FlowMap;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.flowvisor.vtopology.topology_configurator.VTHop;
import org.flowvisor.vtopology.topology_configurator.VTLink;

/**
 * Central collection of all configuration and policy information, e.g., slice
 * permissions, what port to run on, etc.
 *
 * Uses get/set on a hierarchy of nodes like sysctl,snmp, etc.
 * getInt("flowvisor.list_port") --> 6633
 * setString("slice.alice.controller_hostname"
 * ,"alice-controller.controllers.org")
 *
 * All of the set* operations will dynamically create the entry if it does not
 * exist.
 *
 * @author capveg
 *
 */
public class FVConfig {
	public static final String FS = "!";
	final static public String LISTEN_PORT = "flowvisor" + FS + "listen_port";
	public static final String API_WEBSERVER_PORT = "flowvisor" + FS
			+ "api_webserver_port";
	public static final String API_JETTY_WEBSERVER_PORT = "flowvisor" + FS
			+ "api_jetty_webserver_port";
	public static final String CHECKPOINTING = "flowvisor" + FS
			+ "checkpointing";
	public static final String TOPOLOGY_SERVER = "flowvisor" + FS
			+ "run_topology_server";
	public static final String STATS_DESC_HACK = "flowvisor" + FS
			+ "stats_desc_hack";
	public static final String FLOW_TRACKING = "flowvisor" + FS + "track_flows";
	final static public String VERSION_STR = "version"; // This is the flowvisor
	// version
	// Config file version number, should be updated if config file format
	// changes
	public static final int CONFIG_VERSION = 2;
	public static final String CONFIG_VERSION_STR = "config_version";
	final static public String SLICES = "slices";
	final static public String SWITCHES = "switches";
	final public static String SWITCHES_DEFAULT = "default";
	public static final String FLOOD_PERM = "flood_perm";

	final static public String FLOWSPACE = "flowspace";
	final static public String SLICE_CONTROLLER_HOSTNAME = "controller_hostname";
	final static public String SLICE_CONTROLLER_PORT = "controller_port";
	final static public String SLICE_CONTACT_EMAIL = "contact_email";
	public static final String SLICE_SALT = "passwd_salt";
	public static final String SLICE_CRYPT = "passwd_crypt";
	public static final String SLICE_CREATOR = "creator";

	final static public int OFP_TCP_PORT = 6633;
	public static final String LOG_THRESH = "flowvisor" + FS + "logging";
	public static final String LOG_FACILITY = "flowvisor" + FS + "log_facility";
	public static final String LOG_IDENT = "flowvisor" + FS + "log_ident";

	public static final String SUPER_USER = "fvadmin";
	// complain if event processing takes > DelayWarning milliseconds
	public static final long DelayWarning = 10;
	
	/**
	 * @authors roberto.doriguzzi matteo.gerola
	 */
	final static public String ENABLE_VTPLANNER_STATS = "vt_enable_vtplanner_stats";
	final static public String VTPLANNER_STATS_TIMER = "vt_vtplanner_stats_timer";
	final static public String VTPLANNER_STATS_EXPIR = "vt_vtplanner_stats_expiration";
	final static public String DB_TYPE = "vt_db_type";
	final static public String DB_IP = "vt_db_ip_address";
	final static public String DB_PORT = "vt_db_port";
	final static public String DB_USER = "vt_db_user";
	final static public String DB_PASSWD = "vt_db_passwd";
	final static public String LINKS = "links";
	final static public String SLICE_ISVIRTUAL = "slice_is_virtual";
	final static public String HOPS_NUMBER = "hops_number";
	final static public String HOPS = "hops";
	final static public String HOP_SRC_DPID = "source_dpid";
	final static public String HOP_SRC_PORT = "source_port";
	final static public String HOP_DST_DPID = "destination_dpid";
	final static public String HOP_DST_PORT = "destination_port";

	static ConfDirEntry root = new ConfDirEntry(""); // base of all config info

	/**
	 * Return the config entry specific in name
	 *
	 * @param name
	 * @return null if not found
	 */
	static private ConfigEntry lookup(String name) {
		List<String> parts = Arrays.asList(name.split(FS));
		ConfigEntry ret = null;
		ConfDirEntry base = FVConfig.root;
		for (String part : parts) {
			if (base == null)
				break;
			ret = base.lookup(part);
			if (ret == null)
				break;
			if (ret.getType() == ConfigType.DIR)
				base = (ConfDirEntry) ret;
			else
				base = null;
		}
		return ret;
	}

	/**
	 * Clear out the entire config
	 *
	 * FIXME: make sure this doesn't cause an object leak Generally only used
	 * with testing harness, but still...
	 */
	static protected void clear() {
		FVConfig.root = new ConfDirEntry("");
	}

	static protected ConfigEntry create(String name, ConfigType type)
			throws ConfigError {
		String[] parts = name.split(FS);
		int i;
		ConfDirEntry base = FVConfig.root;

		// step through tree; creating as we go
		for (i = 0; i < (parts.length - 1); i++) {
			ConfigEntry tmp = base.lookup(parts[i]);

			if (tmp == null) {
				tmp = new ConfDirEntry(parts[i]);
				base.add(tmp);
			} else if (tmp.getType() != ConfigType.DIR) {
				throw new ConfigCantCreateError("tried to create dir \"" + name
						+ "\"" + " but element " + i + " \"" + parts[i]
						+ " is a " + tmp.getType() + " not a directory");
			}
			base = (ConfDirEntry) tmp;
		}
		// magic up a new instance of
		Class<? extends ConfigEntry> c = type.toClass();
		ConfigEntry entry;
		try {
			entry = c.getConstructor(new Class[] { String.class }).newInstance(
					parts[parts.length - 1]);
		} catch (Exception e) {
			throw new ConfigCantCreateError(e.toString());
		}
		// add it to the (potentially newly created) base
		base.add(entry);
		return entry;
	}

	/**
	 * Sets an integer in the config Will dynamically create the path if it does
	 * not exist
	 *
	 * @param node
	 *            e.g., "path.to.configname"
	 * @param val
	 *            any integer
	 * @throws ConfigError
	 *             If trying to create the path conflicted with existing config
	 */

	static public void setInt(String node, int val) throws ConfigError {
		ConfigEntry entry = FVConfig.lookup(node);
		if (entry == null)
			entry = create(node, ConfigType.INT);
		else if (entry.type != ConfigType.INT)
			throw new ConfigWrongTypeError("tried to set an " + entry.getType()
					+ " to int");
		ConfIntEntry ei = (ConfIntEntry) entry;
		ei.setInt(val);
	}

	/**
	 * Return the integer associated with this node
	 *
	 * @param node
	 *            Full path to node
	 * @return integer
	 * @throws ConfigError
	 *             If entry not found or if not an int
	 */
	static public int getInt(String node) throws ConfigError {
		ConfigEntry entry = FVConfig.lookup(node);
		if (entry == null)
			throw new ConfigNotFoundError("node " + node + " does not exist");
		if (entry.getType() != ConfigType.INT)
			throw new ConfigWrongTypeError("tried to get an int but got a "
					+ entry.getType());
		return ((ConfIntEntry) entry).getInt();
	}

	public static void setBoolean(String node, boolean on) throws ConfigError {
		ConfigEntry entry = FVConfig.lookup(node);
		if (entry == null)
			entry = create(node, ConfigType.BOOL);
		else if (entry.getType() != ConfigType.BOOL)
			throw new ConfigWrongTypeError("tried to set an " + entry.getType()
					+ " to boolean");
		ConfBoolEntry ei = (ConfBoolEntry) entry;
		ei.setBool(on);
	}

	static public boolean getBoolean(String node) throws ConfigError {
		ConfigEntry entry = FVConfig.lookup(node);
		if (entry == null)
			throw new ConfigNotFoundError("node " + node + " does not exist");
		if (entry.getType() != ConfigType.BOOL)
			throw new ConfigWrongTypeError("tried to get a boolean but got a "
					+ entry.getType());
		return ((ConfBoolEntry) entry).getBool();
	}

	/**
	 * Sets an integer in the config Will dynamically create the path if it does
	 * not exist
	 *
	 * @param node
	 *            e.g., "path.to.configname"
	 * @param val
	 *            any integer
	 * @throws ConfigError
	 *             If trying to create the path conflicted with existing config
	 */

	static public void setString(String node, String val) throws ConfigError {
		ConfigEntry entry = FVConfig.lookup(node);
		if (entry == null)
			entry = create(node, ConfigType.STR);
		else if (entry.getType() != ConfigType.STR)
			throw new ConfigWrongTypeError("tried to set an " + entry.getType()
					+ " to string");
		ConfStrEntry ei = (ConfStrEntry) entry;
		ei.setString(val);
	}

	/**
	 * Return the integer associated with this node
	 *
	 * @param node
	 *            Full path to node
	 * @return integer
	 * @throws ConfigError
	 *             If entry not found or if not an int
	 */
	static public String getString(String node) throws ConfigError {
		ConfigEntry entry = FVConfig.lookup(node);
		if (entry == null)
			throw new ConfigNotFoundError("node " + node + " does not exist");
		if (entry.getType() != ConfigType.STR)
			throw new ConfigWrongTypeError("tried to get a string but got a "
					+ entry.getType());
		return ((ConfStrEntry) entry).getString();
	}

	/**
	 * Return the flowmap associated with this node
	 *
	 * @param node
	 * @return
	 * @throws ConfigError
	 */
	static public FlowMap getFlowMap(String node) throws ConfigError {
		ConfigEntry entry = FVConfig.lookup(node);
		if (entry == null)
			throw new ConfigNotFoundError("node " + node + " does not exist");
		if (entry.getType() != ConfigType.FLOWMAP)
			throw new ConfigWrongTypeError("tried to get a flowmap but got a "
					+ entry.getType());
		return ((ConfFlowMapEntry) entry).getFlowMap();
	}

	static synchronized public FlowMap getFlowSpaceFlowMap() {
		FlowMap flowMap;
		try {
			flowMap = FVConfig.getFlowMap(FVConfig.FLOWSPACE);
		} catch (ConfigError e) {
			throw new RuntimeException("WTF!?!  No FlowSpace defined!?!");
		}
		return flowMap;
	}

	/**
	 * Set the flowmap at this entry, creating it if it does not exist
	 *
	 * @param node
	 * @param val
	 * @throws ConfigError
	 */
	static public void setFlowMap(String node, FlowMap val) throws ConfigError {
		ConfigEntry entry = FVConfig.lookup(node);
		if (entry == null)
			entry = create(node, ConfigType.FLOWMAP);
		else if (entry.getType() != ConfigType.FLOWMAP)
			throw new ConfigWrongTypeError("tried to set an " + entry.getType()
					+ " to a FlowMap");
		ConfFlowMapEntry efm = (ConfFlowMapEntry) entry;
		efm.setFlowMap(val);
	}

	/**
	 * Returns a list of nodes at this subdirectory
	 *
	 * @param base
	 * @return List of nodes
	 * @throws ConfigError
	 */
	static synchronized public List<String> list(String base)
			throws ConfigError {
		ConfigEntry e = lookup(base);
		if (e == null)
			throw new ConfigNotFoundError("base not found: " + base);
		if (e.getType() != ConfigType.DIR)
			throw new ConfigWrongTypeError("node " + base + " is a "
					+ e.getType() + ", not a DIR");
		return ((ConfDirEntry) e).list();

	}

	/**
	 * Recusively step through the config tree from the root and call walker on
	 * each non-directory node
	 *
	 * @param walker
	 */

	public synchronized static List<String> getConfig(String name) {
		ConfigEntry val;
		if (name.equals("."))
			val = FVConfig.root;
		else
			val = lookup(name);
		// FIXME: change val.getValue() to return a list instead of an array
		if (val == null)
			return null;
		if (val instanceof ConfDirEntry) {
			ConfigPrinter configPrinter = new ConfigPrinter("");
			if (name.equals("."))
				FVConfig.walk(configPrinter);
			else
				FVConfig.walksubdir(name, configPrinter);
			return configPrinter.getOut();
		} else {
			String[] strings = val.getValue();
			List<String> stringList = new LinkedList<String>();
			for (int i = 0; i < strings.length; i++)
				stringList.add(strings[i]);
			return stringList;
		}
	}

	public synchronized static void setConfig(String name, String val)
			throws ConfigNotFoundError {
		ConfigEntry entry = lookup(name);
		if (entry == null)
			throw new ConfigNotFoundError("config entry not found: " + name);
		entry.setValue(val);
	}

	static public void walk(ConfigIterator walker) {
		walksubdir("", root, walker);
	}

	static public void walksubdir(String base, ConfigIterator walker) {
		ConfigEntry e = lookup(base);
		walksubdir(base, e, walker);
	}

	static private void walksubdir(String base, ConfigEntry e,
			ConfigIterator walker) {
		if (e.getType() == ConfigType.DIR) {
			ConfDirEntry dir = (ConfDirEntry) e;
			for (ConfigEntry entry : dir.listEntries())
				walksubdir(base + FS + entry.getName(), entry, walker);
		} else
			walker.visit(base, e);

	}

	public static void watch(FVEventHandler handler, String name) {
		ConfigEntry e = lookup(name);
		if (e == null)
			FVLog.log(LogLevel.WARN, handler,
					"tried to watch non-existent config: " + name);
		else
			e.watch(handler);
	}

	public static void unwatch(FVEventHandler handler, String name) {
		ConfigEntry e = lookup(name);
		if (e == null)
			FVLog.log(LogLevel.WARN, handler,
					"tried to unwatch non-existent config: " + name);
		else
			e.unwatch(handler);
	}

	/**
	 * Tell all of the FVHandler's that are watching this node that the value
	 * has changed and they need to refresh it
	 *
	 * Fails silently if node does not exist
	 *
	 * @param node
	 *            nodename
	 */
	public static void sendUpdates(String node) {
		ConfigEntry entry = lookup(node);
		if (entry == null) {
			FVLog.log(LogLevel.WARN, null,
					"tried to signal update for non-existent config node: "
							+ node);
			return;
		}
		entry.sendUpdates(node);
	}

	/**
	 * Read XML-encoded config from filename
	 *
	 * @param filename
	 *            fully qualified or relative pathname
	 */
	public static synchronized void readFromFile(String filename)
			throws FileNotFoundException {
		XMLDecoder dec = new XMLDecoder(new BufferedInputStream(
				new FileInputStream(filename)));
		FVConfig.root = (ConfDirEntry) dec.readObject();
		// Check to see if version number exists. If not set it
		try {
			int version = FVConfig.getInt(CONFIG_VERSION_STR);
			if (version < CONFIG_VERSION)
				updateVersion(version, filename);
		} catch (ConfigError e) {
			updateVersion(-1, filename);
		}
	}

	/*
	 * Given the current version of this config, auto-magic update it to the
	 * current version. This is where we "port" old configs up to the current
	 * config file format
	 *
	 * @param currVersion the version of the config we are updating FROM
	 */
	private static void updateVersion(int currVersion, String filename) {
		if (currVersion < 1) // Update Version number
			updateVersion_0_to_1();
		if (currVersion < 2)
			updateVersion_1_to_2();

		// set the version number to current
		try {
			FVConfig.setInt(FVConfig.CONFIG_VERSION_STR,
					FVConfig.CONFIG_VERSION);
		} catch (ConfigError e) {
			FVLog.log(LogLevel.ALERT, null, "Error updating config: + "
					+ e.getMessage());
			e.printStackTrace();
		}
		// Write out update config
		if (VeRTIGO.getInstance() != null)
			VeRTIGO.getInstance().checkPointConfig();
	}

	/*
	 * add a "switches!default!flood_perm" branch to the config
	 */

	protected static void updateVersion_1_to_2() {
		ConfDirEntry switches = new ConfDirEntry(FVConfig.SWITCHES);
		ConfDirEntry def = new ConfDirEntry(FVConfig.SWITCHES_DEFAULT);
		ConfStrEntry floodPerm = new ConfStrEntry(FVConfig.FLOOD_PERM);
		floodPerm.setString(FVConfig.SUPER_USER); // give the root slice flood
		// perms by default
		def.add(floodPerm);
		switches.add(def);
		FVConfig.root.add(switches);
	}

	/**
	 * change the superuser account from "root" to "fvadmin"
	 */

	private static void updateVersion_0_to_1() {
		// Need to change name of su slice to fvadmin from root
		ConfDirEntry sliceList = (ConfDirEntry) lookup(FVConfig.SLICES);
		if (sliceList.entries.containsKey("root")) { // this should always be
			// the case but check
			// anyways
			ConfigEntry suSliceEntry = sliceList.entries.get("root");
			suSliceEntry.setName(FVConfig.SUPER_USER);
			sliceList.remove("root");
			sliceList.add(suSliceEntry);
		}
		// Need to change name of creators of any slice that was created by root
		// to fvadmin
		for (String sliceName : sliceList.entries.keySet()) {
			String base = FVConfig.SLICES + FVConfig.FS + sliceName;

			try {
				String creator = FVConfig.getString(base + FVConfig.FS
						+ FVConfig.SLICE_CREATOR);
				if (creator.equals("root")) {
					FVConfig.setString(base + FVConfig.FS
							+ FVConfig.SLICE_CREATOR, FVConfig.SUPER_USER);
				}
			} catch (ConfigError e) {
				FVLog.log(LogLevel.ALERT, null, "Error updating config: + "
						+ e.getMessage());
				e.printStackTrace();
			}
		}
	}

	/**
	 * Write XML-encoded config to filename
	 *
	 * @param filename
	 *            fully qualified or relative pathname
	 */
	public static synchronized void writeToFile(String filename)
			throws FileNotFoundException {
		XMLEncoder enc = new XMLEncoder(new BufferedOutputStream(
				new FileOutputStream(filename)));
		// FVConfig.walk(new ConfigDumper(System.err));
		enc.writeObject(FVConfig.root);
		enc.close();
	}

	public static void createSlice(String sliceName,
			String controller_hostname, int controller_port, String passwd,
			String slice_email, String creatorSlice) throws InvalidSliceName,
			DuplicateControllerException {
		FVConfig.createSlice(sliceName, controller_hostname, controller_port,
				passwd, APIAuth.getSalt(), slice_email, creatorSlice);
	}

	public synchronized static void createSlice(String sliceName,
			String controller_hostname, int controller_port, String passwd,
			String salt, String slice_email, String creatorSlice)
			throws InvalidSliceName, DuplicateControllerException {
		if (sliceName.contains(FS))
			throw new InvalidSliceName("invalid slicename: cannot contain '"
					+ FS + "' : " + sliceName);

		// Check to make sure we aren't creating a slice with a pre-existing
		// controller hostname/port pair
		try {
			List<String> slices = FVConfig.list(FVConfig.SLICES);
			for (String sliceNameItr : slices) {
				String baseItr = FVConfig.SLICES + FVConfig.FS + sliceNameItr
						+ FVConfig.FS;
				try {
					if (getString(baseItr + "controller_hostname")
							.equalsIgnoreCase(controller_hostname)) {
						if (getInt(baseItr + "controller_port") == controller_port) {
							throw new DuplicateControllerException(
									getString(baseItr + "controller_hostname"),
									getInt(baseItr + "controller_port"),
									sliceName, "created");
						}
					}
				} catch (ConfigError e) {
					// Ignore, but continue looking for other slices
					e.printStackTrace();
				}
			}
		} catch (ConfigError e) {
			// ignore assume we can create slice
		}

		String base = FVConfig.SLICES + FS + sliceName;
		try {
			FVConfig.create(base, ConfigType.DIR);
			FVConfig.create(base + FS + LINKS, ConfigType.DIR);
			FVConfig.setString(base + FS + FVConfig.SLICE_CONTACT_EMAIL,
					slice_email);
			FVConfig.setString(base + FS + FVConfig.SLICE_CONTROLLER_HOSTNAME,
					controller_hostname);
			FVConfig.setInt(base + FS + FVConfig.SLICE_CONTROLLER_PORT,
					controller_port);
			FVConfig.setString(base + FS + FVConfig.SLICE_SALT, salt);
			FVConfig.setString(base + FS + FVConfig.SLICE_CRYPT, APIAuth
					.makeCrypt(salt, passwd));
			FVConfig
					.setString(base + FS + FVConfig.SLICE_CREATOR, creatorSlice);
			
			/**
			 * @authors roberto.doriguzzi matteo.gerola
			 */
			FVConfig.setBoolean(base + FS + FVConfig.SLICE_ISVIRTUAL,
					false);
			
		} catch (ConfigError e) {
			throw new RuntimeException("failed to create slice " + sliceName
					+ "::" + e);
		}
	}

	public static String readPasswd(String prompt) throws IOException {
		Console cons = System.console();
		if (cons != null) {
			char[] passwd = cons.readPassword(prompt);
			return new String(passwd);
		} else {
			/**
			 * This is a hack to get around the fact that in java,
			 * System.console() will return null if the calling process is not a
			 * tty, e.g., with `fvctl listSlices | less`
			 */
			System.err.print(prompt);
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					System.in));
			return reader.readLine();
		}
	}

	public static synchronized void deleteSlice(String sliceName)
			throws ConfigNotFoundError {
		sliceName = FVConfig.sanitize(sliceName);
		ConfDirEntry sliceList = (ConfDirEntry) lookup(FVConfig.SLICES);
		if (!sliceList.entries.containsKey(sliceName))
			throw new ConfigNotFoundError("slice does not exist: " + sliceName);
		sliceList.entries.remove(sliceName);
	}

	public static boolean confirm(String base) {
		return (lookup(base) != null);
	}

	/**
	 * Return the name of the super user account
	 *
	 * @return
	 */
	public static boolean isSupervisor(String user) {
		return SUPER_USER.equals(user);
	}

	/**
	 * Replace all non-kosher characters with underscores
	 *
	 * @param str
	 * @return
	 */
	public static String sanitize(String str) {
		return str.replaceAll("[^a-zA-Z0-9,_+=:-]", "_");
	}

	/**
	 * Create a default config file and write it to arg1
	 *
	 * @param args
	 *            filename
	 * @throws FileNotFoundException
	 * @throws ConfigError
	 * @throws NumberFormatException
	 */

	public static void main(String args[]) throws FileNotFoundException,
			IOException, NumberFormatException, ConfigError {
		if (args.length < 1) {
			System.err
					.println("Usage: FVConfig config.xml [fvadmin_passwd] [of_listen_port] [rpc_listen_port]");
			System.exit(1);
		}
		String filename = args[0];
		String passwd;
		if (args.length > 1)
			passwd = args[1];
		else
			passwd = FVConfig
					.readPasswd("Enter password for account 'fvadmin' on the flowvisor:");
		System.err.println("Generating default config to " + filename);
		DefaultConfig.init(passwd);
		// set the listen port, if requested
		if (args.length > 2)
			FVConfig.setInt(FVConfig.LISTEN_PORT, Integer.valueOf(args[2]));
		// set the api listen port, if requested
		if (args.length > 3)
			FVConfig.setInt(FVConfig.API_WEBSERVER_PORT, Integer
					.valueOf(args[3]));

		FVConfig.writeToFile(filename);
	}

	public synchronized static void setPasswd(String sliceName, String salt,
			String crypt) {
		String base = FVConfig.SLICES + FVConfig.FS + sliceName;
		try {
			FVConfig.setString(base + FVConfig.FS + FVConfig.SLICE_SALT, salt);
			FVConfig
					.setString(base + FVConfig.FS + FVConfig.SLICE_CRYPT, crypt);
		} catch (ConfigError e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	/**
	 * @name enableVTPlannerStats
	 * @description Enables or disables the internal module that collects statistics of traffic. These stats are used 
	 * 				by the VT-Planner module to find the optimal aggregation for virtual links
	 * @authors roberto.doriguzzi matteo.gerola
	 * @param enable = 1 means enable, 0 disable
	 */
	public static void enableVTPlannerStats(String enable) {
		try {
			FVConfig.setInt(ENABLE_VTPLANNER_STATS, Integer.parseInt(enable));
		} catch (ConfigError e) {
			throw new RuntimeException("failed to enable/disable the statistics storage module " + "::" + e);
		}
		
	}
	
	/**
	 * @name setVTPlannerStatsTimers
	 * @description Sets the timers for the VTPlanner statistics collector module. The first is the period of
	 * 				time between two stats requests, the second is the stats expiration time.
	 * @authors roberto.doriguzzi matteo.gerola
	 * @param timer = time in seconds
	 * @param expiration = time in seconds
	 */
	public static void setVTPlannerStatsTimers(String timer, String expiration) {
		try {
			FVConfig.setString(VTPLANNER_STATS_TIMER, timer);
			FVConfig.setString(VTPLANNER_STATS_EXPIR, expiration);
		} catch (ConfigError e) {
			throw new RuntimeException("failed to set the statistics timers " + "::" + e);
		}
		
	}
	
	/**
	 * @name setDbInfo
	 * @authors roberto.doriguzzi matteo.gerola
	 * @param dbType
	 * @param ipAddress
	 * @param port
	 * @param user
	 * @param passwd
	 */
	public static void setDbInfo(String dbType, String ipAddress,
			String port, String user, String passwd) {
		try {
			FVConfig.setString(DB_TYPE, dbType);
			FVConfig.setString(DB_IP, ipAddress);
			FVConfig.setInt(DB_PORT, Integer.parseInt(port));
			FVConfig.setString(DB_USER, user);
			FVConfig.setString(DB_PASSWD, passwd);
		} catch (ConfigError e) {
			throw new RuntimeException("failed set database informations " + "::" + e);
		}
		
	}

	/**
	 * @name addVirtualLink
	 * @authors roberto.doriguzzi matteo.gerola
	 * @param sliceName
	 * @param vtLink
	 */
	public static void addVirtualLink(String sliceName, VTLink vtLink) {
		String base = SLICES + FS + sliceName + FS + LINKS + FS + Integer.toString(vtLink.linkId);
		try {
			FVConfig.create(base, ConfigType.DIR);
			FVConfig.create(base + FS + HOPS, ConfigType.DIR);
			FVConfig.setInt(base + FS + HOPS_NUMBER,vtLink.hopsNumber);
		} catch (ConfigError e) {
			throw new RuntimeException("failed to create virtual link " + vtLink.linkId
					+ "::" + e);
		}
		for(VTHop currentHop:vtLink.vtHopList) {
			String base2 = base + FS + HOPS + FS + currentHop.hopId;
			try {
				FVConfig.create(base2, ConfigType.DIR);
				FVConfig.setString(base2 + FS + HOP_SRC_DPID, currentHop.srcDpid);
				FVConfig.setInt(base2 + FS + HOP_SRC_PORT, currentHop.srcPort);
				FVConfig.setString(base2 + FS + HOP_DST_DPID, currentHop.dstDpid);
				FVConfig.setInt(base2 + FS + HOP_DST_PORT, currentHop.dstPort);
			} catch (ConfigError e) {
				throw new RuntimeException("failed to create hop " + currentHop.hopId
						+ "::" + e);
			}
		}
	}

	/**
	 * @name changeVirtualLink
	 * @authors roberto.doriguzzi matteo.gerola
	 * @param sliceName
	 * @param vtLink
	 * @throws ConfigError 
	 */
	public static void changeVirtualLink(String sliceName, VTLink vtLink) throws ConfigError {
		String base = SLICES + FS + sliceName + FS + LINKS + FS + Integer.toString(vtLink.linkId);
		FVConfig.setInt(base + FS + HOPS_NUMBER,vtLink.hopsNumber);
		for(VTHop currentHop:vtLink.vtHopList) {
			String base2 = base + FS + HOPS + FS + currentHop.hopId;
			FVConfig.setString(base2 + FS + HOP_SRC_DPID, currentHop.srcDpid);
			FVConfig.setInt(base2 + FS + HOP_SRC_PORT, currentHop.srcPort);
			FVConfig.setString(base2 + FS + HOP_DST_DPID, currentHop.dstDpid);
			FVConfig.setInt(base2 + FS + HOP_DST_PORT, currentHop.dstPort);
		}	
		
	}

	/**
	 * @name deleteVirtualLink
	 * @authors roberto.doriguzzi matteo.gerola
	 * @param sliceName
	 * @param parseInt
	 */
	public static void deleteVirtualLink(String sliceName, int linkId) {
		ConfDirEntry linkList = (ConfDirEntry) lookup(SLICES + FS + sliceName + FS + LINKS);
		linkList.entries.remove(Integer.toString(linkId));
	}
}
