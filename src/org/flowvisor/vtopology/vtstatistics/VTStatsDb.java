package org.flowvisor.vtopology.vtstatistics;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import org.flowvisor.config.ConfigError;
import org.flowvisor.config.FVConfig;
import org.flowvisor.vtopology.topology_configurator.VTDBConfig;
import org.flowvisor.vtopology.vtstatistics.VTStatsUtils.*;
import org.openflow.protocol.OFPort;


/**
 * @name VTStatsDb
 * @authors roberto.doriguzzi, matteo.gerola
 * @description Public class used to store traffic statistics for the VT-Planner algorithms  
 */

public class VTStatsDb {
	private final static long DEFAULT_EXPIRATION_TIME = 24*60*60*1000; //24 hours in milliseconds
	private final static long DEFAULT_SAMPLING_TIME = 60*1000; // 60 seconds
	private long expirationTime = DEFAULT_EXPIRATION_TIME; //24 hours in milliseconds
	private long samplingTime = DEFAULT_SAMPLING_TIME;
	private boolean initialized = false;
	private static VTStatsDb instance = null;
	public static VTStatsDb getInstance() {
		if (instance == null) {
			instance = new VTStatsDb();
		}
		return instance;
 	}
	
	// JDBC driver name and database URL
	private String JDBC_DRIVER;
	private String DB_URL;
	//  Database credentials
	private String USER;
	private String PASS;
	// Database connector
	private Connection conn;
	
	private statements statements;
	
	private class statements {
		PreparedStatement insertSwitchTable_switchinfo;	
		PreparedStatement insertSwitchTable_rxtx;
		PreparedStatement selectSwitchTable_rxtx;
		PreparedStatement deleteSwitchTable_rxtx;
		PreparedStatement insertSwitchTable_queues;
		PreparedStatement selectSwitchTable_queues;
		PreparedStatement deleteSwitchTable_queues;
		PreparedStatement insertSwitchTable_portinfo;
		PreparedStatement deleteSwitchTable_switchinfo;
		PreparedStatement deleteSwitchTable_portinfo;
		PreparedStatement selectSwitchTable_switchinfo;
		PreparedStatement selectSwitchTable_switchinfo_all;
		PreparedStatement selectSwitchTable_portinfo;
		PreparedStatement selectSwitchTable_portinfo_all;
	}
	
	private VTStatsDb() {
		statements = new statements();
	}

/**
 * @name sqlDbInit()
 * @authors roberto.doriguzzi, matteo.gerola
 * @description This function provide basic database functionalities: 
 * connect, check db existence, table creation, user authentication 
 * @exception ClassNotFoundException
 * @exception SQLException
 */	
	public synchronized void sqlDbInit() 
	throws ClassNotFoundException, SQLException {
		if(initialized == true) return;
		// Load config informations
		VTDBConfig dbConfig = new VTDBConfig();
		dbConfig.GetDBConfig();
		
		if (dbConfig.dbType.equals("mysql")) {
			this.JDBC_DRIVER = "com.mysql.jdbc.Driver";
			this.DB_URL = "jdbc:mysql://"+ dbConfig.dbAddress + ":" + dbConfig.dbPort +"/";
		}
		else if (dbConfig.dbType.equals("ram")) {
			this.JDBC_DRIVER = "org.hsqldb.jdbcDriver";
			this.DB_URL = "jdbc:hsqldb:mem:";
		}
		this.USER = dbConfig.dbUser;
		this.PASS = dbConfig.dbPasswd;
		
		// Connect to the Database
		this.sqlDbConnect();
		
		// DB initialization and tables creation
		this.sqlDbInitializeDb();
		
		try {
			String timer, expiration;
			timer = FVConfig.getString(FVConfig.VTPLANNER_STATS_TIMER);
			expiration = FVConfig.getString(FVConfig.VTPLANNER_STATS_EXPIR);
			if(timer.length() > 0) {
				try{
					if(timer.endsWith("s")) sqlDbSetTimer(Long.parseLong(timer.replace("s",""))*1000);
					else if(timer.endsWith("m")) sqlDbSetTimer(Long.parseLong(timer.replace("m",""))*60*1000);
					else if(timer.endsWith("h")) sqlDbSetTimer(Long.parseLong(timer.replace("h",""))*3600*1000);
					else if(timer.endsWith("d")) sqlDbSetTimer(Long.parseLong(timer.replace("d",""))*86400*1000);
					else if(timer.endsWith("w")) sqlDbSetTimer(Long.parseLong(timer.replace("w",""))*604800*1000);
					else sqlDbSetTimer(Long.parseLong(timer)*1000);
				} catch (NumberFormatException e) {
					sqlDbSetTimer(DEFAULT_SAMPLING_TIME);
				}
			}
			if(expiration.length() > 0) {
				try{	
					if(expiration.endsWith("s")) sqlDbSetExpirationTime(Long.parseLong(expiration.replace("s",""))*1000);
					else if(expiration.endsWith("m")) sqlDbSetExpirationTime(Long.parseLong(expiration.replace("m",""))*60*1000);
					else if(expiration.endsWith("h")) sqlDbSetExpirationTime(Long.parseLong(expiration.replace("h",""))*3600*1000);
					else if(expiration.endsWith("d")) sqlDbSetExpirationTime(Long.parseLong(expiration.replace("d",""))*86400*1000);
					else if(expiration.endsWith("w")) sqlDbSetExpirationTime(Long.parseLong(expiration.replace("w",""))*604800*1000);
					else sqlDbSetExpirationTime(Long.parseLong(expiration)*1000);
				} catch (NumberFormatException e) {
					sqlDbSetExpirationTime(DEFAULT_EXPIRATION_TIME);
				}
			}
		} catch (ConfigError e1) {
			sqlDbSetExpirationTime(DEFAULT_EXPIRATION_TIME);
			sqlDbSetTimer(DEFAULT_SAMPLING_TIME);
		}
		
		initialized = true;
	}

/**
 * @name sqlDbConnect()
 * @authors roberto.doriguzzi, matteo.gerola
 * @description This function check if the database exists, creates it if not and sets up the connection
 * @exception ClassNotFoundException
 * @exception SQLException
 */	
	public void sqlDbConnect() 
	throws ClassNotFoundException, SQLException {
		Class.forName(this.JDBC_DRIVER);
		String DB_NAME= "vt_statistics";
		Statement stmt = null;
		
		// Check if database exists, if exception caught, create it. Set commit to manual
		try {
			
			String DB = this.DB_URL + DB_NAME;
			conn = DriverManager.getConnection(DB, this.USER, this.PASS);
			conn.setAutoCommit(false);
			//conn.commit();
			this.DB_URL = this.DB_URL + DB_NAME;
		}
		catch (SQLException e) {
			conn = DriverManager.getConnection(this.DB_URL, this.USER, this.PASS);
			stmt = conn.createStatement();
		    String sql = "CREATE DATABASE IF NOT EXISTS " + DB_NAME;
		    stmt.execute(sql);
		    
		    sql = "GRANT ALL ON " + DB_NAME + ".* TO '" + this.USER + "'@'%';";
		    stmt.executeUpdate(sql);
		    if(stmt!=null)
		    	stmt.close();
		    conn.close();
		    
		    this.DB_URL = this.DB_URL + DB_NAME;
		    conn = DriverManager.getConnection(this.DB_URL, this.USER, this.PASS);
			conn.setAutoCommit(false);
			conn.commit();
		}
		
	}
	
/**
 * @name sqlDbDisconnect()
 * @authors roberto.doriguzzi, matteo.gerola
 * @description This function close the connection with the database 
 * @exception SQLException
 */	
	public void sqlDbDisconnect() 
	throws SQLException {
		if(conn!=null)
            conn.close();
	}
	
/**
 * @name sqlDbSetExpirationTime()
 * @authors roberto.doriguzzi, matteo.gerola
 * @description Sets the expiration time 
 */	
	public void sqlDbSetExpirationTime(long sp) {
		expirationTime = sp; 
	}
	
/**
 * @name sqlDbGetExpirationTime()
 * @authors roberto.doriguzzi, matteo.gerola
 * @description Returns the expiration time 
 */	
	public long sqlDbGetExpirationTime() {
		return expirationTime; 
	}
	
/**
 * @name sqlDbSetTimer()
 * @authors roberto.doriguzzi, matteo.gerola
 * @description Sets the insert timer 
 */	
	public void sqlDbSetTimer(long st) {
		samplingTime = st; 
	}

/**
 * @name sqlDbGetTimer()
 * @authors roberto.doriguzzi, matteo.gerola
 * @description Gets the insert timer 
 */	
	public long sqlDbGetTimer() {
		return samplingTime; 
	}
	
/**
 * @name sqlDbInitializeDb()
 * @authors roberto.doriguzzi, matteo.gerola
 * @description This function create the DB tables and indexes
 * @exception SQLException
 */
	public void sqlDbInitializeDb() 
	throws SQLException {
		Statement stmt = null;
		stmt = conn.createStatement();	
		
		stmt.execute ("DROP TABLE IF EXISTS switchTable_switchinfo");
	    stmt.execute ("CREATE TABLE switchTable_switchinfo (" +
	    		"switchId BIGINT NOT NULL," +
	    		"Manufacturer VARCHAR(100) NOT NULL," +
	    		"serialNumber VARCHAR(50) NOT NULL," +
	    		"datapathDescription VARCHAR(100) NOT NULL," +
	    		"availablePorts VARCHAR(1000) NOT NULL," +
	    		"capabilities INT NOT NULL," +
	    		"ofpVersion INT NOT NULL," +
	    		"PRIMARY KEY (switchId))");
	    stmt.execute ("CREATE INDEX infoIndex1 ON switchTable_switchinfo (switchId, Manufacturer, serialNumber, datapathDescription, availablePorts, capabilities, ofpVersion)");
		
	    stmt.execute ("DROP TABLE IF EXISTS switchTable_rxtx");
	    stmt.execute ("CREATE TABLE switchTable_rxtx (" +
	    		"switchId BIGINT NOT NULL," +
	    		"phyPortId INT NOT NULL," +
	    		"txBytes BIGINT NOT NULL," +
	    		"rxBytes BIGINT NOT NULL," +
	    		"txPackets BIGINT NOT NULL," +
	    		"rxPackets BIGINT NOT NULL," +
	    		"ts TIMESTAMP," +
	    		"PRIMARY KEY (switchId,phyPortId,ts))");
	    stmt.execute ("CREATE INDEX rxtxIndex1 ON switchTable_rxtx (switchId, phyPortId, txBytes, rxBytes)");
	    stmt.execute ("CREATE INDEX rxtxIndex2 ON switchTable_rxtx (switchId, phyPortId, txPackets, rxPackets)");
	    
	    stmt.execute ("DROP TABLE IF EXISTS switchTable_queues");
	    stmt.execute ("CREATE TABLE switchTable_queues (" +
	    		"switchId BIGINT NOT NULL," +
	    		"phyPortId INT NOT NULL," +
	    		"queueId INT NOT NULL," +
	    		"txBytes BIGINT NOT NULL," +
	    		"txPackets BIGINT NOT NULL," +
	    		"txErrors BIGINT NOT NULL," +
	    		"ts TIMESTAMP," +
	    		"PRIMARY KEY (switchId,phyPortId,ts))");
	    stmt.execute ("CREATE INDEX queuesIndex1 ON switchTable_queues (switchId, phyPortId, txBytes, txPackets, txErrors)");
	    
	    stmt.execute ("DROP TABLE IF EXISTS switchTable_portinfo");
	    stmt.execute ("CREATE TABLE switchTable_portinfo (" +
	    		"switchId BIGINT NOT NULL," +
	    		"phyPortId INT NOT NULL," +
	    		"portConfig INT NOT NULL," + 
	    		"portFeatures INT NOT NULL," + 
	    		"portState INT NOT NULL," + 
	    		"PRIMARY KEY (switchId,phyPortId))");
	    stmt.execute ("CREATE INDEX portinfoIndex1 ON switchTable_portinfo (switchId, phyPortId, portConfig, portFeatures, portState)");
	
	    //Variables: switchId, manufacturer, serialNumber, datapath, availablePorts, capabilities, ofpVersion
	    statements.insertSwitchTable_switchinfo = conn.prepareStatement("INSERT INTO switchTable_switchinfo " +
	    		"VALUES (?,?,?,?,?,?,?)");
	    //Variables: switchId, phyPortId, txBytes, rxBytes, txPackets, rxPackets, ts
	    statements.insertSwitchTable_rxtx = conn.prepareStatement("INSERT INTO switchTable_rxtx " +
	    		"VALUES (?,?,?,?,?,?,?)");
	    //Variables: switchId, phyPortId, queueId, txBytes, txPackets, txErrors,ts
	    statements.insertSwitchTable_queues = conn.prepareStatement("INSERT INTO switchTable_queues " +
	    		"VALUES (?,?,?,?,?,?,?)");
	    //Variables: switchId, phyPortId, portFeatures, portState,
	    statements.insertSwitchTable_portinfo = conn.prepareStatement("INSERT INTO switchTable_portinfo " +
	    		"VALUES (?,?,?,?,?)");
	    
	    //Variables: switchId
	  	statements.deleteSwitchTable_switchinfo = conn.prepareStatement("DELETE FROM switchTable_switchinfo " +
	  			"WHERE switchId = ?");	  
	  	
	  	//Variables: switchId, phyPortId
	  	statements.deleteSwitchTable_portinfo = conn.prepareStatement("DELETE FROM switchTable_portinfo " +
	  			"WHERE switchId = ? and phyPortId = ?");
	  	
	    //Variables: switchId
	  	statements.selectSwitchTable_switchinfo = conn.prepareStatement("SELECT switchId, Manufacturer, serialNumber, datapathDescription, availablePorts, capabilities, ofpVersion FROM switchTable_switchinfo " +
				"WHERE switchId = ? LIMIT 1");
	  	
	  	//Variables: 
	  	statements.selectSwitchTable_switchinfo_all = conn.prepareStatement("SELECT switchId, Manufacturer, serialNumber, datapathDescription, availablePorts, capabilities, ofpVersion FROM switchTable_switchinfo " +
				"ORDER by switchId");
	  	
	  	//Variables: switchId, phyPortId
	  	statements.selectSwitchTable_portinfo = conn.prepareStatement("SELECT phyPortId, portConfig, portFeatures, portState FROM switchTable_portinfo " +
				"WHERE switchId = ? and phyPortId = ? LIMIT 1");
	  	
	  	//Variables: switchId
	  	statements.selectSwitchTable_portinfo_all = conn.prepareStatement("SELECT phyPortId, portConfig, portFeatures, portState FROM switchTable_portinfo " +
				"WHERE switchId = ? ORDER by phyPortId");
	  	
	  	//Variables: switchId,phyPortId,time1,time2
	  	statements.selectSwitchTable_rxtx = conn.prepareStatement("SELECT txBytes, rxBytes, txPackets, rxPackets, ts FROM switchTable_rxtx " +
				"WHERE switchId = ?  and phyPortId = ? and ts >= ? and ts <= ? ORDER by ts");
	  	
	  	//Variables: switchId,phyPortId,queueId,time1,time2
	  	statements.selectSwitchTable_queues = conn.prepareStatement("SELECT txBytes, txPackets, txErrors, ts FROM switchTable_queues " +
				"WHERE switchId = ?  and phyPortId = ? and queueId = ? and ts >= ? and ts <= ? ORDER by ts");
	  	
	  	//Variables: switchId,phyPortId,ts
	    statements.deleteSwitchTable_rxtx = conn.prepareStatement("DELETE FROM switchTable_rxtx " +
				"WHERE switchId = ?  and phyPortId = ? and ts <= ?");
	    
	    //Variables: switchId,phyPortId,ts
	    statements.deleteSwitchTable_queues = conn.prepareStatement("DELETE FROM switchTable_queues " +
				"WHERE switchId = ?  and phyPortId = ? and ts <= ?");
	    
	    
	    conn.commit();
	    if(stmt!=null)
	        stmt.close();
	}
	
	/**
	 * @name sqlDbInsertSwitchInfo(long switchId, String Manufacturer, String serialNumber, String datapathDescription, String ports) 
	 * @authors roberto.doriguzzi, matteo.gerola
	 * @info Inserts switch info to the DB
	 * @param long switchId = datapath_ID of the switch
	 * @param String Manufacturer = switch Manufacturer
	 * @param String serialNumber = switch serial number
	 * @param String datapathDescription = description of the datapath (IP and port)
	 * @param String available_ports = list of available physical ports
	 * @param int capabilities = capabilities of the switch
	 * @param int ofpVersion = version of the OpenFlow protocol supported by the switch
	 * @return void
	 * @exception SQLException
	 * @exception RuntimeException
	 * @exception ConfigError
	 */
		public void sqlDbInsertSwitchInfo(long switchId, String Manufacturer, String serialNumber, String datapathDescription, String ports, int capabilities, int ofpVersion) 
		throws SQLException {
			
			statements.deleteSwitchTable_switchinfo.setLong(1, switchId);
			statements.deleteSwitchTable_switchinfo.executeUpdate();
			
			statements.insertSwitchTable_switchinfo.setLong(1, switchId);
			statements.insertSwitchTable_switchinfo.setString(2, Manufacturer);
			statements.insertSwitchTable_switchinfo.setString(3, serialNumber);
			statements.insertSwitchTable_switchinfo.setString(4, datapathDescription);
			statements.insertSwitchTable_switchinfo.setString(5,ports);
			statements.insertSwitchTable_switchinfo.setInt(6,capabilities);
			statements.insertSwitchTable_switchinfo.setInt(7,ofpVersion);
			statements.insertSwitchTable_switchinfo.executeUpdate();
			
			conn.commit();
		}
		
	/**
	 * @name sqlDbGetSwitchInfo(long switchId, SwitchInfo switchInfo)
	 * @authors roberto.doriguzzi, matteo.gerola
	 * @info Retrieves switch info to the DB
	 * @param long switchId = datapath_ID of the switch
	 * @param SwitchInfo switchInfo = info container
	 * @return true in case of success, false if ResultSet is empty
	 * @exception SQLException
	 * @exception RuntimeException
	 * @exception ConfigError
	 */
		public boolean sqlDbGetSwitchInfo(long switchId, SwitchInfo sInfo) 
		throws SQLException {
			ResultSet rs = null;
			String tmp_ports = "";
			StringTokenizer st = null;
			
			if(switchId == 0xFFFFFFFFFFFFFFFFl) {
				rs = statements.selectSwitchTable_switchinfo_all.executeQuery();
			}
			else {
				statements.selectSwitchTable_switchinfo.setLong(1, switchId);
				rs = statements.selectSwitchTable_switchinfo.executeQuery();
			}
			
			sInfo.counter = 0;
			while (rs.next ()){
				sInfo.switchId.add(rs.getLong("switchId"));
				sInfo.Manufacturer.add(rs.getString("Manufacturer"));
				sInfo.serialNumber.add(rs.getString("serialNumber"));
				sInfo.datapathDescription.add(rs.getString("datapathDescription"));
				sInfo.capabilities.add(rs.getInt("capabilities"));
				sInfo.ofpVersion.add(rs.getInt("ofpVersion"));
				tmp_ports = rs.getString("availablePorts");
			
				st = new StringTokenizer(tmp_ports, ",");
				List<Integer> tmp_portlist = new LinkedList<Integer>();

				while(st.hasMoreTokens())
					tmp_portlist.add(Integer.decode(st.nextToken()));
					
				sInfo.available_ports.add(tmp_portlist);
				sInfo.counter++;
			}
			
			rs.close ();
			return true;
		}
		
		/**
		 * @name sqlDbInsertPortInfo(long switchId, short phyPortId, short portRate, short portState, short portMedium) 
		 * @authors roberto.doriguzzi, matteo.gerola
		 * @info Inserts port info to the DB
		 * @param long switchId = datapath_ID of the switch
		 * @param short phyPortId = physical port number
		 * @param int portConfig = STP and administrative settings
		 * @param int portFeatures = includes 1) rate of the port and 2) port medium (fiber or copper) (see OF 1.0.0 specs for more info)
		 * @param int portState = STP port state
		 * @return void
		 * @exception SQLException
		 * @exception RuntimeException
		 * @exception ConfigError
		 */
			public void sqlDbInsertPortInfo(long switchId, short phyPortId, int portConfig, int portFeatures, int portState) 
			throws SQLException {
				statements.deleteSwitchTable_portinfo.setLong(1, switchId);
				statements.deleteSwitchTable_portinfo.setInt(2, phyPortId);
				statements.deleteSwitchTable_portinfo.executeUpdate();
				
				statements.insertSwitchTable_portinfo.setLong(1, switchId);
				statements.insertSwitchTable_portinfo.setInt(2, phyPortId);
				statements.insertSwitchTable_portinfo.setInt(3, portConfig);
				statements.insertSwitchTable_portinfo.setInt(4, portFeatures);
				statements.insertSwitchTable_portinfo.setInt(5, portState);
				statements.insertSwitchTable_portinfo.executeUpdate();
				
				conn.commit();
			}
		/**
		 * @name sqlDbDeletePortInfo(long switchId, short phyPortId) 
		 * @authors roberto.doriguzzi, matteo.gerola
		 * @info Deletes port info to the DB
		 * @param long switchId = datapath_ID of the switch
		 * @return void
		 * @exception SQLException
		 * @exception RuntimeException
		 * @exception ConfigError
		 */
			public void sqlDbDeletePortInfo(long switchId, short phyPortId) 
			throws SQLException {
				statements.deleteSwitchTable_portinfo.setLong(1, switchId);
				statements.deleteSwitchTable_portinfo.setInt(2, phyPortId);
				statements.deleteSwitchTable_portinfo.executeUpdate();			
				conn.commit();
			}
		/**
		 * @name sqlDbGetPortInfo(long switchId, SwitchInfo switchInfo)
		 * @authors roberto.doriguzzi, matteo.gerola
		 * @info Gets port info from the DB
		 * @param long switchId = datapath_ID of the switch
		 * @param short phyPortId = physical port number
		 * @param PortInfo pInfo = a container for port information
		 * @return true in case of success, false if ResultSet is empty
		 * @exception SQLException
		 * @exception RuntimeException
		 * @exception ConfigError
		 */
			public boolean sqlDbGetPortInfo(long switchId, short phyPortId, PortInfo pInfo)
			throws SQLException {
				boolean ret = false;
				ResultSet rs = null;
				
				if(phyPortId == OFPort.OFPP_ALL.getValue()) {
					statements.selectSwitchTable_portinfo_all.setLong(1, switchId);
					rs = statements.selectSwitchTable_portinfo_all.executeQuery();
				}
				else {
					statements.selectSwitchTable_portinfo.setLong(1, switchId);
					statements.selectSwitchTable_portinfo.setInt(2, phyPortId);
					rs = statements.selectSwitchTable_portinfo.executeQuery();
				}
				
				pInfo.counter = 0;
				pInfo.switchId = switchId;
				while (rs.next ()){
					pInfo.phyPortId.add(rs.getShort("phyPortId"));
					pInfo.portConfig.add(rs.getInt("portConfig"));
					pInfo.portFeatures.add(rs.getInt("portFeatures"));
					pInfo.portState.add(rs.getInt("portState"));
					pInfo.counter++;
					ret = true;
				}
								
				rs.close ();
				return ret;
			}
		
		/**
		 * @name sqlDbInsertPortStats(long switchId, int phyPortId, long txBytes, long rxBytes, long txPackets, long rxPackets) 
		 * @authors roberto.doriguzzi, matteo.gerola
		 * @info Inserts switch statistics to the DB regarding rx/tx bytes and packets
		 * @param long switchId = datapath_ID of the switch
		 * @param int phyPortId = port number
		 * @param long txBytes/rxBytes = transmitted/received bytes
		 * @param long txPackets/rxPackets = transmitted/received packets
		 * @return void
		 * @exception SQLException
		 * @exception RuntimeException
		 * @exception ConfigError
		 */
			public void sqlDbInsertPortStats(long switchId, short phyPortId, long txBytes, long rxBytes, long txPackets, long rxPackets) 
			throws SQLException {
				java.sql.Timestamp  sqlDate = new java.sql.Timestamp(new java.util.Date().getTime());
				statements.insertSwitchTable_rxtx.setLong(1, switchId);
				statements.insertSwitchTable_rxtx.setInt(2, phyPortId);
				statements.insertSwitchTable_rxtx.setLong(3, txBytes);
				statements.insertSwitchTable_rxtx.setLong(4, rxBytes);
				statements.insertSwitchTable_rxtx.setLong(5, txPackets);
				statements.insertSwitchTable_rxtx.setLong(6, rxPackets);
				statements.insertSwitchTable_rxtx.setTimestamp(7, sqlDate);
				statements.insertSwitchTable_rxtx.executeUpdate();
				
				// deleting old records
				statements.deleteSwitchTable_rxtx.setLong(1, switchId);
				statements.deleteSwitchTable_rxtx.setInt(2, phyPortId);
				sqlDate.setTime(sqlDate.getTime() - this.sqlDbGetExpirationTime());
				statements.deleteSwitchTable_rxtx.setTimestamp(3, sqlDate);
				statements.deleteSwitchTable_rxtx.executeUpdate();

				
				conn.commit();
			}
			
		/**
		 * @name sqlDbGetPortStats(long switchId, short phyPortId, long time1, long time2, PortStats pStats)
		 * @authors roberto.doriguzzi, matteo.gerola
		 * @info Retrieves port statistics recorded between time1 and time2
		 * @param long switchId = datapath_ID of the switch
		 * @param short phyPortId = the port number
		 * @param long time1
		 * @param long time2
		 * @return true in case of success, false if ResultSet is empty
		 * @exception SQLException
		 * @exception RuntimeException
		 * @exception ConfigError
		 */
			public boolean sqlDbGetPortStats(long switchId, short phyPortId, long time1, long time2, PortStats pStats) 
			throws SQLException {
				ResultSet rs = null;
				
				statements.selectSwitchTable_rxtx.setLong(1, switchId);
				statements.selectSwitchTable_rxtx.setInt(2, phyPortId);
				java.sql.Timestamp  sqlDate1 = new java.sql.Timestamp(time1);
				statements.selectSwitchTable_rxtx.setTimestamp(3, sqlDate1);
				java.sql.Timestamp  sqlDate2 = new java.sql.Timestamp(time2);
				statements.selectSwitchTable_rxtx.setTimestamp(4, sqlDate2);
				rs = statements.selectSwitchTable_rxtx.executeQuery();

				pStats.counter = 0;
				pStats.switchId = switchId;
				pStats.portId = phyPortId;
				while (rs.next ()){
					pStats.txBytes.add(Long.valueOf(rs.getLong("txBytes")));
					pStats.rxBytes.add(Long.valueOf(rs.getLong("rxBytes")));
					pStats.txPackets.add(Long.valueOf(rs.getLong("txPackets")));
					pStats.rxPackets.add(Long.valueOf(rs.getLong("rxPackets")));
					java.sql.Timestamp  sqlDate = rs.getTimestamp("ts");
					pStats.timeStamp.add(sqlDate.getTime());
					pStats.counter++;
				}
				rs.close ();
				
				if(pStats.counter == 0) return false; 
				else return true;
			}
			
		/**
		 * @name sqlDbInsertQueueStats(long switchId, short phyPortId, int queueID, long txBytes, long txPackets, long txErrors)  
		 * @authors roberto.doriguzzi, matteo.gerola
		 * @info Inserts queue statistics to the DB 
		 * @param long switchId = datapath_ID of the switch
		 * @param int phyPortId = port number
		 * @param int queueID = identifier of the queue
		 * @param long txBytes = transmitted bytes
		 * @param long txPackets = transmitted packets
		 * @param long txErrors = transmitted errors
		 * @return void
		 * @exception SQLException
		 * @exception RuntimeException
		 * @exception ConfigError
		 */
			public void sqlDbInsertQueueStats(long switchId, short phyPortId, int queueID, long txBytes, long txPackets, long txErrors) 
			throws SQLException {
				java.sql.Timestamp  sqlDate = new java.sql.Timestamp(new java.util.Date().getTime());
				
				statements.insertSwitchTable_queues.setLong(1, switchId);
				statements.insertSwitchTable_queues.setInt(2, phyPortId);
				statements.insertSwitchTable_queues.setInt(3, queueID);
				statements.insertSwitchTable_queues.setLong(4, txBytes);
				statements.insertSwitchTable_queues.setLong(5, txPackets);
				statements.insertSwitchTable_queues.setLong(6, txErrors);
				statements.insertSwitchTable_queues.setTimestamp(7, sqlDate);
				statements.insertSwitchTable_queues.executeUpdate();
				
				// deleting old records
				statements.deleteSwitchTable_queues.setLong(1, switchId);
				statements.deleteSwitchTable_queues.setInt(2, phyPortId);
				sqlDate.setTime(sqlDate.getTime() - this.sqlDbGetExpirationTime());
				statements.deleteSwitchTable_queues.setTimestamp(3, sqlDate);
				statements.deleteSwitchTable_queues.executeUpdate();

				
				conn.commit();
			}
			
		/**
		 * @name sqlDbGetQueueStats(long switchId, short phyPortId, int queueId, long time1, long time2, QueueStats qStats)
		 * @authors roberto.doriguzzi, matteo.gerola
		 * @info Retrieves queue statistics recorded between time1 and time2
		 * @param long switchId = datapath_ID of the switch
		 * @param short phyPortId = the port number
		 * @param int queueId = the queue identifier
		 * @param long time1
		 * @param long time2
		 * @return true in case of success, false if ResultSet is empty
		 * @exception SQLException
		 * @exception RuntimeException
		 * @exception ConfigError
		 */
			public boolean sqlDbGetQueueStats(long switchId, short phyPortId, int queueId, long time1, long time2, QueueStats qStats) 
			throws SQLException {
				ResultSet rs = null;
				
				statements.selectSwitchTable_queues.setLong(1, switchId);
				statements.selectSwitchTable_queues.setInt(2, phyPortId);
				statements.selectSwitchTable_queues.setInt(3, queueId);
				java.sql.Timestamp  sqlDate1 = new java.sql.Timestamp(time1);
				statements.selectSwitchTable_queues.setTimestamp(4, sqlDate1);
				java.sql.Timestamp  sqlDate2 = new java.sql.Timestamp(time2);
				statements.selectSwitchTable_queues.setTimestamp(5, sqlDate2);
				rs = statements.selectSwitchTable_queues.executeQuery();

				qStats.counter = 0;
				qStats.switchId = switchId;
				qStats.portId = phyPortId;
				while (rs.next ()){
					qStats.txBytes.add(Long.valueOf(rs.getLong("txBytes")));
					qStats.txPackets.add(Long.valueOf(rs.getLong("txPackets")));
					qStats.txErrors.add(Long.valueOf(rs.getLong("txErrors")));
					java.sql.Timestamp  sqlDate = rs.getTimestamp("ts");
					qStats.timeStamp.add(sqlDate.getTime());
					qStats.counter++;
				}
				rs.close ();
				
				if(qStats.counter == 0) return false; 
				else return true;
			}
}