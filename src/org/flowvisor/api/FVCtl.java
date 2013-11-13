/**
 *
 */
package org.flowvisor.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.flowvisor.api.FlowChange.FlowChangeOp;
import org.flowvisor.config.BracketParse;
import org.flowvisor.config.FVConfig;
import org.flowvisor.exceptions.MalformedFlowChange;
import org.flowvisor.exceptions.MapUnparsable;
import org.flowvisor.flows.FlowDBEntry;
import org.flowvisor.vtopology.vtstatistics.VTStatsUtils;
import org.openflow.protocol.statistics.OFStatistics;


/**
 * Client side stand alone command-line tool for invoking the FVUserAPI
 *
 * This is pretty hacky and just for testing; people should write their own
 * clients and/or call the XMLRPC directly
 *
 * @author capveg
 *
 */
public class FVCtl {
	String URL;
	XmlRpcClientConfigImpl config;
	XmlRpcClient client;
	static APICmd[] cmdlist = new APICmd[] {
		new APICmd("listSlices", 0),
		new APICmd("createSlice", 3, "<slicename> <controller_url> <email>"),
		new APICmd("changeSlice", 3, "<slicename> <key> <value>"),
		new APICmd("deleteSlice", 1, "<slicename>"),
		new APICmd("changePasswd", 1, "<slicename>"),
		new APICmd("getSliceInfo", 1, "<slicename>"),
		
		new APICmd("setDbInfo", 5, "<database_type> <database_address> <database_port> <user> <password>"),
		new APICmd("addLink", 2, "<slicename> <action>"),
		new APICmd("changeLink", 3, "<slicename> <linkname> <action>"),
		new APICmd("deleteLink", 2, "<slicename> <linkname>"),
		new APICmd("getVirtualLinks", 1, "<slicename>"),

		new APICmd("enableVTPlannerStats", 1, "<enable>"),
		new APICmd("setVTPlannerTimers", 2, "<timer> <exp_time>"),
		new APICmd("getVTPlannerTimers", 0),
		new APICmd("getVTPlannerSwitchInfo", 1, "<switchid>"),
		new APICmd("getVTPlannerPortInfo", 2, "<switchid> <port>"),
		new APICmd("getVTPlannerPortStats", 4, "<switchid> <port> <datetime1> <datetime2>"),
		new APICmd("getVTPlannerQueueStats", 5, "<switchid> <port> <queue> <datetime1> <datetime2>"),
		
		new APICmd("getSliceStats", 1, "<slicename>"),
		new APICmd("getSwitchStats", 1, "<dpid>"),
		new APICmd("getSwitchFlowDB", 1, "<dpid>"),
		new APICmd("getSliceRewriteDB", 2, "<slicename> <dpid>"),

		new APICmd("listFlowSpace", 0),
		new APICmd("removeFlowSpace", 1, "<id>"),
		new APICmd("addFlowSpace", 4, "<dpid> <priority> <match> <actions>"),
		new APICmd("changeFlowSpace", 5,
		"<id> <dpid> <priority> <match> <actions>"),

		new APICmd("listDevices", 0),
		new APICmd("getDeviceInfo", 1, "<dpid>"),
		new APICmd("getLinks", 0),

		new APICmd("ping", 1, "<msg>"),
		new APICmd("getConfig", 1, "<configEntry>"),
		new APICmd("setConfig", 2, "<configEntry> <value>"),

		new APICmd("registerCallback", 2, "<URL> <cookie>"),
		new APICmd("unregisterCallback", 0), };

	static class APICmd {
		String name;
		int argCount;
		String usage;
		static HashMap<String, APICmd> cmdlist = new HashMap<String, APICmd>();

		APICmd(String name, int argCount, String usage) {
			this.name = name;
			this.argCount = argCount;
			this.usage = usage;
			cmdlist.put(name, this);
		}

		APICmd(String name, int argCount) {
			this(name, argCount, "");
		}

		@SuppressWarnings("unchecked")
		// Need to figure out magic java sauce to fix this
		void invoke(FVCtl client, String args[]) throws SecurityException,
		NoSuchMethodException, IllegalArgumentException,
		IllegalAccessException, InvocationTargetException {
			Class<String>[] params = new Class[args.length];
			for (int i = 0; i < args.length; i++)
				params[i] = String.class;
			Method m = FVCtl.class.getMethod("run_" + this.name, params);
			m.invoke(client, (Object[]) args);
		}
	}

	/**
	 *
	 * @param URL
	 *            Server URL
	 */
	public FVCtl(String URL) {
		this.URL = URL;
	}

	/**
	 * Init connection to XMLRPC Server in URL
	 *
	 * @throws MalformedURLException
	 *
	 * @throws Exception
	 */
	public void init(String user, String passwd) throws MalformedURLException {
		this.installDumbTrust();
		config = new XmlRpcClientConfigImpl();
		config.setBasicUserName(user);
		config.setBasicPassword(passwd);
		config.setServerURL(new URL(this.URL));
		config.setEnabledForExtensions(true);

		client = new XmlRpcClient();
		// client.setTransportFactory(new
		// XmlRpcCommonsTransportFactory(client));
		// client.setTransportFactory(new )
		client.setConfig(config);
	}

	public void runJetty(String user, String passwd, String methodName, String[] args){
		try {
			this.installDumbTrust();
			// Jetty Client
			AuthorizedServiceProxy proxy;
			proxy = new AuthorizedServiceProxy(FVUserAPIJSON.class, this.URL, user, passwd);
			FVUserAPIJSON apiService = (FVUserAPIJSON)proxy.create();
			Class<?> [] argTypes = new Class[args.length];
			for (int argNum = 0; argNum < args.length; argNum++){
				argTypes[argNum] = String.class;
			}
			Method serviceMethod = FVUserAPIJSON.class.getMethod(methodName, argTypes);
			System.out.println("executing request");
			Object stats  = serviceMethod.invoke(apiService, (Object[])args);
			System.out.println("Reponse: " + stats);
			System.out.println("----------------------------------------");

		} catch(Exception e){
			e.printStackTrace();
		}
	}

	private TrustManager[] getTrustAllManager(){
		// Create a trust manager that does not validate certificate chains
		// System.err.println("WARN: blindly trusting server cert - FIXME");
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(X509Certificate[] certs,
					String authType) {
				// Trust always
			}

			public void checkServerTrusted(X509Certificate[] certs,
					String authType) {
				// Trust always
			}
		} };
		return trustAllCerts;
	}
	public void installDumbTrust() {

		TrustManager[] trustAllCerts = getTrustAllManager();
		try {
			// Install the all-trusting trust manager
			SSLContext sc = SSLContext.getInstance("SSL");
			// Create empty HostnameVerifier
			HostnameVerifier hv = new HostnameVerifier() {
				public boolean verify(String arg0, SSLSession arg1) {
					return true;
				}
			};

			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection
			.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier(hv);
		} catch (KeyManagementException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

	public void run_listDevices() throws XmlRpcException {
		Object[] reply = (Object[]) this.client.execute("api.listDevices",
				new Object[] {});
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		for (int i = 0; i < reply.length; i++) {
			String dpid = (String) reply[i];
			System.out.println("Device " + i + ": " + dpid);
		}
	}

	@SuppressWarnings("unchecked")
	public void run_getDeviceInfo(String dpidStr) throws XmlRpcException {
		Map<String, Object> reply = (Map<String, Object>) this.client.execute(
				"api.getDeviceInfo", new Object[] { dpidStr });
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		for (String key : reply.keySet()) {
			System.out.println(key + "=" + reply.get(key));
		}
	}

	public void run_getConfig(String name) throws XmlRpcException {

		Object reply = this.client.execute("api.getConfig",
				new Object[] { name });
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		Object objects[] = (Object[]) reply;
		if (objects.length == 1)
			System.out.println(name + " = " + (String) objects[0]);
		else
			for (int i = 0; i < objects.length; i++)
				System.out
				.println(name + " " + i + " = " + (String) objects[i]);
	}

	public void run_setConfig(String name, String value) throws XmlRpcException {
		Object reply = this.client.execute("api.setConfig", new Object[] {
				name, value });
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		if (!(reply instanceof Boolean)) {
			System.err.println("Didn't get boolean reply?; got" + reply);
			System.exit(-1);
		}
		boolean success = ((Boolean) reply).booleanValue();
		if (success) {
			System.out.println("success");
			System.exit(0);
		} else {
			System.out.println("failure");
			System.exit(-1);
		}
	}

	@SuppressWarnings("unchecked")
	public void run_getLinks() throws XmlRpcException, MapUnparsable {
		Object[] reply = (Object[]) this.client.execute("api.getLinks",
				new Object[] {});
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		Map<String, String> map;
		for (int i = 0; i < reply.length; i++) {
			if (!(reply[i] instanceof Map<?, ?>)) {
				System.err.println("not a map: Skipping unparsed reply: "
						+ reply[i]);
			} else {
				map = (Map<String, String>) reply[i];
				LinkAdvertisement ad = LinkAdvertisement.fromMap(map);
				System.out.println("Link " + i + ": " + ad);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public void run_getSwitchFlowDB(String dpidString) throws XmlRpcException,
	MapUnparsable {
		Object[] reply = (Object[]) this.client.execute("api.getSwitchFlowDB",
				new Object[] { dpidString });
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		Map<String, String> map;
		FlowDBEntry flowDBEntry;
		for (int i = 0; i < reply.length; i++) {
			if (!(reply[i] instanceof Map<?, ?>)) {
				System.err.println("not a map: Skipping unparsed reply: "
						+ reply[i]);
			} else {
				map = (Map<String, String>) reply[i];
				flowDBEntry = new FlowDBEntry();
				flowDBEntry.fromBacketMap(map);
				System.out.println("DBEntry " + i + ": " + flowDBEntry);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public void run_getSliceRewriteDB(String sliceName, String dpidStr)
	throws XmlRpcException {

		Object ret = this.client.execute("api.getSliceRewriteDB", new Object[] {
				sliceName, dpidStr });
		Map<String, Object[]> flowRewriteDB;
		if (!(ret instanceof Map)) {
			throw new XmlRpcException("unknown reply type "
					+ ret.getClass().toString());
		}
		flowRewriteDB = (Map<String, Object[]>) ret;
		for (String original : flowRewriteDB.keySet()) {
			System.out.println("============ Original");
			System.out.println(original);
			System.out.println("\n=========== Rewritten to:");
			Object[] objs = flowRewriteDB.get(original);
			Map<String, String> rewrite;
			for (int i = 0; i < objs.length; i++) {
				rewrite = (Map<String, String>) objs[i];
				System.out.println("\t\t" + BracketParse.encode(rewrite));
			}

		}
	}

	public void run_changePasswd(String sliceName) throws IOException,
	XmlRpcException {
		String passwd = FVConfig.readPasswd("New password: ");
		Boolean reply = (Boolean) this.client.execute("api.changePasswd",
				new Object[] { sliceName, passwd });
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		if (reply)
			System.out.println("success!");
		else
			System.err.println("failed!");
	}

	public void run_changeSlice(String sliceName, String key, String value)
	throws IOException, XmlRpcException {
		Boolean reply = (Boolean) this.client.execute("api.changeSlice",
				new Object[] { sliceName, key, value });
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		if (reply)
			System.out.println("success!");
		else
			System.err.println("failed!");
	}

	@SuppressWarnings("unchecked")
	public void run_getSliceInfo(String sliceName) throws IOException,
	XmlRpcException {

		Object o = this.client.execute("api.getSliceInfo",
				new Object[] { sliceName });
		if (o == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		Map<String, String> reply = null;
		if (o instanceof Map<?, ?>)
			reply = (Map<String, String>) o;

		System.out.println("Got reply:");
		for (String key : reply.keySet())
			System.out.println(key + "=" + reply.get(key));
	}
	
	/**
	 * @name run_enableVTPlannerStats
	 * @authors roberto.doriguzzi matteo.gerola
	 */
	public void run_enableVTPlannerStats(String enable) throws XmlRpcException {
		Boolean reply = (Boolean) this.client
				.execute("api.enableVTPlannerStats", new Object[] {enable});
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		else System.out.println("success!");
	}
	
	/**
	 * @name run_setVTPlannerTimers
	 * @authors roberto.doriguzzi matteo.gerola
	 */
	public void run_setVTPlannerTimers(String timer, String expiration) throws XmlRpcException {
		Boolean reply = (Boolean) this.client
				.execute("api.setVTPlannerTimers", new Object[] {timer, expiration});
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		else System.out.println("success!");
	}
	
	/**
	 * @name run_getVTPlannerTimers
	 * @authors roberto.doriguzzi matteo.gerola
	 */
	public void run_getVTPlannerTimers() throws XmlRpcException {
		Object[] reply = (Object[]) this.client.execute("api.getVTPlannerTimers", new Object[] {});
		
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		else if (reply.length > 0){
			String [] reply_timers = ((String)reply[0]).split(",");
			System.out.println("\nVTPlanner Statistics Timers (s=seconds, m=minutes, h=hours, d=days, w=weeks)");
			System.out.println("\nSampling period: " + reply_timers[0]);
			System.out.println("Expiration time: " + reply_timers[1]);
			System.out.println("success!");
		}
	}
	
	/**
	 * @name run_getVTPlannerPortStats
	 * @authors roberto.doriguzzi matteo.gerola
	 */

	public void run_getVTPlannerPortStats(String switchId, String port, String time1, String time2) throws XmlRpcException, MapUnparsable {
		Object[] reply = (Object[]) this.client.execute("api.getVTPlannerPortStats",
				new Object[] {switchId, port, time1, time2});
		
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		else {
			System.out.println("\nSWITCH_ID: " + switchId + " PORT_NUMBER: " + port + "\n");
			System.out.println("TIME\t\t\t\t\tBYTES_RX\t\tBYTES_TX\t\tPACKETS_RX\t\tPACKETS_TX");
			
			for (int i = 0; i < reply.length; i++) {
				String reply_str = reply[i].toString();
				String [] reply_str_items = reply_str.split(",");
				String new_reply_str = new String();
				if(reply_str_items.length == 5) { // date, rxBytes, txBytes, rxPackets, txPackets 
					Date date = new Date(Long.parseLong(reply_str_items[0]));
					new_reply_str += date.toString() + "\t\t";
					new_reply_str += String.format("%010d",Long.parseLong(reply_str_items[1])) + "\t\t";
					new_reply_str += String.format("%010d",Long.parseLong(reply_str_items[2])) + "\t\t";
					new_reply_str += String.format("%010d",Long.parseLong(reply_str_items[3])) + "\t\t";
					new_reply_str += String.format("%010d",Long.parseLong(reply_str_items[4]));
				}
				System.out.println(new_reply_str);
			}
			System.out.println("success!");
		}
	}
	
	/**
	 * @name run_getVTPlannerQueueStats
	 * @authors roberto.doriguzzi matteo.gerola
	 */

	public void run_getVTPlannerQueueStats(String switchId, String port, String queue, String time1, String time2) throws XmlRpcException, MapUnparsable {
		Object[] reply = (Object[]) this.client.execute("api.getVTPlannerQueueStats",
				new Object[] {switchId, port, queue, time1, time2});
		
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		else {
			System.out.println("\nSWITCH_ID: " + switchId + " PORT_NUMBER: " + port + " QUEUE_ID: " + queue + "\n");
			System.out.println("TIME\t\t\t\t\tBYTES_TX\t\tPACKETS_TX\t\tERRORS_TX");
			
			for (int i = 0; i < reply.length; i++) {
				String reply_str = reply[i].toString();
				String [] reply_str_items = reply_str.split(",");
				String new_reply_str = new String();
				if(reply_str_items.length == 4) { // date, txBytes, txPackets, txErrors 
					Date date = new Date(Long.parseLong(reply_str_items[0]));
					new_reply_str += date.toString() + "\t\t";
					new_reply_str += String.format("%010d",Long.parseLong(reply_str_items[1])) + "\t\t";
					new_reply_str += String.format("%010d",Long.parseLong(reply_str_items[2])) + "\t\t";
					new_reply_str += String.format("%010d",Long.parseLong(reply_str_items[3]));
				}
				System.out.println(new_reply_str);
			}
			System.out.println("success!");
		}
	}
	
	/**
	 * @name run_getVTPlannerPortInfo
	 * @authors roberto.doriguzzi matteo.gerola
	 */

	public void run_getVTPlannerPortInfo(String switchId, String port) throws XmlRpcException, MapUnparsable {
		Object[] reply = (Object[]) this.client.execute("api.getVTPlannerPortInfo",
				new Object[] {switchId, port});
		
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		else {
			System.out.println("\nSWITCH_ID: " + switchId + " PORT NUMBER: " + port + "\n");
			System.out.println("PORT\t\tCONFIG\t\tFEATURES\tSTATE");
			for (int i = 0; i < reply.length; i++) {
				String tmp = reply[i].toString();
				System.out.println(tmp.replace(",","\t\t"));
			}
			System.out.println("success!");
		}
	}
	
	/**
	 * @name run_getVTPlannerSwitchInfo
	 * @authors roberto.doriguzzi matteo.gerola
	 */

	public void run_getVTPlannerSwitchInfo(String switchId) throws XmlRpcException, MapUnparsable {
		Object[] reply = (Object[]) this.client.execute("api.getVTPlannerSwitchInfo",
				new Object[] {switchId});
		
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		else {
			int maxLength = 0;
			for (int i = 0; i < reply.length; i++) {
				String reply_str = reply[i].toString();
				for(String tmp_str : reply_str.split(",,")) {
					if(tmp_str.length() > maxLength) maxLength = tmp_str.length();
				}
			}
			maxLength+=2;
			
			System.out.println("\nDATAPATH_ID" + VTStatsUtils.fillString(' ', maxLength-String.format("DATAPATH_ID").length()) + 
			   "DPATH_DESCRIPTION" + VTStatsUtils.fillString(' ', maxLength-String.format("DPATH_DESCRIPTION").length()) +
			   "MANUFACTURER" + VTStatsUtils.fillString(' ', maxLength-String.format("MANUFACTURER").length()) + 
			   "SERIAL_NUMBER" + VTStatsUtils.fillString(' ', maxLength-String.format("SERIAL_NUMBER").length()) + 
			   "CAPABILITIES" + VTStatsUtils.fillString(' ', maxLength-String.format("CAPABILITIES").length()) + 
			   "OF_VERSION" + VTStatsUtils.fillString(' ', maxLength-String.format("OF_VERSION").length()) + 
			   "PORTS\n");
			
			for (int i = 0; i < reply.length; i++) {
				String reply_str = reply[i].toString();
				String new_str = "";
				for(String tmp_str : reply_str.split(",,")) {
					new_str += tmp_str + VTStatsUtils.fillString(' ', maxLength-tmp_str.length());
				}			
				System.out.println(new_str);
			}
			System.out.println("success!");
		}
	}

	/**
	 * @name run_setDbInfo
	 * @authors roberto.doriguzzi matteo.gerola
	 */
	public void run_setDbInfo(String dbType, String ipAddress , String port, String user, String passwd) 
	throws IOException, XmlRpcException {
		Boolean reply = (Boolean) this.client
				.execute("api.setDbInfo", new Object[] { dbType, ipAddress, port, user, passwd});
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		if (reply)
			System.out.println("success!");
		else
			System.err.println("failed!");
	}
	
	/**
	 * @name run_addLink
	 * @authors roberto.doriguzzi matteo.gerola

	 */
	public void run_addLink(String sliceName, String linkStructure) throws IOException, XmlRpcException {
		Boolean reply = (Boolean) this.client
				.execute("api.addLink", new Object[] { sliceName, linkStructure });
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		if (reply)
			System.out.println("success!");
		else
			System.err.println("failed!");
	}
	
	/**
	 * @name run_changeLink
	 * @authors roberto.doriguzzi matteo.gerola
	 */
	public void run_changeLink(String sliceName, String linkName, String linkStructure) throws IOException, XmlRpcException {
		Boolean reply = (Boolean) this.client
				.execute("api.changeLink", new Object[] { sliceName, linkName, linkStructure });
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		if (reply)
			System.out.println("success!");
		else
			System.err.println("failed!");
	}
	
	/**
	 * @name run_deleteLink
	 * @authors roberto.doriguzzi matteo.gerola
	 */
	public void run_deleteLink(String sliceName, String linkName) throws XmlRpcException {
		Boolean reply = (Boolean) this.client.execute("api.deleteLink",
				new Object[] { sliceName, linkName });
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		if (reply)
			System.out.println("success!");
		else
			System.err.println("failed!");
	}
	
	/**
	 * @name run_getVirtualLinks
	 * @authors roberto.doriguzzi matteo.gerola
	 */
	@SuppressWarnings("unchecked")
	public void run_getVirtualLinks(String sliceName) throws XmlRpcException, MapUnparsable {
		Map<String,Map<String, String>> reply = (Map<String,Map<String, String>>) this.client.execute("api.getVirtualLinks",
				new Object[] {sliceName});
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		
		Map<String,Map<String, String>> copy1 = new TreeMap<String,Map<String, String>>(reply);
		Set<String> keySet1 = copy1.keySet();
		for(String linkId : keySet1){
			System.out.println("Virtual Link " + linkId + ": ");
			Map<String, String> copy2 = new TreeMap<String, String>(copy1.get(linkId));
			Set<String> keySet2 = copy2.keySet();
			for(String hopId : keySet2){
				System.out.println("\tHop " + hopId + ": " + copy2.get(hopId));
			}	
		}
	}

	
	public void run_getSliceStats(String sliceName) throws IOException,
	XmlRpcException {

		Object o = this.client.execute("api.getSliceStats",
				new Object[] { sliceName });
		if (o == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		String reply = null;
		if (o instanceof String)
			reply = (String) o;

		System.out.println("Got reply:");
		System.out.println(reply);
	}

	public void run_getSwitchStats(String dpid) throws IOException,
	XmlRpcException {

		Object o = this.client.execute("api.getSwitchStats",
				new Object[] { dpid });
		if (o == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		String reply = null;
		if (o instanceof String)
			reply = (String) o;

		System.out.println("Got reply:");
		System.out.println(reply);
	}

	public void run_createSlice(String sliceName, String controller_url,
			String slice_email) throws IOException, XmlRpcException {
		String passwd = FVConfig.readPasswd("New password: ");
		Boolean reply = (Boolean) this.client
		.execute("api.createSlice", new Object[] { sliceName, passwd,
				controller_url, slice_email });
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		if (reply)
			System.out.println("success!");
		else
			System.err.println("failed!");
	}

	public void run_ping(String msg) throws XmlRpcException {
		String reply = (String) this.client.execute("api.ping",
				new Object[] { msg });
		if (reply != null) {
			System.out.println("Got reply:");
			System.out.println(reply);
		} else {
			System.err.println("Got 'null' for reply :-(");
		}
	}

	public void run_deleteSlice(String sliceName) throws XmlRpcException {
		Boolean reply = (Boolean) this.client.execute("api.deleteSlice",
				new Object[] { sliceName });
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		if (reply)
			System.out.println("success!");
		else
			System.err.println("failed!");
	}

	public void run_removeFlowSpace(String indexStr) throws XmlRpcException {
		FlowChange change = new FlowChange(FlowChangeOp.REMOVE,
				Integer.valueOf(indexStr));
		List<Map<String, String>> mapList = new LinkedList<Map<String, String>>();
		mapList.add(change.toMap());

		try {
			Object[] reply = (Object[]) this.client.execute(
					"api.changeFlowSpace", new Object[] { mapList });

			if (reply == null) {
				System.err.println("Got 'null' for reply :-(");
				System.exit(-1);
			}
			if (reply.length > 0)
				System.out.println("success: " + (String) reply[0]);
			else
				System.err.println("failed!");
		} catch (XmlRpcException e) {
			System.err.println("Failed: Flow Entry not found");
			System.exit(-1);
		}

	}

	public void run_addFlowSpace(String dpid, String priority, String match,
			String actions) throws XmlRpcException, MalformedFlowChange {
		do_flowSpaceChange(FlowChangeOp.ADD, dpid, null, priority, match,
				actions);
	}

	public void run_changeFlowSpace(String idStr, String dpid, String priority,
			String match, String actions) throws XmlRpcException,
			MalformedFlowChange {
		do_flowSpaceChange(FlowChangeOp.CHANGE, dpid, idStr, priority, match,
				actions);
	}

	private void do_flowSpaceChange(FlowChangeOp op, String dpid, String idStr,
			String priority, String match, String actions)
	throws XmlRpcException {
		if (match.equals("") || match.equals("any") || match.equals("all"))
			match = "OFMatch[]";
		Map<String, String> map = FlowChange.makeMap(op, dpid, idStr, priority,
				match, actions);

		try {
			FlowChange.fromMap(map);
		} catch (MalformedFlowChange e) {
			System.err.println("Local sanity check failed: " + e);
			return;
		}
		List<Map<String, String>> mapList = new LinkedList<Map<String, String>>();
		mapList.add(map);
		Object[] reply = (Object[]) this.client.execute("api.changeFlowSpace",
				new Object[] { mapList });
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		if (reply.length > 0)
			System.out.println("success: " + (String) reply[0]);
		else
			System.err.println("failed!");
	}

	public void run_listSlices() throws XmlRpcException {
		Object[] reply = (Object[]) this.client.execute("api.listSlices",
				new Object[] {});
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		for (int i = 0; i < reply.length; i++) {
			String slice = (String) reply[i];
			System.out.println("Slice " + i + ": " + slice);
		}

	}

	public void run_listFlowSpace() throws XmlRpcException {
		Object[] result2 = (Object[]) client.execute("api.listFlowSpace",
				new Object[] {});
		if (result2 != null) {
			System.out.println("Got reply:");
			int i;
			for (i = 0; i < result2.length; i++)
				System.out.println("rule " + i + ": " + (String) result2[i]);
		} else {
			System.err.println("Got 'null' for reply :-(");
		}
	}

	public void run_registerCallback(String URL, String cookie)
	throws IOException, XmlRpcException, MalformedURLException {
		Boolean reply = (Boolean) this.client.execute(
				"api.registerTopologyChangeCallback", new Object[] { URL,
						cookie });
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		if (reply)
			System.out.println("success!");
		else
			System.err.println("failed!");
	}

	public void run_unregisterCallback() throws IOException, XmlRpcException,
	MalformedURLException {
		Boolean reply = (Boolean) this.client.execute(
				"api.unregisterTopologyChangeCallback", new Object[] {});
		if (reply == null) {
			System.err.println("Got 'null' for reply :-(");
			System.exit(-1);
		}
		if (reply)
			System.out.println("success!");
		else
			System.err.println("failed!");
	}

	private static void usage(String string) {
		usage(string, true);
	}

	private static void usage(String string, boolean printFull) {
		System.err.println(string);
		if (printFull) {
			System.err
			.println("Usage: VeCtl [--debug=true] [--jetty=true] [--user=user] [--url=url] "
					+ "[--passwd-file=filename] command [args...] ");
			for (int i = 0; i < FVCtl.cmdlist.length; i++) {
				APICmd cmd = FVCtl.cmdlist[i];
				System.err.println("\t" + cmd.name + " " + cmd.usage);
			}
		}
		System.exit(-1);
	}

	/**
	 * Front-end cmdline parser for FVCtl
	 *
	 * @param args
	 * @throws SecurityException
	 * @throws IllegalArgumentException
	 * @throws NoSuchMethodException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 * @throws IOException
	 */
	public static void main(String args[]) {
		// FIXME: make URL a parameter
		// FVCtl client = new
		// FVCtl("https://root:joemama@localhost:8080/xmlrpc");
		String URL = "https://localhost:8080/xmlrpc";
		String JETTY_URL = "https://localhost:8081/flowvisor";
		String user = FVConfig.SUPER_USER;
		String passwd = null;
		boolean debug = false;
		boolean jetty = false;

		int cmdIndex = 0;
		// FIXME: find a decent java cmdline args parsing lib
		while ((args.length > cmdIndex) && (args[cmdIndex].startsWith("--"))) {
			String params[] = args[cmdIndex].split("=");
			if (params.length < 2)
				usage("parameter " + params[0] + " needs an argument");
			if (params[0].equals("--url"))
				URL = params[1];
			else if (params[0].equals("--user"))
				user = params[1];
			else if (params[0].equals("--debug"))
				debug = Boolean.valueOf(params[1]);
			else if (params[0].equals("--passwd-file")) {

				try {
                                        if ( params[1].equals("-") ) { // Read from STDIN
					  passwd = new BufferedReader(new InputStreamReader(
                                                        System.in)).readLine();
                                          } else {
					  passwd = new BufferedReader(new FileReader(new File(
							params[1]))).readLine();
                                          }
				} catch (FileNotFoundException e) {
					die(debug, "file: '" + params[1] + "' :: ", e);
				} catch (IOException e) {
					die(debug, "IO: ", e);
				}
			}
			else if (params[0].equals("--jetty")){
				jetty = true;
			}else
				usage("unknown parameter: " + params[0]);
			cmdIndex++;
		}
		if (args.length == cmdIndex)
			usage("need to specify a command");

		APICmd cmd = APICmd.cmdlist.get(args[cmdIndex]);
		if (cmd == null)
			usage("command '" + args[cmdIndex] + "' does not exist");
		if ((args.length - 1 - cmdIndex) < cmd.argCount)
			usage("command '" + args[cmdIndex] + "' takes " + cmd.argCount
					+ " args: only " + (args.length - 1 - cmdIndex)
					+ " given\n" + args[cmdIndex] + " " + cmd.usage, false);
		String[] strippedArgs = new String[args.length - 1 - cmdIndex];
		System.arraycopy(args, cmdIndex + 1, strippedArgs, 0,
				strippedArgs.length);
		try {
			if (passwd == null)
				passwd = FVConfig.readPasswd("Enter " + user + "'s passwd: ");
			FVCtl client = new FVCtl(jetty ? JETTY_URL : URL);

			if (jetty){
				client.runJetty(user, passwd, cmd.name, strippedArgs);
			}
			else{
				client.init(user, passwd);
				cmd.invoke(client, strippedArgs);
			}
		} catch (Exception e) {
			die(debug, "error: ", e);
		}
	}

	private static void die(boolean debug, String string, Exception e) {
		Throwable cause = e;
		while (cause.getCause() != null) {
			if (debug)
				cause.printStackTrace(System.err);
			cause = cause.getCause();
		}
		if (debug)
			cause.printStackTrace(System.err);
		System.err.println(string + cause);
		System.exit(-1);
	}

}
