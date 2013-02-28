/**
 * 
 */
package org.flowvisor.vtopology.topology_configurator;

import java.sql.*;

import org.flowvisor.VeRTIGO;
import org.flowvisor.config.ConfigError;
import org.flowvisor.events.FVEventHandler;
import org.flowvisor.events.VTEvent;
import org.flowvisor.events.VTLLDPEvent;
import org.flowvisor.exceptions.UnhandledEvent;
import org.flowvisor.flows.FlowSpaceUtil;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;



/**
 * @name VTSqlDb
 * @authors roberto.doriguzzi, matteo.gerola
 * @description Public class used to connect to a MySQL database, and perform all necessaries actions 
 */
public class VTSqlDb {
	
	private static VTSqlDb instance = null;
	public static VTSqlDb getInstance() {
		if (instance == null) {
			instance = new VTSqlDb();
		}
		return instance;
 	}
	
	private class nextHopClass {
		Long switchId;
		Integer inputPort;
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

		PreparedStatement selectSwitchTableOne;
		PreparedStatement selectSwitchTableTwo;
		PreparedStatement selectSwitchTableFive;
		PreparedStatement selectSwitchTableFour;
		PreparedStatement selectSwitchTableSix;
		PreparedStatement selectSwitchTableSeven;
		PreparedStatement selectSwitchTableThree;
		PreparedStatement selectSwitchTableEight;
		PreparedStatement selectSwitchTableNine;
		PreparedStatement selectSwitchTableTen;
		PreparedStatement selectSwitchTableEleven;
		PreparedStatement selectSwitchTableTwelve;
		PreparedStatement selectSwitchTableThirteen;
		PreparedStatement selectSwitchTableFourteen;
		PreparedStatement selectSwitchTableFifthteen;
		PreparedStatement selectSwitchTableSixteen;
		PreparedStatement selectSwitchTableSeventeen;
		PreparedStatement selectSwitchTableEightteen;
		PreparedStatement selectSwitchTableNineteen;
		PreparedStatement selectSwitchTableTwenty;
		PreparedStatement deleteSwitchTableOne;
		PreparedStatement deleteSwitchTableTwo;
		PreparedStatement updateSwitchTable;
		PreparedStatement insertSwitchTable;
		
		PreparedStatement selectBufferTable;
		PreparedStatement deleteBufferTableOne;
		PreparedStatement deleteBufferTableTwo;
		PreparedStatement insertBufferTable;
		
		PreparedStatement selectLinkTableOne;
		PreparedStatement selectLinkTableTwo;
		PreparedStatement selectLinkTableThree;
		PreparedStatement selectLinkTableFour;
		PreparedStatement deleteLinkTableOne;
		PreparedStatement deleteLinkTableTwo;
		PreparedStatement insertLinkTable;
		
		PreparedStatement selectLeaseTableOne;
		PreparedStatement selectLeaseTableTwo;
		PreparedStatement insertLeaseTable;
	}
	
	private VTSqlDb() {
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
	public void sqlDbInit() 
	throws ClassNotFoundException, SQLException {
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
		
		// Link table filling
		try {
			this.sqlLinkTableFill();
		} catch (RuntimeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ConfigError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
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
		String DB_NAME= "flowvisor";
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
		    String sql = "CREATE DATABASE IF NOT EXISTS flowvisor";
		    stmt.execute(sql);
		    
		    sql = "GRANT ALL ON flowvisor.* TO '" + this.USER + "'@'%';";
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
 * @name sqlDbInitializeDb()
 * @authors roberto.doriguzzi, matteo.gerola
 * @description This function create the DB tables and indexes
 * @exception SQLException
 */
	public void sqlDbInitializeDb() 
	throws SQLException {
		Statement stmt = null;
		stmt = conn.createStatement();
		
	    // Create switch info table
	    stmt.execute ("DROP TABLE IF EXISTS switchTable");
	    stmt.execute ("CREATE TABLE switchTable (" +
	    		"sliceId VARCHAR(40) NOT NULL," +
	    		"switchId BIGINT NOT NULL," +
	    		"phyPortId INT NOT NULL," +
	    		"virtPortId INT NOT NULL," +
	    		"accessPort BOOLEAN NOT NULL," +
	    		"linkId INT NOT NULL," +
	    		"endPoint BOOLEAN NOT NULL," +
	    		"status BOOLEAN NOT NULL," +
	    		"PRIMARY KEY (sliceId,switchId,phyPortId,virtPortId,linkId))");
	    stmt.execute ("CREATE INDEX switchIndex ON switchTable (sliceId, switchId, linkId)");
	    stmt.execute ("CREATE INDEX switchIndex2 ON switchTable (sliceId, switchId, endPoint, virtPortId)");
	    stmt.execute ("CREATE INDEX switchIndex3 ON switchTable (sliceId, switchId, linkId, endPoint)");
	    stmt.execute ("CREATE INDEX switchIndex4 ON switchTable (sliceId, switchId)");
	    stmt.execute ("CREATE INDEX switchIndex5 ON switchTable (sliceId, switchId, phyPortId)");
	    stmt.execute ("CREATE INDEX switchIndex6 ON switchTable (sliceId, switchId, linkId, phyPortId)");
	    stmt.execute ("CREATE INDEX switchIndex7 ON switchTable (linkId, phyPortId)");
	    
	    // Create virtual port history table
	    stmt.execute ("CREATE TABLE IF NOT EXISTS leaseTable (" +
	    		"sliceId VARCHAR(40) NOT NULL," +
	    		"linkName VARCHAR(400) NOT NULL," +
	    		"linkId INT NOT NULL," +
	    		"switchId BIGINT NOT NULL," +
	    		"phyPortId INT NOT NULL," +
	    		"virtPortId INT NOT NULL," +
	    		//"ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
	    		"PRIMARY KEY (sliceId,linkName,linkId,switchId,phyPortId,virtPortId))");
	    
	    // Create flowMatch to buffer association
	    stmt.execute ("DROP TABLE IF EXISTS bufferTable");
	    stmt.execute ("CREATE TABLE bufferTable (" +
	    		"flowMatch VARCHAR(200) NOT NULL," +
	    		"bufferId INT NOT NULL," +
	    		"sliceId VARCHAR(40) NOT NULL," +
	    		"switchId BIGINT NOT NULL," +
	    		"PRIMARY KEY (bufferId, sliceId, switchId))");
	    //stmt.execute ("CREATE INDEX bufferIndex ON bufferTable (bufferId, sliceId, switchId)");
	    
	 // Create link info table
	    stmt.execute ("DROP TABLE IF EXISTS linkTable");
	    stmt.execute ("CREATE TABLE linkTable (" +
	    		"sliceId VARCHAR(40) NOT NULL," +
	    		"linkId INT NOT NULL," +
	    		"switchId BIGINT NOT NULL," +
	    		"outPortId INT NOT NULL," +
	    		"nextHop BIGINT NOT NULL," +
	    		"PRIMARY KEY (sliceId,linkId,switchId,outPortId))");
	    //stmt.execute ("CREATE INDEX linkIndex ON linkTable (sliceId, linkId, switchId, outPortId)");
	    
//////////SwitchTable prepared statements
	    
		
	    //Variables: sliceId, switchId, phyPortId, virtPortId, accessPort, linkId, endPoint, status
	    statements.insertSwitchTable = conn.prepareStatement("INSERT INTO switchTable " +
	    		"VALUES (?,?,?,?,?,?,?,?)");
	    
		//Variables: sliceId, switchId, linkId
		statements.selectSwitchTableOne = conn.prepareStatement("SELECT phyPortId FROM switchTable " +
				"WHERE sliceId = ? and switchId = ? and linkId = ? and endPoint = false and status = true");
		
		//Variables: sliceId, linkId, switchId
		statements.selectSwitchTableTwo = conn.prepareStatement("SELECT endPoint FROM switchTable " +
				"WHERE sliceId = ? and switchId = ? and linkId = ? and status = true ORDER BY phyPortId LIMIT 1");

		//Variables: sliceId
		statements.selectSwitchTableThree = conn.prepareStatement("SELECT linkId FROM linkTable " +
				"WHERE sliceId = ? ORDER BY linkId DESC LIMIT 1");
		
		//Variables: sliceId, switchId
		statements.selectSwitchTableFour = conn.prepareStatement("SELECT phyPortId, virtPortId FROM switchTable " +
				"WHERE sliceId = ? and switchId = ? and status = true and virtPortId != 0 ORDER BY phyPortId");
		
		//Variables: sliceId, switchId, linkId, phyPortId
		statements.selectSwitchTableFive = conn.prepareStatement("SELECT phyPortId, virtPortId, endPoint FROM switchTable " +
				"WHERE sliceId = ? and switchId = ? and linkId = ? and phyPortId = ? and status = true ORDER BY phyPortId"); 
		
		//Variables: sliceId, switchId
		statements.selectSwitchTableSix = conn.prepareStatement("SELECT virtPortId, phyPortId, linkId FROM switchTable " +
				"WHERE sliceId = ? and switchId = ? and virtPortId > 0 and endPoint = true and status = true");
		
		//Variables: sliceId, switchId, virtPortId
		statements.selectSwitchTableSeven = conn.prepareStatement("SELECT virtPortId, phyPortId, linkId FROM switchTable " +
				"WHERE sliceId = ? and switchId = ? and virtPortId = ? and endPoint = true and status = true"); 
		
		//Variables: sliceId, switchId, phyPortId, status
		statements.selectSwitchTableEight = conn.prepareStatement("SELECT virtPortId, linkId FROM switchTable " +
				"WHERE sliceId = ? and switchId = ? and phyPortId = ? and status = ?"); 
		
		//Variables: sliceId, linkId, status
		statements.selectSwitchTableNine = conn.prepareStatement("SELECT switchId, virtPortId FROM switchTable " +
				"WHERE sliceId = ? and linkId = ? and endPoint = true and status = ?"); 
		
		//Variables: sliceId, linkId, switchId, nextHop
		statements.selectSwitchTableTen = conn.prepareStatement("SELECT status FROM switchTable " +
				"WHERE sliceId = ? and linkId = ? and switchId != ? and switchId != ? and endPoint = false"); 
		
		//Variables: sliceId, switchId
		statements.selectSwitchTableEleven = conn.prepareStatement("SELECT virtPortId FROM switchTable " +
				"WHERE sliceId = ? and switchId = ? ORDER BY virtPortId DESC LIMIT 1"); 
		
		//Variables: sliceId, linkId
		statements.selectSwitchTableTwelve = conn.prepareStatement("SELECT switchId, phyPortId, virtPortId, endPoint FROM switchTable " +
				"WHERE sliceId = ? and linkId = ?"); 
		
		statements.selectSwitchTableThirteen = conn.prepareStatement("SELECT virtPortId, accessPort, status FROM switchTable " +
				"WHERE sliceId = ? and switchId = ? and phyPortId = ?");
		
		statements.selectSwitchTableFourteen = conn.prepareStatement("SELECT linkId FROM switchTable " +
				"WHERE sliceId = ? and switchId = ? and virtPortId = ? and status = true");
		
		statements.selectSwitchTableFifthteen = conn.prepareStatement("SELECT switchId, virtPortId FROM switchTable " +
				"WHERE sliceId = ? and linkId = ? and switchId != ? and endPoint = true and accessPort = false and status = true");
		
		statements.selectSwitchTableSixteen = conn.prepareStatement("SELECT linkId, phyPortId FROM switchTable " +
				"WHERE sliceId = ? and switchId = ? and endPoint=false and status = true");
		
		statements.selectSwitchTableSeventeen = conn.prepareStatement("SELECT linkId, virtPortId FROM switchTable " +
				"WHERE sliceId = ? and switchId = ?  and status = true");
		
		statements.selectSwitchTableEightteen = conn.prepareStatement("SELECT switchId, phyPortId FROM switchTable " +
				"WHERE sliceId = ? and linkId = ? and endPoint = false and status = true");
		
		statements.selectSwitchTableNineteen = conn.prepareStatement("SELECT switchId, phyPortId, virtPortId FROM switchTable " +
				"WHERE sliceId = ? and switchId != ? and linkId = ? and endPoint = true LIMIT 1"); 
		
		statements.selectSwitchTableTwenty = conn.prepareStatement("SELECT virtPortId FROM switchTable " +
				"WHERE sliceId = ? and switchId = ? and linkId = ? and endPoint = true LIMIT 1"); 
		
		statements.updateSwitchTable = conn.prepareStatement("UPDATE switchTable SET status = ? " +
				"WHERE sliceId = ? and switchId = ? and linkId = ? and virtPortId = ?"); 
		
		//Variables: sliceId
		statements.deleteSwitchTableOne = conn.prepareStatement("DELETE FROM switchTable " +
				"WHERE sliceId = ? and status = true");
		
	    //Variables: sliceId, linkId
		statements.deleteSwitchTableTwo = conn.prepareStatement("DELETE FROM switchTable " +
				"WHERE sliceId = ? and linkId = ?");
		
//////////BufferTable prepared statements
		
		
		//Variables: sliceId
		statements.deleteBufferTableOne = conn.prepareStatement("DELETE FROM bufferTable " +
				"WHERE sliceId = ?");
		
		//Variables: flowMatch, bufferId, sliceId, switchId
		statements.insertBufferTable = conn.prepareStatement("INSERT INTO bufferTable "+
				"VALUES (?,?,?,?)");

		//Variables: bufferId, sliceId, switchId
		statements.selectBufferTable = conn.prepareStatement("SELECT flowMatch FROM bufferTable " +
				"WHERE bufferId = ? and sliceId = ? and switchId = ? LIMIT 1");		
	
		//Variables: bufferId, sliceId, switchId
		statements.deleteBufferTableTwo = conn.prepareStatement("DELETE FROM bufferTable " +
				"WHERE bufferId = ? and sliceId = ? and switchId = ?");

		
//////////LinkTable prepared statements
		
		
		//Variables: sliceId
		statements.deleteLinkTableOne = conn.prepareStatement("DELETE FROM linkTable " +
				"WHERE sliceId = ?");
		
		//Variables: sliceId, linkId, switchId, outPortId
		statements.selectLinkTableOne = conn.prepareStatement("SELECT nextHop FROM linkTable " +
				"WHERE sliceId = ? and linkId = ? and switchId = ? and outPortId = ? LIMIT 1");
				
		//Variables: sliceId, switchId, outPortId
		statements.selectLinkTableTwo = conn.prepareStatement("SELECT nextHop FROM linkTable " +
				"WHERE sliceId = ? and switchId = ? and outPortId = ? LIMIT 1");
		
		//Variables: sliceId, linkId, switchId, nextHop
		statements.selectLinkTableThree = conn.prepareStatement("SELECT outPortId FROM linkTable " +
				"WHERE sliceId = ? and linkId = ? and switchId = ? and nextHop = ? LIMIT 1");
		
		//Variables: sliceId, linkId
		statements.selectLinkTableFour = conn.prepareStatement("SELECT switchId, outPortId, nextHop FROM linkTable " +
				"WHERE sliceId = ? and linkId = ?");
		
		
		//Variables: sliceId, linkId
		statements.deleteLinkTableTwo = conn.prepareStatement("DELETE FROM linkTable " +
				"WHERE sliceId = ? and linkId = ?");
		
		//Variables: sliceId, linkId, switchId, phyPortId, nextHop
		statements.insertLinkTable = conn.prepareStatement("INSERT INTO linkTable " +
		"VALUES (?,?,?,?,?)");
		

//////////LeaseTable prepared statements
		
		
		//Variables: sliceId, linkName
		statements.selectLeaseTableOne = conn.prepareStatement("SELECT linkId FROM leaseTable " +
				"WHERE sliceId = ? and linkName = ? LIMIT 1"); 
		
		//Variables: sliceId, linkId, switchId, phyPortId
		statements.selectLeaseTableTwo = conn.prepareStatement("SELECT virtPortId FROM leaseTable " +
				"WHERE sliceId = ? and linkId = ? and switchId = ? and phyPortId = ?"); 
		
		//Variables: sliceId, linkName, linkId, switchId, phyPortId, virtPortId
		statements.insertLeaseTable = conn.prepareStatement("INSERT INTO leaseTable " +
		"VALUES (?,?,?,?,?,?)");
		
	    conn.commit();
	    if(stmt!=null)
	        stmt.close();
	}
	
	
/**
 * @name sqlLinkTableFill()
 * @authors roberto.doriguzzi, matteo.gerola
 * @description This function creates an association within a link between a switch and a next-hop. 
 * It's use to add this information in the flowTable
 * @exception SQLException, RuntimeException, ConfigError
 */
	public void sqlLinkTableFill() 
	throws SQLException, RuntimeException, ConfigError {
		Statement stmt = null;
		stmt = conn.createStatement();
		long srcSwitch;
		long dstSwitch;

		VTConfig vtConfig = new VTConfig();
		
		for (VTSlice currentSlice : vtConfig.vtSliceList) {
			for (VTLink currentLink : currentSlice.vtLinkList) {
				for (VTHop currentHop : currentLink.vtHopList) {
					srcSwitch = FlowSpaceUtil.parseDPID(currentHop.srcDpid);
					dstSwitch = FlowSpaceUtil.parseDPID(currentHop.dstDpid);
					statements.insertLinkTable.setString(1, currentSlice.sliceId);
					statements.insertLinkTable.setInt(2, currentLink.linkId);
					statements.insertLinkTable.setLong(3, srcSwitch);
					statements.insertLinkTable.setInt(4, currentHop.srcPort);
					statements.insertLinkTable.setLong(5, dstSwitch);
					statements.insertLinkTable.executeUpdate();	
					
					statements.insertLinkTable.setString(1, currentSlice.sliceId);
					statements.insertLinkTable.setInt(2, currentLink.linkId);
					statements.insertLinkTable.setLong(3, dstSwitch);
					statements.insertLinkTable.setInt(4, currentHop.dstPort);
					statements.insertLinkTable.setLong(5, srcSwitch);
					statements.insertLinkTable.executeUpdate();
					
				}
			}
		}
		
	    conn.commit();
	    if(stmt!=null)
	        stmt.close();
	}
	
	
/**
 * @name sqlDbInitSwitchInfo()
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @param List<OFPhysicalPort> phyPortList = list of physical ports available on this switch for this slice
 * @authors roberto.doriguzzi, matteo.gerola
 * @info This function is used when a FeatureReply packet arrives. It initialises the db for the switch.
 * @exception SQLException
 * @exception RuntimeException
 * @exception ConfigError
 */
	@SuppressWarnings("unchecked")
	public void sqlDbInitSwitchInfo(String sliceId, long switchId, List<OFPhysicalPort> phyPortList) 
	throws SQLException, RuntimeException, ConfigError {
		Statement stmt = null;

		int virtPortId = 101;
		boolean endPoint = true;
		LinkedList<Integer> accessPortList = new LinkedList<Integer>();
		HashMap<Integer,HashMap<Integer,Boolean>> linkPortMap = 
			new HashMap<Integer,HashMap<Integer,Boolean>>();
		stmt = conn.createStatement();
		String sql = null;
		
		//get slice info from config file
		VTSlice sliceInfo = new VTSlice();
		sliceInfo.GetVTSliceConfig(sliceId);
		
		//convert dpid in string and get the link list for this slice
		String switchIdString = FlowSpaceUtil.dpidToString(switchId);
		sliceInfo.GetLinksInfo(switchIdString, linkPortMap);
		
		//add port to list only if this port has no link within the slice
		for (OFPhysicalPort currentPort:phyPortList) {
			if (!linkPortMap.containsKey(Integer.valueOf(currentPort.getPortNumber()))) accessPortList.add(Integer.valueOf(currentPort.getPortNumber()));
		}
		
		//delete previous switch info from switchTable
		sql="DELETE FROM switchTable WHERE sliceId = '" + sliceId + "' and switchId = " + switchId;
		stmt.executeUpdate(sql);
		conn.commit();
		stmt.close();
		

		//insert the access ports in the table
		for (int currentPort:accessPortList) {
			statements.insertSwitchTable.setString(1, sliceId);
			statements.insertSwitchTable.setLong(2, switchId);
			statements.insertSwitchTable.setInt(3, currentPort);
			statements.insertSwitchTable.setInt(4, currentPort);
			statements.insertSwitchTable.setBoolean(5, true);
			statements.insertSwitchTable.setInt(6, 0);
			statements.insertSwitchTable.setBoolean(7, true);
			statements.insertSwitchTable.setBoolean(8, true);
			statements.insertSwitchTable.execute();	
		}
				
		//insert the link port in the table
		Set<Integer> keySetPortMap = linkPortMap.keySet();
		for(int currentPort:keySetPortMap){
			HashMap<Integer,Boolean> tmpPortInfo = linkPortMap.get(currentPort);
			Set<Integer> keySetPortInfo = tmpPortInfo.keySet();
			for(int currentLink:keySetPortInfo){
				endPoint = tmpPortInfo.get(currentLink);
				statements.insertSwitchTable.setString(1, sliceId);
				statements.insertSwitchTable.setLong(2, switchId);
				statements.insertSwitchTable.setInt(3, currentPort);
				if (endPoint == true) {
					statements.insertSwitchTable.setInt(4, virtPortId);
					virtPortId++;
				}
				else statements.insertSwitchTable.setInt(4, 0);
				statements.insertSwitchTable.setBoolean(5, false);
				statements.insertSwitchTable.setInt(6, currentLink);
				statements.insertSwitchTable.setBoolean(7, endPoint);
				statements.insertSwitchTable.setBoolean(8, true);
				statements.insertSwitchTable.execute();
			}	
		}
		conn.commit();
	}
	

/**
 * @param sliceId
 * @param link
 * @param status
 * @param vtConfigInterface
 * @return
 */
	public boolean sqlUpdateVirtualLink(String sliceId, VTLink currentLink, int status, VTConfigInterface vtConfig) 
	throws SQLException {
		boolean ret = true;
		
		HashMap<Long,LinkedList<String>> activeFlows = new HashMap<Long,LinkedList<String>>();
		LinkedList<VTLinkStatus> linkStatusList = new LinkedList<VTLinkStatus>();
		switch (status) {
			case 0: {
				//add link
				addVirtualLink (sliceId, currentLink, status, vtConfig, linkStatusList);
				break;
			}
			case 1: {
				//delete link
				deleteVirtualLink (sliceId, currentLink, status, vtConfig, activeFlows, linkStatusList);
				break;
			}
			case 2: {
				//update link
				deleteVirtualLink (sliceId, currentLink, status, vtConfig, activeFlows, linkStatusList);
				addVirtualLink (sliceId, currentLink, status, vtConfig, linkStatusList);
				break;
			}
		}
		
		List<FVEventHandler> handlers = VeRTIGO.getInstance().getHandlersCopy();
		for (VTLinkStatus linkStatus : linkStatusList) {
			for (FVEventHandler handler : handlers) {
				if (handler.getName().contains("slicer_") && handler.getName().contains(sliceId) &&
						handler.getName().contains(FlowSpaceUtil.dpidToString(linkStatus.switchId))) {
					if (linkStatus.phyPortId != 0 && linkStatus.virtPortId != 0) {
						VTEvent event = new VTEvent(null,handler,linkStatus.phyPortId,linkStatus.virtPortId,linkStatus.status);
						try {
							handler.handleEvent(event);
						} catch (UnhandledEvent e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}	
						break;
					}
				}
			}	
		}
		
		Set<Long> activeFlowsSet = activeFlows.keySet();
		for(long switchId : activeFlowsSet) {
			for (FVEventHandler handler : handlers) {
				if (handler.getName().contains("classifier-") && 
						handler.getName().contains(FlowSpaceUtil.dpidToString(switchId))) {
					if (!activeFlows.get(switchId).isEmpty()) {
						VTEvent event = new VTEvent(null,handler,activeFlows.get(switchId));
						try {
							handler.handleEvent(event);
						} catch (UnhandledEvent e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}	
						break;
					}
				}
			}
		}
		
		conn.commit();
		return ret;
	}
		
	
	public boolean addVirtualLink (String sliceId, VTLink currentLink, int status, VTConfigInterface vtConfig, 
			LinkedList<VTLinkStatus> linkStatus) 
	throws SQLException {
		boolean ret = true;
		int virtPortId = 0;
		long currentSwitch = 0;
		int currentPort = 0;
		boolean endPoint;
		boolean alreadyLeased;
		long nextHop;
		String linkString = currentLink.toString();
		
		for (VTHop currentHop:currentLink.vtHopList) {
			for (int i = 1; i <= 2; i++) {
				endPoint = false;
				alreadyLeased = false;
				nextHop = 0;
				virtPortId = 0;
				switch (i) {
					case 1: {
						currentSwitch = FlowSpaceUtil.parseDPID(currentHop.srcDpid);
						currentPort = currentHop.srcPort;
						nextHop = FlowSpaceUtil.parseDPID(currentHop.dstDpid);
						if (currentHop.hopId == 1)
							endPoint = true;
						break;
					}
					case 2: {
						currentSwitch = FlowSpaceUtil.parseDPID(currentHop.dstDpid);
						currentPort = currentHop.dstPort;
						nextHop = FlowSpaceUtil.parseDPID(currentHop.srcDpid);
						if (currentHop.hopId == currentLink.hopsNumber)
							endPoint = true;
						break;
					}
				}
				VTLinkStatus linkInfo = new VTLinkStatus();
				if (endPoint == true) {
					virtPortId = sqlGetLeasePortInfo(sliceId, linkString, currentLink.linkId, currentSwitch, currentPort);
					if (virtPortId == 0)
						virtPortId = sqlGetNewVirtPortId(sliceId, currentSwitch);
					else
						alreadyLeased = true;
					linkInfo.phyPortId = currentPort;
					linkInfo.virtPortId = virtPortId;
					linkInfo.status = true;
					linkInfo.switchId = currentSwitch;
				}
				
				boolean isFirstLink = sqlCheckIfFirstVLink(sliceId, currentSwitch, currentPort, true);
				if (isFirstLink == true) {
					linkStatus.add(new VTLinkStatus(currentSwitch,currentPort,currentPort,false));
				}
				
				if (endPoint == true && virtPortId != 0) {
					linkStatus.add(linkInfo);
					if (alreadyLeased == false)
						sqlInsertLeaseInfo(sliceId, linkString, currentLink.linkId, currentSwitch, currentPort, virtPortId);
				}
					
				statements.insertSwitchTable.setString(1, sliceId);
				statements.insertSwitchTable.setLong(2, currentSwitch);
				statements.insertSwitchTable.setInt(3, currentPort);
				if (endPoint == true) {
					statements.insertSwitchTable.setInt(4, virtPortId);
				}
				else statements.insertSwitchTable.setInt(4, 0);
				statements.insertSwitchTable.setBoolean(5, false);
				statements.insertSwitchTable.setInt(6, currentLink.linkId);
				statements.insertSwitchTable.setBoolean(7, endPoint);
				statements.insertSwitchTable.setBoolean(8, true);
				FVLog.log(LogLevel.DEBUG, null, "vtopology: addVirtualLink: insertSwitchTable: insert => "
						+ " sliceId = " + sliceId + " switchId = " + Long.toHexString(currentSwitch) + " phyPortId = " + currentPort
						 + " virtPortId = " + virtPortId + " isAccess = false linkId = " + currentLink.linkId
						 + " endPoint = " + endPoint + " status = true");
				statements.insertSwitchTable.execute();
		
				statements.insertLinkTable.setString(1, sliceId);
				statements.insertLinkTable.setInt(2, currentLink.linkId);
				statements.insertLinkTable.setLong(3, currentSwitch);
				statements.insertLinkTable.setInt(4, currentPort);
				statements.insertLinkTable.setLong(5, nextHop);
				FVLog.log(LogLevel.DEBUG, null, "vtopology: addVirtualLink: insertLinkTable: insert => "
						+ " sliceId = " + sliceId + " linkId = " + currentLink.linkId + " switchId = " + Long.toHexString(currentSwitch)
						 + " phyPortId = " + currentPort + " nextHop = " + Long.toHexString(nextHop));
				statements.insertLinkTable.executeUpdate();	
			}
		}
		conn.commit();
		return ret;
	}
	
	
	public boolean deleteVirtualLink (String sliceId, VTLink currentLink, int status, VTConfigInterface vtConfig,
			HashMap<Long,LinkedList<String>> activeFlows, LinkedList<VTLinkStatus> linkStatus) 
	throws SQLException {
		boolean ret = true;
		ResultSet rs = null;
		int virtPortId = 0;
		int phyPortId = 0;
		long currentSwitch = 0;
		String flowMatch;
		LinkedList<String> switchportList = new LinkedList<String>();
		
		statements.selectSwitchTableTwelve.setString(1, sliceId);
		statements.selectSwitchTableTwelve.setInt(2, currentLink.linkId);
		FVLog.log(LogLevel.DEBUG, null, "vtopology: deleteVirtualLink: selectSwitchTableTwelve: variables => "
				+ " sliceId = " + sliceId + " linkId = " + currentLink.linkId);
		rs = statements.selectSwitchTableTwelve.executeQuery();
		while (rs.next()){
			currentSwitch = rs.getLong("switchId");
			virtPortId = rs.getInt("virtPortId");
			phyPortId = rs.getInt("phyPortId");
			if (rs.getBoolean("endPoint") == true) {
				VTLinkStatus linkInfo = new VTLinkStatus();
				linkInfo.phyPortId = phyPortId;
				linkInfo.virtPortId = virtPortId;
				linkInfo.status = false;
				linkInfo.switchId = currentSwitch;
				linkStatus.add(linkInfo);
			}
			FVLog.log(LogLevel.DEBUG, null, "vtopology: deleteVirtualLink: selectSwitchTableTwelve: results => "
					+ " switchId = " + Long.toHexString(currentSwitch) + " virtPortId = " + virtPortId + " endPoint = " + rs.getBoolean("endPoint"));
			switchportList.add(currentSwitch+"/"+phyPortId);
		}

		statements.deleteSwitchTableTwo.setString(1, sliceId);
		statements.deleteSwitchTableTwo.setInt(2, currentLink.linkId);
		statements.deleteSwitchTableTwo.execute();
		FVLog.log(LogLevel.DEBUG, null, "vtopology: deleteVirtualLink: deleteSwitchTableTwo: delete => "
				+ " sliceId = " + sliceId + " linkId = " + currentLink.linkId);	
		statements.deleteLinkTableTwo.setString(1, sliceId);
		statements.deleteLinkTableTwo.setInt(2, currentLink.linkId);
		statements.deleteLinkTableTwo.execute();
		FVLog.log(LogLevel.DEBUG, null, "vtopology: deleteVirtualLink: deleteLinkTableTwo: delete => "
				+ " sliceId = " + sliceId + " linkId = " + currentLink.linkId);	
		
		
		for (String switchport : switchportList){ 
			String[] portElements = switchport.split("/");
			currentSwitch = Long.decode(portElements[0]);
			phyPortId = Integer.decode(portElements[1]);
			boolean isFirstLink = sqlCheckIfFirstVLink(sliceId, currentSwitch, phyPortId, false);
			if (isFirstLink == true)
				linkStatus.add(new VTLinkStatus(currentSwitch,phyPortId,phyPortId,true));
		}
		conn.commit();
		return ret;
	}
	
/**
 * @authors roberto.doriguzzi, matteo.gerola
 * @info This function is used when there are some changes in the physical switch (e.g.: port up/down). 
 * It updates the db for the switch, modifying virtual ports (access or link) associated to the physical ports provided
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @param int portId = port identifier
 * @param boolean portStatus = new port status
 * @return HashMap<Long,LinkedList<Integer>> classifierToVirtPortMap = mapping between switchId and virtual ports
 * @exception SQLException
 * @exception RuntimeException
 * @exception ConfigError
 */
	public HashMap<Long,LinkedList<Integer>> sqlDbUpdatePortStatus(String sliceId, long switchId, int portId, boolean portStatus) 
	throws SQLException {
		ResultSet rs = null;
		HashMap<Long,LinkedList<Integer>> ret = new HashMap<Long,LinkedList<Integer>>();
		LinkedList<Integer> linkList = new LinkedList<Integer>();
		
		int virtPortId = 0;
		int linkId = 0;
		long currentSwitch = 0;
		long nextHop = 0;
		boolean currentStatus; 
		boolean checkStatus = true;
		if (portStatus == true) {
			currentStatus = false;
			//Variables: sliceId, switchId, outPortId
			statements.selectLinkTableTwo.setString(1,sliceId);
			statements.selectLinkTableTwo.setLong(2,switchId);
			statements.selectLinkTableTwo.setInt(3,portId);
			FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbUpdatePortStatus: selectLinkTableTwo: variables => "
					+ " sliceId = " + sliceId + " switchId = " + Long.toHexString(switchId) + " phyPortId = " + portId);
			rs = statements.selectLinkTableTwo.executeQuery();
			while (rs.next ()){
				nextHop = rs.getLong("nextHop");
				FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbUpdatePortStatus: selectLinkTableTwo: results => "
						+ " nextHop = " + Long.toHexString(nextHop));
			}
		}
		else
			currentStatus = true;
		
		//select virtPortIds for the current switchId and linkIds if the switch is not endPoint
		statements.selectSwitchTableEight.setString(1,sliceId);
		statements.selectSwitchTableEight.setLong(2,switchId);
		statements.selectSwitchTableEight.setInt(3,portId);
		statements.selectSwitchTableEight.setBoolean(4,currentStatus);
		FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbUpdatePortStatus: selectSwitchTableEight: variables => "
				+ " sliceId = " + sliceId + " switchId = " + Long.toHexString(switchId) + " phyPortId = " + portId + " status = " + currentStatus);
		rs = statements.selectSwitchTableEight.executeQuery();
		LinkedList<Integer> virtPortList = new LinkedList<Integer>();
		while (rs.next ()){
			checkStatus = true;
			virtPortId = rs.getInt("virtPortId");
			linkId = rs.getInt("linkId");
			if (virtPortId != 0) {
				if (portStatus == true)
					checkStatus = CheckLinkStatus(sliceId, linkId, switchId, nextHop); 
				if (checkStatus == true) {
					virtPortList.add(virtPortId);
					FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbUpdatePortStatus: selectSwitchTableEight: results => "
							+ " virtPortId = " + virtPortId);
					UpdateLinkStatus(portStatus, sliceId, switchId, linkId, virtPortId);
				}
			}
			else if (linkId != 0) {
				linkList.add(linkId);
				FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbUpdatePortStatus: selectSwitchTableEight: results => "
						+ " linkId = " + linkId);
				UpdateLinkStatus(portStatus, sliceId, switchId, linkId, virtPortId);
			}
		}
		
		ret.put(switchId, virtPortList);
		
		//check other switches
		for (int currentLink : linkList) {
			checkStatus = true;
			if (portStatus == true)
				checkStatus = CheckLinkStatus(sliceId, linkId, switchId, nextHop); 
			if (checkStatus == true) {
				//select virtPortIds for the end point switches in the current link
				statements.selectSwitchTableNine.setString(1,sliceId);
				statements.selectSwitchTableNine.setInt(2,currentLink);
				statements.selectSwitchTableNine.setBoolean(3,currentStatus);
				FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbUpdatePortStatus: selectSwitchTableNine: variables => "
						+ " sliceId = " + sliceId + " linkId = " + currentLink + " status = " + currentStatus);
				rs = statements.selectSwitchTableNine.executeQuery();
				while (rs.next ()){
					LinkedList<Integer> portList = new LinkedList<Integer>();
					currentSwitch = rs.getLong("switchId");
					virtPortId = rs.getInt("virtPortId");
					if (!ret.containsKey(currentSwitch)) {
						portList.add(virtPortId);
						ret.put(currentSwitch, portList);
					}
					else {
						portList = ret.get(currentSwitch);
						if (!portList.contains(virtPortId))
							portList.add(virtPortId);
						ret.put(currentSwitch, portList);
					}
					FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbUpdatePortStatus: selectSwitchTableNine: results => "
							+ " switchId = " + Long.toHexString(currentSwitch) + " virtPortId = " + virtPortId);
					UpdateLinkStatus(portStatus, sliceId, currentSwitch, currentLink, virtPortId);
				}
			}
		}
		conn.commit();
		
		return ret;
	}
	
	
	public boolean CheckLinkStatus(String sliceId, int linkId, long switchId, long nextHop) 
	throws SQLException {
		boolean checkStatus = true;
		ResultSet rs = null;
		//Variables: sliceId, linkId, switchId, nextHop
		statements.selectSwitchTableTen.setString(1,sliceId);
		statements.selectSwitchTableTen.setInt(2,linkId);
		statements.selectSwitchTableTen.setLong(3,switchId);
		statements.selectSwitchTableTen.setLong(4,nextHop);
		FVLog.log(LogLevel.DEBUG, null, "vtopology: CheckLinkStatus: selectSwitchTableTen: variables => "
				+ " sliceId = " + sliceId + " linkId = " + linkId + " switchId = " + Long.toHexString(switchId) + " nextHop = " + Long.toHexString(nextHop));
		rs = statements.selectSwitchTableTen.executeQuery();
		while (rs.next ()){
			boolean tmpStatus = rs.getBoolean("status");
			FVLog.log(LogLevel.DEBUG, null, "vtopology: CheckLinkStatus: selectSwitchTableTen: results => "
					+ " status = " + checkStatus);
			if (tmpStatus == false)
				checkStatus = false;
		}
		return checkStatus;
	}
	
	
	public boolean UpdateLinkStatus(boolean newStatus, String sliceId, long switchId, int linkId, int virtPortId) 
	throws SQLException {
		boolean ret = true;
		//update port status for the current switch
		statements.updateSwitchTable.setBoolean(1,newStatus);
		statements.updateSwitchTable.setString(2,sliceId);
		statements.updateSwitchTable.setLong(3,switchId);
		statements.updateSwitchTable.setInt(4,linkId);
		statements.updateSwitchTable.setInt(5,virtPortId);
		FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbUpdatePort: updateSwitchTable: update portStatus = "+ newStatus 
				+" with variables => sliceId = " + sliceId + " switchId = " + Long.toHexString(switchId) + " linkId = " + linkId
				+" virtPortId = " + virtPortId);
		statements.updateSwitchTable.executeUpdate();
		return ret;
	}
	
/**
 * @name GetNewLinkId
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function (used by FVUserAPIImpl) return the current max linkId used within a slice
 * @param String sliceId = name of the slice     
 * @return linkId = current linkId maximum value in the slice
 * @return ret = boolean return code (TRUE: ok, FALSE: error in the function)  
 * @exception SQLException
 */
	public short sqlGetNewLinkId(String sliceId, String linkName, VTConfigInterface vtConfig) 
	throws SQLException {
		short linkId = 0;

		linkId = sqlGetLeaseLinkInfo(sliceId, linkName);
		if (linkId == 0) {
			statements.selectSwitchTableThree.setString(1, sliceId);
			ResultSet rs = statements.selectSwitchTableThree.executeQuery();
			while (rs.next())
				linkId = (short) (rs.getInt("linkId")+1);
			rs.close ();
		}
		if (linkId == 0) linkId = 1;
		if (linkId > 0xFFFFFF) linkId = 1;
		return linkId;
	}
	
	
	public int sqlGetNewVirtPortId(String sliceId, long switchId) 
	throws SQLException {
		int virtPortId = 0;
		statements.selectSwitchTableEleven.setString(1, sliceId);
		statements.selectSwitchTableEleven.setLong(2, switchId);
		FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlGetNewVirtPortId: selectSwitchTableEleven: variables => "
				+ " sliceId = " + sliceId + " switchId = " + switchId);
		ResultSet rs = statements.selectSwitchTableEleven.executeQuery();
		while (rs.next()) {
			virtPortId = rs.getInt("virtPortId") + 1;
			FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlGetNewVirtPortId: selectSwitchTableEleven: results => "
					+ " virtPortId = " + virtPortId);
		}
		rs.close ();
		if (virtPortId <= 100)
			virtPortId = 101;
		return virtPortId;
	}
	
/**
 * @name sqlRemoveSliceInfo
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function removes all the slice information from switchTable, flowTable, bufferTable and linkTable
 * @param String sliceId = name of the slice     
 * @return ret = boolean return code (TRUE: ok, FALSE: error in the function)  
 * @exception SQLException
 */
	public boolean sqlRemoveSliceInfo(String sliceId) 
	throws SQLException {
		boolean ret = true;
		statements.deleteSwitchTableOne.setString(1, sliceId);
		statements.deleteSwitchTableOne.execute();
		statements.deleteBufferTableOne.setString(1, sliceId);
		statements.deleteBufferTableOne.execute();
		statements.deleteLinkTableOne.setString(1, sliceId);
		statements.deleteLinkTableOne.execute();
		conn.commit();		
		return ret;
	}

	
	public boolean sqlInsertLeaseInfo(String sliceId, String linkName, int linkId, long switchId, int phyPortId, int virtPortId) 
	throws SQLException {
		boolean ret = true;
		statements.insertLeaseTable.setString(1, sliceId);
		statements.insertLeaseTable.setString(2, linkName);
		statements.insertLeaseTable.setInt(3, linkId);
		statements.insertLeaseTable.setLong(4, switchId);
		statements.insertLeaseTable.setInt(5, phyPortId);
		statements.insertLeaseTable.setInt(6, virtPortId);
		statements.insertLeaseTable.execute();
		FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlInsertLeaseInfo: insertLeaseTable: insert => "
				+ " sliceId = " + sliceId + " linkName = " + linkName + " linkId = " + linkId
				 + " switchId = " + Long.toHexString(switchId) + " phyPortId = " + phyPortId + "virtPortId = " + virtPortId);
		conn.commit();
		return ret;
	}
	
	
	public short sqlGetLeaseLinkInfo(String sliceId, String linkName) 
	throws SQLException {
		short linkId = 0;
		statements.selectLeaseTableOne.setString(1, sliceId);
		statements.selectLeaseTableOne.setString(2, linkName);
		FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlGetLeaseInfo: selectLeaseTableOne: variables => "
				+ " sliceId = " + sliceId + " linkName = " + linkName);
		ResultSet rs = statements.selectLeaseTableOne.executeQuery();
		while (rs.next()) {
			linkId = (short) rs.getInt("linkId");
			FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlGetLeaseInfo: selectLeaseTableOne: results => "
					+ " linkId = " + linkId);
		}
		rs.close ();		
		return linkId;
	}
	
	
	public int sqlGetLeasePortInfo(String sliceId, String linkName, int linkId, long switchId, int phyPortId) 
	throws SQLException {
		int virtPortId = 0;
		statements.selectLeaseTableTwo.setString(1, sliceId);
		statements.selectLeaseTableTwo.setInt(2, linkId);
		statements.selectLeaseTableTwo.setLong(3, switchId);
		statements.selectLeaseTableTwo.setInt(4, phyPortId);
		FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlGetLeaseInfo: selectLeaseTableTwo: variables => "
				+ " sliceId = " + sliceId + " linkId = " + linkId + " switchId = " + Long.toHexString(switchId) + " phyPortId = " + phyPortId);
		ResultSet rs = statements.selectLeaseTableTwo.executeQuery();
		while (rs.next()) {
			virtPortId = rs.getInt("virtPortId");
			FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlGetLeaseInfo: selectLeaseTableTwo: results => "
					+ " virtPortId = " + virtPortId);
		}
		rs.close ();		
		return virtPortId;
	}

/**
 * @name sqlGetNextHop
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function returns the dpid of the next hop switch in the link
 * @param String sliceId = name of the slice
 * @param int linkId = link identifier within the slice
 * @param long switchId = dpid of the source switch
 * @param int outPortId = physical output port 
 * @return long nextHop = dpid of the next switch in the link
 * @exception SQLException
 */
	public nextHopClass sqlGetNextHop(String sliceId, int linkId, long switchId, int outPortId) 
	throws SQLException {
		long nextHop = 0;
		nextHopClass nhc = new nextHopClass();
		
		statements.selectLinkTableOne.setString(1, sliceId);
		statements.selectLinkTableOne.setInt(2, linkId);
		statements.selectLinkTableOne.setLong(3, switchId);
		statements.selectLinkTableOne.setInt(4, outPortId);
		FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlGetNextHop: selectLinkTableOne: variables => "
				+ " sliceId = " + sliceId + " switchId = " + Long.toHexString(switchId) + " linkId = " + linkId + " outPortId = " + outPortId);
		ResultSet rs = statements.selectLinkTableOne.executeQuery();
		while (rs.next ()) {
			nextHop = nhc.switchId = rs.getLong("nextHop");
			FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlGetNextHop: selectLinkTableOne: results => "
					+ " nextHop = " + Long.toHexString(nextHop));
		}
		
		statements.selectLinkTableThree.setString(1, sliceId);
		statements.selectLinkTableThree.setInt(2, linkId);
		statements.selectLinkTableThree.setLong(3, nhc.switchId);
		statements.selectLinkTableThree.setLong(4, switchId);
		
		rs = statements.selectLinkTableThree.executeQuery();
		while (rs.next ()) {
			
			nhc.inputPort = rs.getInt("outPortId");
		}
		
		return nhc;
	}
	
/**
 * @name sqlGetMiddlePointLinks
 * @authors roberto.doriguzzi matteo.gerola
 * @description This function returns the input/output physical ports of one or all virtual links crossing a middlepoint switch
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the source switch
 * @return HashMap <Integer,LinkedList<Integer>> = the list of virtual links and related in/out physical ports
 * @exception SQLException
 */
	public HashMap <Integer,LinkedList<Integer>> sqlGetMiddlePointLinks(String sliceId, long switchId, Integer linkId) 
	throws SQLException {
		ResultSet rs = null;
		HashMap <Integer,LinkedList<Integer>> LinkList = new HashMap <Integer,LinkedList<Integer>>();
		
		if(linkId == 0) { // all virtual links and related physical ports on this middlepoint
			statements.selectSwitchTableSixteen.setString(1, sliceId);
			statements.selectSwitchTableSixteen.setLong(2, switchId);
			rs = statements.selectSwitchTableSixteen.executeQuery();
			while (rs.next ()) {
				Integer tmpLinkId = rs.getInt("linkId");
				Integer phyPort = rs.getInt("phyPortId");
				if(!LinkList.containsKey(tmpLinkId)) {
					LinkedList <Integer> TmpList = new LinkedList <Integer>();
					TmpList.add(phyPort);
					LinkList.put(tmpLinkId, TmpList);
				}
				else {
					LinkedList <Integer> TmpList = LinkList.get(tmpLinkId);
					TmpList.add(phyPort);
				}
			}
			
		} else { // just one specific virtual link
			statements.selectSwitchTableOne.setString(1, sliceId);
			statements.selectSwitchTableOne.setLong(2, switchId);
			statements.selectSwitchTableOne.setInt(3, linkId);
			rs = statements.selectSwitchTableOne.executeQuery();
			while (rs.next ()) {
				Integer phyPort = rs.getInt("phyPortId");
				if(!LinkList.containsKey(linkId)) {
					LinkedList <Integer> TmpList = new LinkedList <Integer>();
					TmpList.add(phyPort);
					LinkList.put(linkId, TmpList);
				}
				else {
					LinkedList <Integer> TmpList = LinkList.get(linkId);
					TmpList.add(phyPort);
				}
			}
		}
		
		
		
		rs.close ();
		
		return LinkList;
	}	
	
	
/**
 * @name sqlGetLinkMiddlePoints
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function returns the list of middlepoints of a given virtual link
 * @param String sliceId = name of the slice
 * @param Integer linkId = the virtual link identifier
 * @return HashMap <Long,LinkedList<Integer>> = the list of DPIDs and related in/out physical ports
 * @exception SQLException
 */
	public HashMap <Long,LinkedList<Integer>> sqlGetLinkMiddlePoints(String sliceId, Integer linkId) 
	throws SQLException {
		HashMap <Long,LinkedList<Integer>> MiddlePointList = new HashMap <Long,LinkedList<Integer>>();
		
		statements.selectSwitchTableEightteen.setString(1, sliceId);
		statements.selectSwitchTableEightteen.setInt(2, linkId);
		ResultSet rs = statements.selectSwitchTableEightteen.executeQuery();
		while (rs.next ()) {
			Long switchId = rs.getLong("switchId");
			Integer phyPort = rs.getInt("phyPortId");
			if(!MiddlePointList.containsKey(switchId)) {
				LinkedList <Integer> TmpList = new LinkedList <Integer>();
				TmpList.add(phyPort);
				MiddlePointList.put(switchId, TmpList);
			}
			else {
				LinkedList <Integer> TmpList = MiddlePointList.get(switchId);
				TmpList.add(phyPort);
			}
		}
		rs.close ();
		
		return MiddlePointList;
	}	
	
/**
 * @name sqlGetLinkHops
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function returns a list of pairs <switchId,nextHop> linked with the outport of the switch with DPID=switchId
 * @param String sliceId = name of the slice
 * @param Integer linkId = the virtual link identifier
 * @return HashMap <LinkedList<Long>,Integer> = hashmap <<switchId, nextHop>, switchId_outPort>
 * @exception SQLException
 */
	public HashMap <LinkedList<Long>,Integer> sqlGetLinkHops(String sliceId, Integer linkId) 
	throws SQLException {
		HashMap <LinkedList<Long>,Integer> HopList = new HashMap <LinkedList<Long>,Integer>();
		
		statements.selectLinkTableFour.setString(1, sliceId);
		statements.selectLinkTableFour.setInt(2, linkId);
		ResultSet rs = statements.selectLinkTableFour.executeQuery();
		while (rs.next ()) {
			Long switchId = rs.getLong("switchId");
			Long nextHop = rs.getLong("nextHop");
			Integer outPortId = rs.getInt("outPortId");

			LinkedList <Long> TmpList = new LinkedList <Long>();
			TmpList.add(0,switchId);
			TmpList.add(1,nextHop);
			HopList.put(TmpList, outPortId);
		}
		rs.close ();
		
		return HopList;
	}
/**
 * @name sqlGetVirtualPortLinkMapping
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function returns the mapping between virtual ports and virtual links for a single switch
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the source switch
 * @return HashMap <Integer,Integer> = the hashmap 
 * @exception SQLException
 */
	public HashMap <Integer,Integer> sqlGetVirtualPortLinkMapping(String sliceId, long switchId) 
	throws SQLException {
		HashMap <Integer,Integer> LinkMap = new HashMap <Integer,Integer>();
		
		statements.selectSwitchTableSeventeen.setString(1, sliceId);
		statements.selectSwitchTableSeventeen.setLong(2, switchId);
		ResultSet rs = statements.selectSwitchTableSeventeen.executeQuery();
		while (rs.next ()) {
			if(rs.getInt("linkId") > 0)
				LinkMap.put(rs.getInt("virtPortId"), rs.getInt("linkId"));
		}
		rs.close ();
		
		return LinkMap;
	}	
	
/**
 * @name sqlGetEndPoint
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function tells you whether a switch is an end-point of a link
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the source switch
 * @param short linkId = link identifier
 * @return boolean = true if the switch is an end-point for the link
 * @exception SQLException
 */
	public boolean sqlGetEndPoint(String sliceId, long switchId, short linkId) 
	throws SQLException {
		boolean ret = false;
		
		statements.selectSwitchTableTwo.setString(1, sliceId);
		statements.selectSwitchTableTwo.setLong(2, switchId);
		statements.selectSwitchTableTwo.setInt(3, linkId);

		ResultSet rs = statements.selectSwitchTableTwo.executeQuery();
		while (rs.next()) {
			ret = rs.getBoolean("endPoint");
		}
		rs.close ();
		
		return ret;
	}
/**
 * @name sqlGetRemoteEndPoint
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function returns the DPID and outPort info of the remote endpoint of a given virtual link
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the source switch
 * @param short linkId = link identifier
 * @return List <Object> remoteEndPoint = <remote DPID, linkId, the phyPortId, virtPortId>
 * @exception SQLException
 */
	public List <Object> sqlGetRemoteEndPoint(String sliceId, long switchId, short linkId) 
	throws SQLException {
		List <Object> remoteEndPoint = null; 
		
		statements.selectSwitchTableNineteen.setString(1, sliceId);
		statements.selectSwitchTableNineteen.setLong(2, switchId);
		statements.selectSwitchTableNineteen.setInt(3, linkId);

		ResultSet rs = statements.selectSwitchTableNineteen.executeQuery();
		while (rs.next()) {
			remoteEndPoint = new ArrayList <Object>();
			remoteEndPoint.add(rs.getLong("switchId"));
			remoteEndPoint.add(Integer.valueOf(linkId));
			remoteEndPoint.add((Integer)rs.getInt("phyPortId"));
			remoteEndPoint.add((Integer)rs.getInt("virtPortId"));
		}
		rs.close ();
		
		return remoteEndPoint;
	}	
/**
 * @name sqlGetPhyToVirtMap
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function returns the value of the virtual port which "connects" the virtual link to the endpoint switch
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the source switch
 * @param short linkId = virtual link identifier
 * @return Integer = the virtual port number
 * @exception SQLException
 */
	public Integer sqlGetPhyToVirtMap(String sliceId, long switchId, short linkId) 
	throws SQLException {
		ResultSet rs = null;
		Integer virtPortId = null;

		statements.selectSwitchTableTwenty.setString(1, sliceId);
		statements.selectSwitchTableTwenty.setLong(2, switchId);
		statements.selectSwitchTableTwenty.setInt(3, linkId);
		rs = statements.selectSwitchTableTwenty.executeQuery();
		while (rs.next()) {
			virtPortId = rs.getInt("virtPortId");	
		}
		
		rs.close ();
		
		return virtPortId;
	}
	
/**
 * @name sqlGetPhyToVirtMap
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function returns the list of virtual ports mapped on each physical port
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the source switch
 * @return HashMap<Integer,LinkedList<Integer>> = the mapping between physical and virtual ports
 * @exception SQLException
 */
	public HashMap<Integer,LinkedList<Integer>> sqlGetPhyToVirtMap(String sliceId, long switchId) 
	throws SQLException {
		ResultSet rs = null;
		LinkedList<Integer> virtPortList;
		HashMap<Integer,LinkedList<Integer>> phyToVirtPortMap = new HashMap<Integer,LinkedList<Integer>>();
		
		statements.selectSwitchTableFour.setString(1, sliceId);
		statements.selectSwitchTableFour.setLong(2, switchId);
		rs = statements.selectSwitchTableFour.executeQuery();
		
		while (rs.next ()){
			int phyPortId = rs.getInt("phyPortId");
			
			if (phyToVirtPortMap.containsKey(phyPortId)) {
				virtPortList = phyToVirtPortMap.get(phyPortId);
				virtPortList.add(rs.getInt("virtPortId"));
			}
			else {
				virtPortList = new LinkedList<Integer>();
				virtPortList.add(rs.getInt("virtPortId"));
				phyToVirtPortMap.put(phyPortId, virtPortList);					
			}
		}
		
		rs.close ();
		
		return phyToVirtPortMap;
	}
	
	
/**
 * @name sqlGetVirtPortsMappings
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function returns the mapping between virtual ports and (physical ports, virtual links)
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @return VirtToPhyPortMap = mapping between virtual ports and (physical ports, virtual links) 
 * @exception SQLException  
 */
	public HashMap<Integer,LinkedList<Integer>> sqlGetVirtPortsMappings(String sliceId, long switchId) 
	throws SQLException {
		ResultSet rs = null;
		HashMap<Integer,LinkedList<Integer>> VirtToPhyPortMap = new HashMap<Integer,LinkedList<Integer>>();
		
		statements.selectSwitchTableSix.setString(1, sliceId);
		statements.selectSwitchTableSix.setLong(2, switchId);
		rs = statements.selectSwitchTableSix.executeQuery();
			
		while (rs.next ()){
			int virtPortId = rs.getInt("virtPortId");
			LinkedList<Integer> temp = new LinkedList<Integer>();
			temp.clear();
			temp.add(0,rs.getInt("phyPortId"));
			temp.add(1,rs.getInt("linkId"));
			VirtToPhyPortMap.put(virtPortId, temp);
		}
		rs.close ();
		return VirtToPhyPortMap;
	}
	
	
	public boolean sqlCheckIfFirstVLink(String sliceId, long switchId, int phyPortId, boolean status) 
	throws SQLException {
		int counter = 0;
		boolean isChanged = false;
		boolean isAccess = false;
		boolean hasLink = false;
		
		statements.selectSwitchTableThirteen.setString(1, sliceId);
		statements.selectSwitchTableThirteen.setLong(2, switchId);
		statements.selectSwitchTableThirteen.setInt(3, phyPortId);
		FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlCheckIfFirstVLink: selectSwitchTableThirteen: variables => "
				+ " sliceId = " + sliceId + " switchId = " + Long.toHexString(switchId) + " phyPortId = " + phyPortId);
		ResultSet rs = statements.selectSwitchTableThirteen.executeQuery();
		while (rs.next ()) {
			if (rs.getBoolean("accessPort") == true && rs.getBoolean("status") == true)
				isAccess = true;
			else if (rs.getBoolean("accessPort") == false)
				hasLink = true;
			FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlCheckIfFirstVLink: selectSwitchTableThirteen: results => "
					+ " accessPort = " + rs.getBoolean("accessPort") + " status = " + rs.getBoolean("status"));
			counter++;
		}
		rs.close ();
		
		if (counter == 0) {
			statements.insertSwitchTable.setString(1, sliceId);
			statements.insertSwitchTable.setLong(2, switchId);
			statements.insertSwitchTable.setInt(3, phyPortId);
			statements.insertSwitchTable.setInt(4, phyPortId);
			statements.insertSwitchTable.setBoolean(5, true);
			statements.insertSwitchTable.setInt(6, 0);
			statements.insertSwitchTable.setBoolean(7, true);
			statements.insertSwitchTable.setBoolean(8, true);
			statements.insertSwitchTable.execute();
			isChanged = true;
		}
		else {
			if (status == true && isAccess == true && hasLink == false) {
				statements.updateSwitchTable.setBoolean(1,false);
				statements.updateSwitchTable.setString(2,sliceId);
				statements.updateSwitchTable.setLong(3,switchId);
				statements.updateSwitchTable.setInt(4,0);
				statements.updateSwitchTable.setInt(5,phyPortId);
				FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlCheckIfFirstVLink: updateSwitchTable: update portStatus = "+ false 
						+" with variables => sliceId = " + sliceId + " switchId = " + Long.toHexString(switchId) + " linkId = " + 0
						+" virtPortId = " + phyPortId);
				statements.updateSwitchTable.executeUpdate();
				isChanged = true;
			}
			if (status == false && isAccess == false && hasLink == false) {	
				statements.updateSwitchTable.setBoolean(1,true);
				statements.updateSwitchTable.setString(2,sliceId);
				statements.updateSwitchTable.setLong(3,switchId);
				statements.updateSwitchTable.setInt(4,0);
				statements.updateSwitchTable.setInt(5,phyPortId);
				FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlCheckIfFirstVLink: updateSwitchTable: update portStatus = "+ true 
						+" with variables => sliceId = " + sliceId + " switchId = " + Long.toHexString(switchId) + " linkId = " + 0
						+" virtPortId = " + phyPortId);
				statements.updateSwitchTable.executeUpdate();
				isChanged = true;
			}
		}
		conn.commit();
		return isChanged;
	}
	
	/**
	 * @param sliceId
	 * @param switchId
	 * @param flowMatch
	 * @param vtConfig 
	 * @return
	 * @throws SQLException 
	 */
	public boolean sqlManageVLinkLLDP(String sliceId, long switchId, Integer virtPortId, byte[] bs) 
	throws SQLException {
		int linkId = 0;
		long nextHop = 0;
		int outVirtPortId = 0;

		statements.selectSwitchTableFourteen.setString(1, sliceId);
		statements.selectSwitchTableFourteen.setLong(2, switchId);
		statements.selectSwitchTableFourteen.setInt(3, virtPortId);
		ResultSet rs = statements.selectSwitchTableFourteen.executeQuery();
		while (rs.next ()) {
			linkId = rs.getInt("linkId");
		}
		if (linkId != 0) {
			statements.selectSwitchTableFifthteen.setString(1, sliceId);
			statements.selectSwitchTableFifthteen.setInt(2, linkId);
			statements.selectSwitchTableFifthteen.setLong(3, switchId);
			rs = statements.selectSwitchTableFifthteen.executeQuery();
			
			while (rs.next ()) {
				nextHop = rs.getLong("switchId");
				outVirtPortId = rs.getInt("virtPortId");
				List<FVEventHandler> handlers = VeRTIGO.getInstance().getHandlersCopy();
				for (FVEventHandler handler : handlers) {
					if (handler.getName().contains("slicer_") && handler.getName().contains(sliceId) &&
							handler.getName().contains(FlowSpaceUtil.dpidToString(nextHop))) {
						if (outVirtPortId != 0 && nextHop != 0) {
							VTLLDPEvent event = new VTLLDPEvent(null,handler,outVirtPortId,bs);
							try {
								handler.handleEvent(event);
							} catch (UnhandledEvent e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}	
							break;
						}
					}
				}	
				
			}
		}
		
		// TODO Auto-generated method stub
		return false;
	}
}
