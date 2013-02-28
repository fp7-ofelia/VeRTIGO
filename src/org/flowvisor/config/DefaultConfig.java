package org.flowvisor.config;

import java.io.FileNotFoundException;

import org.flowvisor.VeRTIGO;
import org.flowvisor.api.APIServer;
import org.flowvisor.api.JettyServer;
import org.flowvisor.exceptions.DuplicateControllerException;
import org.flowvisor.flows.FlowEntry;
import org.flowvisor.flows.FlowMap;
import org.flowvisor.flows.LinearFlowMap;
import org.flowvisor.flows.SliceAction;
import org.flowvisor.log.LogLevel;
import org.openflow.protocol.OFMatch;

/**
 * List of things to populate FVConfig with on startup Everything here can be
 * overridden from the config file
 *
 * @author capveg
 *
 */
public class DefaultConfig {

	static public void init(String rootPasswd) {
		FVConfig.clear(); // remove any pre-existing config
		// setup a bunch of default things in the config
		FlowMap flowMap = new LinearFlowMap();
		SliceAction aliceAction = new SliceAction("alice", SliceAction.WRITE);
		SliceAction bobAction = new SliceAction("bob", SliceAction.WRITE);
		OFMatch match = new OFMatch();
		short alicePorts[] = { 0, 2, 3 };
		String aliceMacs[] = { "00:00:00:00:00:02", "00:01:00:00:00:02" };
		// short alicePorts[] = { 0 };
		// String aliceMacs[] = {"00:00:00:00:00:02"};

		int i, j;
		match.setWildcards(OFMatch.OFPFW_ALL
				& ~(OFMatch.OFPFW_DL_SRC | OFMatch.OFPFW_IN_PORT));

		// add all of alice's rules
		for (i = 0; i < alicePorts.length; i++) {
			match.setInputPort(alicePorts[i]);
			for (j = 0; j < aliceMacs.length; j++) {
				match.setDataLayerSource(aliceMacs[j]);
				flowMap.addRule(new FlowEntry(match.clone(), aliceAction));
			}
		}

		short bobPorts[] = { 1, 3 };
		String bobMacs[] = { "00:00:00:00:00:01", "00:01:00:00:00:01" };
		// short bobPorts[] = { 3};
		// String bobMacs[] = { "00:01:00:00:00:01"};

		// add all of bob's rules
		for (i = 0; i < bobPorts.length; i++) {
			match.setInputPort(bobPorts[i]);
			for (j = 0; j < bobMacs.length; j++) {
				match.setDataLayerSource(bobMacs[j]);
				flowMap.addRule(new FlowEntry(match.clone(), bobAction));
			}
		}
		// now populate the config
		try {
			FVConfig.setInt(FVConfig.LISTEN_PORT, FVConfig.OFP_TCP_PORT);
			FVConfig.setInt(FVConfig.API_WEBSERVER_PORT, APIServer
					.getDefaultPort());
			FVConfig.setInt(FVConfig.API_JETTY_WEBSERVER_PORT, JettyServer.default_jetty_port);
			FVConfig.setString(FVConfig.VERSION_STR,
					VeRTIGO.FLOWVISOR_VERSION);
			FVConfig.setInt(FVConfig.CONFIG_VERSION_STR,
					FVConfig.CONFIG_VERSION);
			// checkpointing on by default
			FVConfig.setBoolean(FVConfig.CHECKPOINTING, true);
			// stats_desc hack on by default
			FVConfig.setBoolean(FVConfig.STATS_DESC_HACK, true);
			// topology server on by default
			FVConfig.setBoolean(FVConfig.TOPOLOGY_SERVER, true);
			// track flows off by default -- experimental feature
			FVConfig.setBoolean(FVConfig.FLOW_TRACKING, false);
			// set logging to NOTE by default
			FVConfig.setString(FVConfig.LOG_THRESH, LogLevel.NOTE.toString());
			
			/**
			 * @authors roberto.doriguzzi matteo.gerola
			 */
			FVConfig.setString(FVConfig.DB_TYPE,"ram");
			FVConfig.setString(FVConfig.DB_IP,"127.0.0.1");
			FVConfig.setInt(FVConfig.DB_PORT, 0);
			FVConfig.setString(FVConfig.DB_USER,"flowvisor");
			FVConfig.setString(FVConfig.DB_PASSWD,"flowvisor");
			
			// create slices

			FVConfig.createSlice(FVConfig.SUPER_USER, "none", 0, rootPasswd,
					"sillysalt", "fvadmin@localhost", FVConfig.SUPER_USER);
			FVConfig.createSlice("alice", "localhost", 54321, "alicePass",
					"sillysalt", "alice@foo.com", FVConfig.SUPER_USER);
			FVConfig.createSlice("bob", "localhost", 54322, "bobPass",
					"sillysalt", "bob@foo.com", FVConfig.SUPER_USER);
			// create switches
			FVConfig.create(FVConfig.SWITCHES, ConfigType.DIR);
			FVConfig.setFlowMap(FVConfig.FLOWSPACE, flowMap);

			// set .switches.default.flood_perm to the super user
			FVConfig.updateVersion_1_to_2();
		} catch (ConfigError e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (InvalidSliceName e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (DuplicateControllerException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Print default config to stdout
	 *
	 * @param args
	 * @throws FileNotFoundException
	 */

	public static void main(String args[]) throws FileNotFoundException {

		if (args.length == 0) {
			System.err.println("Generating default config");
			DefaultConfig.init("CHANGEME");
		} else {
			System.err.println("Reading config from: " + args[0]);
			FVConfig.readFromFile(args[0]);
		}
		FVConfig.walk(new ConfigDumper(System.out));
	}
}
