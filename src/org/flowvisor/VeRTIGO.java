package org.flowvisor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.xmlrpc.webserver.WebServer;
import org.flowvisor.api.APIServer;
import org.flowvisor.api.JettyServer;
import org.flowvisor.config.ConfigError;
import org.flowvisor.config.FVConfig;
import org.flowvisor.events.FVEventHandler;
import org.flowvisor.events.FVEventLoop;
import org.flowvisor.exceptions.UnhandledEvent;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.flowvisor.log.StderrLogger;
import org.flowvisor.log.ThreadLogger;
import org.flowvisor.message.FVMessageFactory;
import org.flowvisor.ofswitch.OFSwitchAcceptor;
import org.flowvisor.ofswitch.TopologyController;
import org.flowvisor.vtopology.topology_configurator.VTConfigInterface;
import org.flowvisor.vtopology.topology_configurator.VTSqlDb;
import org.openflow.example.cli.Option;
import org.openflow.example.cli.Options;
import org.openflow.example.cli.ParseException;
import org.openflow.example.cli.SimpleCLI;

public class VeRTIGO {
	// VENDOR EXTENSION ID
		public final static int FLOWVISOR_VENDOR_EXTENSION = 0x80000001;

		// VERSION
		public final static String FLOWVISOR_VERSION = "flowvisor-0.8.1";
		public final static String VERTIGO_VERSION = "vertigo-0.3.0";

		// Max slicename len ; used in LLDP for now; needs to be 1 byte
		public final static int MAX_SLICENAME_LEN = 255;

		/********/
		String configFile; 
		List<FVEventHandler> handlers;

		private int port;

		private WebServer apiServer;
		static VeRTIGO instance;

		FVMessageFactory factory; 

		private static final Options options = Options.make(new Option[] {
				new Option("d", "debug", LogLevel.NOTE.toString(),
						"Override default logging threshold in config"),
				new Option("l", "logging", "Log to stderr instead of syslog"),
				new Option("p", "port", 0, "Override port from config"),
				new Option("h", "help", "Print help"),

		});

		public VeRTIGO() {
			this.port = 0;
			this.handlers = new ArrayList<FVEventHandler>();
			this.factory = new FVMessageFactory();
		}

		/*
		 * Unregister this event handler from the system
		 */

		/**
		 * @return the configFile
		 */
		public String getConfigFile() {
			return configFile;
		}

		/**
		 * @param configFile
		 *            the configFile to set
		 */
		public void setConfigFile(String configFile) {
			this.configFile = configFile;
		}

		/**
		 * @return the port
		 */
		public int getPort() {
			return port;
		}

		/**
		 * @param port
		 *            the port to set
		 */
		public void setPort(int port) {
			this.port = port;
		}

		/**
		 * @return the factory
		 */
		public FVMessageFactory getFactory() {
			return factory;
		}

		/**
		 * @param factory
		 *            the factory to set
		 */
		public void setFactory(FVMessageFactory factory) {
			this.factory = factory;
		}

		public synchronized boolean unregisterHandler(FVEventHandler handler) {
			if (handlers.contains(handler)) {
				handlers.remove(handler);
				return true;
			}
			return false;
		}

		public void run() throws ConfigError, IOException, UnhandledEvent {
			VeRTIGO.setInstance(this);

					// init polling loop
			FVLog.log(LogLevel.INFO, null, "initializing poll loop");
			FVEventLoop pollLoop = new FVEventLoop();

			if (port == 0)
				port = FVConfig.getInt(FVConfig.LISTEN_PORT);

			// init topology discovery, if configured for it
			if (TopologyController.isConfigured())
				handlers.add(TopologyController.spawn(pollLoop));

			// init switchAcceptor
			OFSwitchAcceptor acceptor = new OFSwitchAcceptor(pollLoop, port, 16);
			handlers.add(acceptor);
			// start XMLRPC UserAPI server; FIXME not async!
			try {
				this.apiServer = APIServer.spawn();
			} catch (Exception e) {
				FVLog.log(LogLevel.FATAL, null, "failed to spawn APIServer");
				e.printStackTrace();
				System.exit(-1);
				
			}

			// print some system state
			boolean flowdb = false;
			try {
				if (FVConfig.getBoolean(FVConfig.FLOW_TRACKING))
					flowdb = true;
			} catch (ConfigError e) {
				// assume off if not set
				FVConfig.setBoolean(FVConfig.FLOW_TRACKING, false);
				this.checkPointConfig();
			}
			if (!flowdb)
				FVLog.log(LogLevel.INFO, null, "flowdb: Disabled");
			
			// start event processing
			pollLoop.doEventLoop();
		}

		/**
		 * FlowVisor Daemon Executable Main
		 *
		 * Takes a config file as only parameter
		 *
		 * @param args
		 *            config file
		 * @throws Throwable
		 */

		public static void main(String args[]) throws Throwable {

			ThreadLogger threadLogger = new ThreadLogger();
			Thread.setDefaultUncaughtExceptionHandler(threadLogger);
			long lastRestart = System.currentTimeMillis();
			while (true) {
				VeRTIGO ve = new VeRTIGO();
				ve.parseArgs(args);

				try {
					// load config from file
					FVConfig.readFromFile(ve.configFile);

					JettyServer.spawnJettyServer();
					ve.run();
				} catch (Throwable e) {
					FVLog.log(LogLevel.CRIT, null, "MAIN THREAD DIED!!!");
					FVLog.log(LogLevel.CRIT, null, "----------------------------");
					threadLogger.uncaughtException(Thread.currentThread(), e);
					FVLog.log(LogLevel.CRIT, null, "----------------------------");
					if ((lastRestart + 5000) > System.currentTimeMillis()) {
						System.err.println("respawning too fast -- DYING");
						FVLog.log(LogLevel.CRIT, null,
								"respawning too fast -- DYING");
						ve.tearDown();
						throw e;
					} else {
						FVLog.log(LogLevel.CRIT, null,
								"restarting after main thread died");
						lastRestart = System.currentTimeMillis();
						ve.tearDown();
					}
					ve = null;
					System.gc(); // give the system a bit to clean up after itself
					Thread.sleep(1000);
				}
			}
		}

		private void parseArgs(String[] args) {
			SimpleCLI cmd = null;
			try {
				cmd = SimpleCLI.parse(options, args);

			} catch (ParseException e) {
				usage("ParseException: " + e.toString());
			}
			if (cmd == null)
				usage("need to specify arguments");
			int i = cmd.getOptind();
			if (i >= args.length)
				usage("need to specify a configuration");
			setConfigFile(args[i]);

			if (cmd.hasOption("d")) {
				FVLog.setThreshold(LogLevel.valueOf(cmd.getOptionValue("d")));
				System.err.println("Set default logging threshold to "
						+ FVLog.getThreshold());
			}
			if (cmd.hasOption("l")) {
				System.err.println("Setting debugging mode: all logs to stderr");
				FVLog.setDefaultLogger(new StderrLogger());
			}
			if (cmd.hasOption("p")) {
				int p = Integer.valueOf(cmd.getOptionValue("p"));
				setPort(p);
				System.err.println("Overriding port from config: setting to "
						+ getPort());
			}

		}

		private void tearDown() {
			if (this.apiServer != null)
				this.apiServer.shutdown(); // shutdown the API Server
			List<FVEventHandler> tmp = this.handlers;
			this.handlers = new LinkedList<FVEventHandler>();
			for (Iterator<FVEventHandler> it = tmp.iterator(); it.hasNext();) {
				FVEventHandler handler = it.next();
				it.remove();
				handler.tearDown();
			}
		}

		/**
		 * Print usage message and warning string then exit
		 *
		 * @param string
		 *            warning
		 */

		private static void usage(String string) {
			System.err.println("VeRTIGO version: " + VERTIGO_VERSION);
			System.err.println("Roberto Doriguzzi: roberto.doriguzzi@create-net.org");
			System.err.println("Matteo Gerola: matteo.gerola@create-net.org");
			System.err.println("");
			System.err.println("FlowVisor version: " + FLOWVISOR_VERSION);
			System.err.println("Rob Sherwood: rsherwood@telekom.com/rob.sherwood@stanford.edu");
			System.err.println("---------------------------------------------------------------");
			System.err.println("err: " + string);
			SimpleCLI.printHelp("vertigo [options] config.xml",
					VeRTIGO.getOptions());
			System.exit(-1);
		}

		private static Options getOptions() {

			return VeRTIGO.options;
		}

		/**
		 * Get the running fv instance
		 *
		 * @return
		 */
		public static VeRTIGO getInstance() {
			return instance;
		}

		/**
		 * Set the running fv instance
		 *
		 * @param instance
		 */
		public static void setInstance(VeRTIGO instance) {
			VeRTIGO.instance = instance;
		}

		/**
		 * Returns a unique, shallow copy of the list of event handlers registered
		 * in the flowvisor
		 *
		 * Is unique to prevent concurrency problems, i.e., when wakling through the
		 * list and a handler gets deleted
		 *
		 * @return
		 */
		public synchronized ArrayList<FVEventHandler> getHandlersCopy() {
			return new ArrayList<FVEventHandler>(handlers);
		}

		public void addHandler(FVEventHandler handler) {
			this.handlers.add(handler);
		}

		public void removeHandler(FVEventHandler handler) {
			this.handlers.remove(handler);
		}

		public void setHandlers(ArrayList<FVEventHandler> handlers) {
			this.handlers = handlers;
		}

		/**
		 * Save the running config back to disk
		 *
		 * Write to a temp file and only if it succeeds, move it into place
		 *
		 * FIXME: add versioning
		 */
		public void checkPointConfig() {
			String tmpFile = this.configFile + ".tmp"; // assumes no one else can
			// write to same dir
			// else security problem

			// do we want checkpointing?
			try {
				if (!FVConfig.getBoolean(FVConfig.CHECKPOINTING))
					return;
			} catch (ConfigError e1) {
				FVLog.log(LogLevel.WARN, null,
						"Checkpointing config not set: assuming you want checkpointing");
			}

			try {
				FVConfig.writeToFile(tmpFile);
			} catch (FileNotFoundException e) {
				FVLog.log(LogLevel.CRIT, null,
						"failed to save config: tried to write to '" + tmpFile
								+ "' but got FileNotFoundException");
				return;
			}
			// sometimes, Java has the stoopidest ways of doing things :-(
			File tmp = new File(tmpFile);
			if (tmp.length() == 0) {
				FVLog.log(LogLevel.CRIT, null,
						"failed to save config: tried to write to '" + tmpFile
								+ "' but wrote empty file");
				return;
			}

			tmp.renameTo(new File(this.configFile));
			FVLog.log(LogLevel.INFO, null, "Saved config to disk at "
					+ this.configFile);
		}

		public String getInstanceName() {
			// TODO pull from FVConfig; needed for slice stiching
			return "magic vertigo1";
		}
}
