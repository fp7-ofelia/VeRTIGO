/**
 * 
 */
package org.flowvisor.vtopology.topology_configurator;

import java.sql.*;

import org.flowvisor.FlowVisor;
import org.flowvisor.config.ConfigError;
import org.flowvisor.events.FVEventHandler;
import org.flowvisor.events.VTEvent;
import org.flowvisor.exceptions.UnhandledEvent;
import org.flowvisor.flows.FlowSpaceUtil;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.openflow.protocol.OFPort;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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
		PreparedStatement selectFlowTableOne;
		PreparedStatement deleteFlowTableOne;
		PreparedStatement deleteFlowTableTwo;
		PreparedStatement deleteFlowTableThree;
		PreparedStatement deleteFlowTableFour;
		PreparedStatement insertFlowTable;
	
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
		PreparedStatement deleteLinkTableOne;
		PreparedStatement deleteLinkTableTwo;
		PreparedStatement insertLinkTable;
		
		PreparedStatement selectLeaseTableOne;
		PreparedStatement selectLeaseTableTwo;
		PreparedStatement insertLeaseTable;
		
		PreparedStatement selectActiveFlowTableOne;
		PreparedStatement selectActiveFlowTableTwo;
		PreparedStatement deleteActiveFlowTableOne;
		PreparedStatement deleteActiveFlowTableTwo;
		PreparedStatement insertActiveFlowTableOne;
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

		// Create flow table
		stmt.execute ("DROP TABLE IF EXISTS flowTable");
	    stmt.execute ("CREATE TABLE flowTable (" +
	    		"sliceId VARCHAR(40) NOT NULL," +
	    		"linkId INT NOT NULL," +
	    		"flowMatch VARCHAR(200) NOT NULL," +
	    		"nextHop BIGINT NOT NULL," +
	    		"isFlowMod BOOLEAN NOT NULL," +
	    		"idleTO INT NOT NULL," +
	    		"hardTO INT NOT NULL," +
	    		"PRIMARY KEY (sliceId,linkId,flowMatch,nextHop))");
	    stmt.execute ("CREATE INDEX flowIndex ON flowTable (sliceId, linkId, nextHop)");
	    stmt.execute ("CREATE INDEX flowIndex2 ON flowTable (sliceId, flowMatch, nextHop)");
	    //stmt.execute ("CREATE INDEX flowIndexProva ON flowTable (sliceId, nextHop)");
		
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
	    
	    stmt.execute ("DROP TABLE IF EXISTS activeFlowTable");
	    stmt.execute ("CREATE TABLE activeFlowTable (" +
	    		"sliceId VARCHAR(40) NOT NULL," +
	    		"linkId INT NOT NULL," +
	    		"flowMatch VARCHAR(200) NOT NULL," +
	    		"switchId BIGINT NOT NULL," +
	    		"PRIMARY KEY (sliceId,linkId,flowMatch,switchId))");
	    
	    
	    //Variables: sliceId, nextHop, flowMatch
	    statements.selectFlowTableOne = conn.prepareStatement("SELECT linkId, isFlowMod, idleTO, hardTO FROM flowTable " +
				"WHERE sliceId = ? and nextHop = ? and flowMatch = ? LIMIT 1");
		
		//Variables: sliceId, nextHop, flowMatch, linkId
	    statements.deleteFlowTableOne = conn.prepareStatement("DELETE FROM flowTable " +
				"WHERE sliceId = ? and nextHop = ? and flowMatch = ? and linkId = ? ");
		
	    //Variables: sliceId, switchId, phyPortId, virtPortId, accessPort, linkId, endPoint, status
	    statements.insertSwitchTable = conn.prepareStatement("INSERT INTO switchTable " +
	    		"VALUES (?,?,?,?,?,?,?,?)");
	    
		//Variables: sliceId, switchId, linkId
		statements.selectSwitchTableOne = conn.prepareStatement("SELECT phyPortId FROM switchTable " +
				"WHERE sliceId = ? and switchId = ? and linkId = ? and endPoint = false and status = true");
		
		//Variables: sliceId, linkId, switchId
		statements.selectSwitchTableTwo = conn.prepareStatement("SELECT endPoint FROM switchTable " +
				"WHERE sliceId = ? and switchId = ? and linkId = ? and status = true ORDER BY phyPortId LIMIT 1");
		
		//Variables: sliceId, linkId, flowMatch, nextHop, isFlowMod, idleTO, hardTO
		statements.insertFlowTable = conn.prepareStatement("INSERT INTO flowTable " +
				"VALUES (?,?,?,?,?,?,?)");

		//Variables: sliceId
		statements.selectSwitchTableThree = conn.prepareStatement("SELECT linkId FROM linkTable " +
				"WHERE sliceId = ? ORDER BY linkId DESC LIMIT 1");
		
		//Variables: sliceId
		statements.deleteFlowTableTwo = conn.prepareStatement("DELETE FROM flowTable " +
				"WHERE sliceId = ?");

		//Variables: sliceId
		statements.deleteSwitchTableOne = conn.prepareStatement("DELETE FROM switchTable " +
				"WHERE sliceId = ? and status = true");
		
		//Variables: sliceId
		statements.deleteBufferTableOne = conn.prepareStatement("DELETE FROM bufferTable " +
				"WHERE sliceId = ?");
		
		//Variables: sliceId
		statements.deleteLinkTableOne = conn.prepareStatement("DELETE FROM linkTable " +
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
		
		//Variables: sliceId, linkId, switchId, outPortId
		statements.selectLinkTableOne = conn.prepareStatement("SELECT nextHop FROM linkTable " +
				"WHERE sliceId = ? and linkId = ? and switchId = ? and outPortId = ? LIMIT 1");

		//Variables: sliceId, switchId
		statements.selectSwitchTableFour = conn.prepareStatement("SELECT phyPortId, virtPortId, endPoint FROM switchTable " +
				"WHERE sliceId = ? and switchId = ? and status = true ORDER BY phyPortId");
		
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
		
		//Variables: sliceId, switchId, outPortId
		statements.selectLinkTableTwo = conn.prepareStatement("SELECT nextHop FROM linkTable " +
				"WHERE sliceId = ? and switchId = ? and outPortId = ? LIMIT 1");
		
		//Variables: sliceId, linkId, switchId, nextHop
		statements.selectSwitchTableTen = conn.prepareStatement("SELECT status FROM switchTable " +
				"WHERE sliceId = ? and linkId = ? and switchId != ? and switchId != ? and endPoint = false"); 
		
		//Variables: sliceId, switchId, linkId, phyPortId
		statements.updateSwitchTable = conn.prepareStatement("UPDATE switchTable SET status = ? " +
				"WHERE sliceId = ? and switchId = ? and linkId = ? and virtPortId = ?"); 
		
		//Variables: sliceId, switchId
		statements.selectSwitchTableEleven = conn.prepareStatement("SELECT virtPortId FROM switchTable " +
				"WHERE sliceId = ? and switchId = ? ORDER BY virtPortId DESC LIMIT 1"); 
		
		//Variables: sliceId, linkId
		statements.selectSwitchTableTwelve = conn.prepareStatement("SELECT switchId, phyPortId, virtPortId, endPoint FROM switchTable " +
				"WHERE sliceId = ? and linkId = ?"); 
		
		//Variables: sliceId, linkId
	    statements.deleteFlowTableThree = conn.prepareStatement("DELETE FROM flowTable " +
				"WHERE sliceId = ? and linkId = ?");
	    
	  //Variables: sliceId, flowMatch
	    statements.deleteFlowTableFour = conn.prepareStatement("DELETE FROM flowTable " +
				"WHERE sliceId = ? and flowMatch = ?");
		
	    //Variables: sliceId, linkId
		statements.deleteSwitchTableTwo = conn.prepareStatement("DELETE FROM switchTable " +
				"WHERE sliceId = ? and linkId = ?");
		
		//Variables: sliceId, linkId
		statements.deleteLinkTableTwo = conn.prepareStatement("DELETE FROM linkTable " +
				"WHERE sliceId = ? and linkId = ?");
		
		//Variables: sliceId, linkId, switchId, phyPortId, nextHop
		statements.insertLinkTable = conn.prepareStatement("INSERT INTO linkTable " +
		"VALUES (?,?,?,?,?)");
		
		//Variables: sliceId, linkName
		statements.selectLeaseTableOne = conn.prepareStatement("SELECT linkId FROM leaseTable " +
				"WHERE sliceId = ? and linkName = ? LIMIT 1"); 
		
		//Variables: sliceId, linkId, switchId, phyPortId
		statements.selectLeaseTableTwo = conn.prepareStatement("SELECT virtPortId FROM leaseTable " +
				"WHERE sliceId = ? and linkId = ? and switchId = ? and phyPortId = ?"); 
		
		//Variables: sliceId, linkName, linkId, switchId, phyPortId, virtPortId
		statements.insertLeaseTable = conn.prepareStatement("INSERT INTO leaseTable " +
		"VALUES (?,?,?,?,?,?)");
		
		statements.selectActiveFlowTableOne = conn.prepareStatement("SELECT switchId, flowMatch FROM activeFlowTable " +
				"WHERE sliceId = ? and linkId = ? ORDER BY switchId");
		
		statements.selectActiveFlowTableTwo = conn.prepareStatement("SELECT flowMatch FROM activeFlowTable " +
		"WHERE sliceId = ? and linkId = ? and flowMatch = ? and switchId = ?");
		
		statements.deleteActiveFlowTableOne = conn.prepareStatement("DELETE FROM activeFlowTable " +
				"WHERE sliceId = ? and linkId = ?");
		
		statements.deleteActiveFlowTableTwo = conn.prepareStatement("DELETE FROM activeFlowTable " +
		"WHERE sliceId = ? and flowMatch = ? and switchId = ?");
		
		statements.insertActiveFlowTableOne = conn.prepareStatement("INSERT INTO activeFlowTable " +
		"VALUES (?,?,?,?)");
		
		//Variables: sliceId, switchId
		statements.selectSwitchTableThirteen = conn.prepareStatement("SELECT virtPortId, accessPort, status FROM switchTable " +
				"WHERE sliceId = ? and switchId = ? and phyPortId = ?");
		
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
		vtConfig.GetConfigVTTree();
		
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
 * @authors roberto.doriguzzi, matteo.gerola
 * @info This function is used when a FeatureReply packet arrives. It initialises the db for the switch, adding virtual 
 * ports (access or link) associated to the physical ports provided
 * @exception SQLException
 * @exception RuntimeException
 * @exception ConfigError
 */
	@SuppressWarnings("unchecked")
	public boolean sqlDbInitSwitchInfo(String sliceId, long switchId, VTConfigInterface vtConfig) 
	throws SQLException, RuntimeException, ConfigError {
		Statement stmt = null;
		ResultSet rs = null;
		
		//create-initialize the variables
		boolean ret = false;
		int virtPortId = 101;
		boolean endPoint = true;
		LinkedList<Integer> accessPortList = new LinkedList<Integer>();
		HashMap<Integer,HashMap<Integer,Boolean>> linkPortMap = 
			new HashMap<Integer,HashMap<Integer,Boolean>>();
		stmt = conn.createStatement();
		String sql = null;
		
		int phyPort = 0;
		int tmpPhyPort = 0;
		LinkedList<Integer> tmpVirtPortList = new LinkedList<Integer>();
		
		//get slice info from config file
		VTSlice sliceInfo = new VTSlice();
		sliceInfo.GetVTSliceConfig(sliceId);
		
		//convert dpid in string and get the link list for this slice
		String switchIdString = FlowSpaceUtil.dpidToString(switchId);
		sliceInfo.GetLinksInfo(switchIdString, linkPortMap);
		
		//add port to list only if this port has no link within the slice
		for (int currentPort:vtConfig.phyPortList) {
			if (!linkPortMap.containsKey(currentPort)) accessPortList.add(currentPort);
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
		
		//get the map of physical and virtual ports from the table
		stmt = conn.createStatement();
		sql="SELECT phyPortId,virtPortId FROM switchTable WHERE sliceId = '" + sliceId + "' and virtPortId != 0 and switchId = " + 
		switchId + " and status = true ORDER BY phyPortId";
		rs = stmt.executeQuery(sql);
		//maybe i can do it better!
		while (rs.next ()){
			phyPort = rs.getInt("phyPortId");
			if (tmpPhyPort == 0) tmpPhyPort = phyPort;
			if (phyPort == tmpPhyPort) tmpVirtPortList.add(rs.getInt("virtPortId"));
			else {
				LinkedList<Integer> virtPortList = new LinkedList<Integer>();
				virtPortList = (LinkedList<Integer>) tmpVirtPortList.clone();
				vtConfig.phyToVirtPortMap.put(tmpPhyPort, virtPortList);
				tmpVirtPortList.clear();
				tmpPhyPort = phyPort;
				tmpVirtPortList.add(rs.getInt("virtPortId"));
			}
			ret = true;
		}
		if (tmpPhyPort != 0 && tmpVirtPortList.size() > 0) {
			LinkedList<Integer> virtPortList = new LinkedList<Integer>();
			virtPortList = (LinkedList<Integer>) tmpVirtPortList.clone();
			vtConfig.phyToVirtPortMap.put(tmpPhyPort, virtPortList);
		}
		rs.close ();
		stmt.close();
		return ret;
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
				//capire se anche qui vanno eliminati i flussi...
				deleteVirtualLink (sliceId, currentLink, status, vtConfig, activeFlows, linkStatusList);
				addVirtualLink (sliceId, currentLink, status, vtConfig, linkStatusList);
				break;
			}
		}
		
		List<FVEventHandler> handlers = FlowVisor.getInstance().getHandlersCopy();
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
						+ " sliceId = " + sliceId + " switchId = " + currentSwitch + " phyPortId = " + currentPort
						 + " virtPortId = " + virtPortId + " isAccess = false linkId = " + currentLink.linkId
						 + " endPoint = " + endPoint + " status = true");
				statements.insertSwitchTable.execute();
		
				statements.insertLinkTable.setString(1, sliceId);
				statements.insertLinkTable.setInt(2, currentLink.linkId);
				statements.insertLinkTable.setLong(3, currentSwitch);
				statements.insertLinkTable.setInt(4, currentPort);
				statements.insertLinkTable.setLong(5, nextHop);
				FVLog.log(LogLevel.DEBUG, null, "vtopology: addVirtualLink: insertLinkTable: insert => "
						+ " sliceId = " + sliceId + " linkId = " + currentLink.linkId + " switchId = " + currentSwitch
						 + " phyPortId = " + currentPort + " nextHop = " + nextHop);
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
					+ " switchId = " + currentSwitch + " virtPortId = " + virtPortId + " endPoint = " + rs.getBoolean("endPoint"));
			switchportList.add(currentSwitch+"/"+phyPortId);
		}
		
		statements.selectActiveFlowTableOne.setString(1, sliceId);
		statements.selectActiveFlowTableOne.setInt(2, currentLink.linkId);
		FVLog.log(LogLevel.DEBUG, null, "vtopology: deleteVirtualLink: selectActiveFlowTableOne: variables => "
				+ " sliceId = " + sliceId + " linkId = " + currentLink.linkId);
		rs = statements.selectActiveFlowTableOne.executeQuery();
		while (rs.next ()){
			LinkedList<String> flowList = new LinkedList<String>();
			currentSwitch = rs.getLong("switchId");
			flowMatch = rs.getString("flowMatch");
			if (!activeFlows.containsKey(currentSwitch))
				flowList.add(flowMatch);
			else {
				flowList = activeFlows.get(currentSwitch);
				flowList.add(flowMatch);
			}
			activeFlows.put(currentSwitch, flowList);
			
			FVLog.log(LogLevel.DEBUG, null, "vtopology: deleteVirtualLink: selectActiveFlowTableOne: results => "
					+ " switchId = " + currentSwitch + " flowMatch = " + flowMatch);
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
		statements.deleteFlowTableThree.setString(1, sliceId);
		statements.deleteFlowTableThree.setInt(2, currentLink.linkId);
		statements.deleteFlowTableThree.execute();
		FVLog.log(LogLevel.DEBUG, null, "vtopology: deleteVirtualLink: deleteFlowTableThree: delete => "
				+ " sliceId = " + sliceId + " linkId = " + currentLink.linkId);	
		statements.deleteActiveFlowTableOne.setString(1, sliceId);
		statements.deleteActiveFlowTableOne.setInt(2, currentLink.linkId);
		statements.deleteActiveFlowTableOne.execute();
		FVLog.log(LogLevel.DEBUG, null, "vtopology: deleteVirtualLink: deleteActiveFlowTableOne: delete => "
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
 * @return classifierToVirtPortMap = mapping between switchId and virtual ports
 * @exception SQLException
 * @exception RuntimeException
 * @exception ConfigError
 */
	public boolean sqlDbUpdatePortStatus(String sliceId, long switchId, int portId, boolean portStatus, VTConfigInterface vtConfig) 
	throws SQLException {
		ResultSet rs = null;
		boolean ret = true;
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
					+ " sliceId = " + sliceId + " switchId = " + switchId + " phyPortId = " + portId);
			rs = statements.selectLinkTableTwo.executeQuery();
			while (rs.next ()){
				nextHop = rs.getLong("nextHop");
				FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbUpdatePortStatus: selectLinkTableTwo: results => "
						+ " nextHop = " + nextHop);
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
				+ " sliceId = " + sliceId + " switchId = " + switchId + " phyPortId = " + portId + " status = " + currentStatus);
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
		
		vtConfig.classifierToVirtPortMap.put(switchId, virtPortList);
		
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
					if (!vtConfig.classifierToVirtPortMap.containsKey(currentSwitch)) {
						portList.add(virtPortId);
						vtConfig.classifierToVirtPortMap.put(currentSwitch, portList);
					}
					else {
						portList = vtConfig.classifierToVirtPortMap.get(currentSwitch);
						if (!portList.contains(virtPortId))
							portList.add(virtPortId);
						vtConfig.classifierToVirtPortMap.put(currentSwitch, portList);
					}
					FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbUpdatePortStatus: selectSwitchTableNine: results => "
							+ " switchId = " + currentSwitch + " virtPortId = " + virtPortId);
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
				+ " sliceId = " + sliceId + " linkId = " + linkId + " switchId = " + switchId + " nextHop = " + nextHop);
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
				+" with variables => sliceId = " + sliceId + " switchId = " + switchId + " linkId = " + linkId
				+" virtPortId = " + virtPortId);
		statements.updateSwitchTable.executeUpdate();
		return ret;
	}
	
/**
 * @name sqlDbGetSwitchInfo
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function is used when a packet from a switch is directed to a controller. 
 * A list of physical ports is mapped in virtual ports.
 * It also returns informations about the switch position in the link (endPoint, if not => send pkt to the link_broker)
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @param String flowMatch = packet match (L2+L3)
 * @param phyPortList = list of the physical ports of the switch in this slice
 * @return isEndPoint = TRUE if the switch is an end point, FALSE otherwise
 * @return phyToVirtPortMap = mapping between physical and virtual ports (only if isEnd Point == TRUE)
 * @return phyPortId = physical output port (only if isEnd Point == FALSE)
 * @return ret = boolean return code (TRUE: ok, FALSE: error in the function) 
 * @exception SQLException   
 */
	public boolean sqlDbGetSwitchInfo(String sliceId, long switchId, String flowMatch, VTConfigInterface vtConfig) 
	throws SQLException {
		ResultSet rs = null;
		boolean ret = true;
		int linkId = 0;
		
		if (!flowMatch.equals("OFMatch[]")) {
			statements.selectFlowTableOne.setString(1,sliceId);
			statements.selectFlowTableOne.setLong(2,switchId);
			statements.selectFlowTableOne.setString(3,flowMatch);
			FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbGetSwitchInfo: selectFlowTableOne: variables => "
					+ " sliceId = " + sliceId + " switchId = " + switchId + " flowMatch = " + flowMatch);
			rs = statements.selectFlowTableOne.executeQuery();
			while (rs.next ()){
				linkId = rs.getInt("linkId");
				vtConfig.isFlowMod = rs.getBoolean("isFlowMod");
				vtConfig.idleTO = rs.getInt("idleTO");
				vtConfig.hardTO = rs.getInt("hardTO");
				FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbGetSwitchInfo: selectFlowTableOne: results => "
						+ " linkId = " + linkId + " isFlowMod = " + vtConfig.isFlowMod 
						+ " idleTO = " + vtConfig.idleTO + " hardTO = " + vtConfig.hardTO);
			}
			
			if (linkId != 0) {
				statements.deleteFlowTableOne.setString(1, sliceId);
				statements.deleteFlowTableOne.setLong(2, switchId);
				statements.deleteFlowTableOne.setString(3,flowMatch);
				statements.deleteFlowTableOne.setInt(4,linkId);
				FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbGetSwitchInfo: deleteFlowTableOne: variables => "
						+ " sliceId = " + sliceId + " switchId = " + switchId + " flowMatch = " + flowMatch
						+ " linkId = " + linkId);
				statements.deleteFlowTableOne.execute();
			}
			conn.commit();
			rs.close ();
		}
		
		if (!vtConfig.phyPortList.isEmpty()) {
			if (vtConfig.phyPortList.contains((int)OFPort.OFPP_ALL.getValue())) {
				statements.selectSwitchTableFour.setString(1, sliceId);
				statements.selectSwitchTableFour.setLong(2, switchId);
				FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbGetSwitchInfo: selectSwitchTableFour: variables => "
						+ " sliceId = " + sliceId + " switchId = " + switchId);
				sqlGetSwitchInfoRSParsing(statements.selectSwitchTableFour.executeQuery(), vtConfig);
				}
			else {
				for (int currentPort : vtConfig.phyPortList) {
					statements.selectSwitchTableFive.setString(1, sliceId);
					statements.selectSwitchTableFive.setLong(2, switchId);
					statements.selectSwitchTableFive.setInt(3, linkId);
					statements.selectSwitchTableFive.setInt(4, currentPort);
					FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbGetSwitchInfo: selectSwitchTableFive: variables => "
							+ " sliceId = " + sliceId + " switchId = " + switchId + " linkId = " + linkId
							+ " phyPortId = " + currentPort);
					sqlGetSwitchInfoRSParsing(statements.selectSwitchTableFive.executeQuery(), vtConfig);
				}
			}
			
			//If the switch isn't and end point of a link, 
			//find the physical output port to pass this info to the link_broker
			if (vtConfig.isEndPoint == false && linkId != 0 && vtConfig.phyPortList.size() == 1) {
				statements.selectSwitchTableOne.setString(1, sliceId);
				statements.selectSwitchTableOne.setLong(2, switchId);
				statements.selectSwitchTableOne.setInt(3, linkId);
				FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbGetSwitchInfo: selectSwitchTableOne: variables => "
						+ " sliceId = " + sliceId + " switchId = " + switchId + " linkId = " + linkId);
				rs = statements.selectSwitchTableOne.executeQuery();
				while (rs.next ()){
					if (!vtConfig.phyToVirtPortMap.containsKey(rs.getInt("phyPortId"))) {
						vtConfig.phyPortId = rs.getShort("phyPortId");
						FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbGetSwitchInfo: selectFlowTableOne: results => "
								+ " phyPortId = " + vtConfig.phyPortId);
					}
				}
				
				if (vtConfig.phyPortId == 0) 
					ret = false;
				else {
					HashMap<Integer, Long> linkSwitchMap = new HashMap<Integer, Long>();
					linkSwitchMap.put(linkId,sqlGetNextHop(sliceId, linkId, switchId, vtConfig.phyPortId));
					sqlUpdateFlowDb(sliceId, flowMatch, linkSwitchMap, vtConfig);
				}
				rs.close ();
			}			
		}
		else {
			statements.selectSwitchTableTwo.setString(1, sliceId);
			statements.selectSwitchTableTwo.setLong(2, switchId);
			statements.selectSwitchTableTwo.setInt(3, linkId);
			FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbGetSwitchInfo: selectSwitchTableTwo: variables => "
					+ " sliceId = " + sliceId + " switchId = " + switchId + " linkId = " + linkId);
			rs = statements.selectSwitchTableTwo.executeQuery();
			while (rs.next()) {
				vtConfig.isEndPoint = rs.getBoolean("endPoint");
				FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbGetSwitchInfo: selectSwitchTableTwo: results => "
						+ " endPoint = " + vtConfig.isEndPoint);
			}
			rs.close ();
		}
		return ret;
	}

	
	public boolean sqlGetSwitchInfoRSParsing(ResultSet rs, VTConfigInterface vtConfig) 
	throws SQLException {
		boolean ret = true;
		int phyPortId = 0;
		
		while (rs.next ()){
			LinkedList<Integer> virtPortList = new LinkedList<Integer>();
			virtPortList.clear();
			vtConfig.isEndPoint = rs.getBoolean("endPoint");
			phyPortId = rs.getInt("phyPortId");
			if (vtConfig.phyToVirtPortMap.containsKey(phyPortId)) {
				virtPortList = vtConfig.phyToVirtPortMap.get(phyPortId);
				virtPortList.add(rs.getInt("virtPortId"));
				//forse basta cosi, altrimenti devo mettere la riga seguente
				//vtConfig.phyToVirtPortMap.put(phyPortId, virtPortList);	
			}
			else {
				virtPortList.add(rs.getInt("virtPortId"));
				vtConfig.phyToVirtPortMap.put(phyPortId, virtPortList);					
			}
			FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlDbGetSwitchInfo: sqlGetSwitchInfoRSParsing: results => "
					+ " phyPortId = " + phyPortId + " virtPortId = " + rs.getInt("virtPortId") 
					+ " isEndPoint = " + vtConfig.isEndPoint);
		}
		
		rs.close ();
		return ret;
	}
	
	
/**
 * @name sqlGetVirttoPhyPortMap
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function is used to convert a list of virtual ports sent from a controller to a switch
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @param String flowMatch = packet match (L2+L3) 
 * @param virtPortId = input virtual port
 * @param virtPortList = list of the output virtual ports of the switch in this slice
 * @return phyPortId = input physical port     
 * @return virtToPhyPortMap = mapping between virtual ports and physical ports
 * @return ret = boolean return code (TRUE: ok, FALSE: error in the function)
 * @exception SQLException  
 */
	public boolean sqlGetVirttoPhyPortMap(String sliceId, long switchId, String flowMatch, VTConfigInterface vtConfig) 
	throws SQLException {
		boolean ret = true;
		HashMap<Integer, Long> linkSwitchMap = new HashMap<Integer, Long>();
		HashMap<Integer, Integer> linkTmpMap = new HashMap<Integer, Integer>();
		if (vtConfig.virtPortList.contains((int)OFPort.OFPP_ALL.getValue()) || 
				vtConfig.virtPortList.contains((int)OFPort.OFPP_ALL.getValue()) || 
				vtConfig.virtPortList.contains((int)OFPort.OFPP_FLOOD.getValue())) {
				statements.selectSwitchTableSix.setString(1, sliceId);
				statements.selectSwitchTableSix.setLong(2, switchId);
				FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlGetVirttoPhyPortMap: selectSwitchTableSix: variables => "
						+ " sliceId = " + sliceId + " switchId = " + switchId);
				sqlVirttoPhyRSParsing (statements.selectSwitchTableSix.executeQuery(), linkTmpMap, vtConfig);
			}
		else {
			for (int currentPort : vtConfig.virtPortList) {
				statements.selectSwitchTableSeven.setString(1, sliceId);
				statements.selectSwitchTableSeven.setLong(2, switchId);
				statements.selectSwitchTableSeven.setLong(3, currentPort);
				FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlGetVirttoPhyPortMap: selectSwitchTableSeven: variables => "
						+ " sliceId = " + sliceId + " switchId = " + switchId + " virtPortId = " + currentPort);
				sqlVirttoPhyRSParsing (statements.selectSwitchTableSeven.executeQuery(), linkTmpMap, vtConfig);
			}
			if (vtConfig.virtPortId != 0) {
				statements.selectSwitchTableSeven.setString(1, sliceId);
				statements.selectSwitchTableSeven.setLong(2, switchId);
				statements.selectSwitchTableSeven.setLong(3, vtConfig.virtPortId);
				FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlGetVirttoPhyPortMap: selectSwitchTableSeven: variables => "
						+ " sliceId = " + sliceId + " switchId = " + switchId + " virtPortId = " + vtConfig.virtPortId);
				sqlVirttoPhyRSParsing (statements.selectSwitchTableSeven.executeQuery(), linkTmpMap, vtConfig);
			}
		}
		
		Set<Integer> keySetLinkTmpMap = linkTmpMap.keySet();
		for(int currentLink:keySetLinkTmpMap)
			linkSwitchMap.put(currentLink, sqlGetNextHop(sliceId, currentLink, switchId, linkTmpMap.get(currentLink)));

		if (!flowMatch.equals("OFMatch[]")) {
			sqlUpdateFlowDb(sliceId, flowMatch, linkSwitchMap, vtConfig);
			insertActiveFlow(sliceId, flowMatch, linkSwitchMap, switchId);
		}
		return ret;
	}

	
	public boolean sqlVirttoPhyRSParsing(ResultSet rs, HashMap<Integer, Integer> linkTmpMap, VTConfigInterface vtConfig) 
	throws SQLException {
		boolean ret = true;
		int virtPortId;
		while (rs.next ()){
			virtPortId = rs.getInt("virtPortId");
			if (virtPortId == vtConfig.virtPortId) {
				vtConfig.phyPortId = (short)rs.getInt("phyPortId");
				FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlGetVirttoPhyPortMap: sqlVirttoPhyRSParsing: results => "
						+ " Match virtPortId = " + virtPortId + " Match phyPortId = " + vtConfig.phyPortId);
			}
			else {	
				vtConfig.virtToPhyPortMap.put(virtPortId, rs.getInt("phyPortId"));
				FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlGetVirttoPhyPortMap: sqlVirttoPhyRSParsing: results => "
						+ " Out virtPortId = " + virtPortId + " Out phyPortId = " + rs.getInt("phyPortId"));
				if (rs.getInt("linkId") != 0) {
					linkTmpMap.put(rs.getInt("linkId"), rs.getInt("phyPortId"));
					FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlGetVirttoPhyPortMap: sqlVirttoPhyRSParsing: results => "
							+ " Out linkId = " + rs.getInt("linkId"));
				}
			}
		}
		rs.close ();
		return ret;
	}
	
/**
 * @name sqlUpdateFlowDb
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function inserts in the flowTable a list of new flows, with unique keys (sliceId, linkId, nextHop)
 * @param String sliceId = name of the slice 
 * @param String flowMatch = packet match (L2+L3)
 * @param Boolean isFlowMod = if TRUE, the flowMatch belong to a flowMod action, is FALSE to an output action
 * @param int idleTO = flowMod idle timeout. If isFlowMod is TRUE, 
 * this value is used by the linkBroker when it creates the flowMod action
 * @param int hardTO = flowMod hard timeout. If isFlowMod is TRUE, 
 * this value is used by the linkBroker when it creates the flowMod action
 * @param HashMap<Integer,Long> linkSwitchMap = map between a list of links and related nextHop switches      
 * @exception SQLException
 */
	private void sqlUpdateFlowDb(String sliceId, String flowMatch, HashMap<Integer,Long> linkSwitchMap, VTConfigInterface vtConfig) 
	throws SQLException {		
		Set<Integer> keySetPortMap = linkSwitchMap.keySet();
		for (int linkId : keySetPortMap) {
			statements.insertFlowTable.setString(1, sliceId);
			statements.insertFlowTable.setInt(2, linkId);
			statements.insertFlowTable.setString(3, flowMatch);
			statements.insertFlowTable.setLong(4, linkSwitchMap.get(linkId));
			statements.insertFlowTable.setBoolean(5, vtConfig.isFlowMod);
			statements.insertFlowTable.setInt(6, vtConfig.idleTO);
			statements.insertFlowTable.setInt(7, vtConfig.hardTO);
			FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlUpdateFlowDb: insertFlowTable: insert => "
					+ " sliceId = " + sliceId + " linkId = " + linkId + " nextHop = " + linkSwitchMap.get(linkId) 
					+ " flowMatch = " + flowMatch + " isFlowMod = " + vtConfig.isFlowMod
					 + " idleTO = " + vtConfig.idleTO + " hardTO = " + vtConfig.hardTO);
			statements.insertFlowTable.executeUpdate();
		}
		conn.commit();	
	}

	
	private void insertActiveFlow(String sliceId, String flowMatch, HashMap<Integer,Long> linkSwitchMap, long switchId) 
	throws SQLException {		
		ResultSet rs;
		int counter = 0;
		Set<Integer> keySetPortMap = linkSwitchMap.keySet();
		for (int linkId : keySetPortMap) {
			statements.selectActiveFlowTableTwo.setString(1, sliceId);
			statements.selectActiveFlowTableTwo.setInt(2, linkId);
			statements.selectActiveFlowTableTwo.setString(3, flowMatch);
			statements.selectActiveFlowTableTwo.setLong(4, switchId);
			FVLog.log(LogLevel.DEBUG, null, "vtopology: insertActiveFlow: selectActiveFlowTableTwo: variables => "
					+ " sliceId = " + sliceId + " switchId = " + switchId + " linkId = " + linkId, " flowMatch = "+ flowMatch);
			rs = statements.selectActiveFlowTableTwo.executeQuery();
			while (rs.next())
				counter++;
			rs.close ();

			if (counter == 0) {
				statements.insertActiveFlowTableOne.setString(1, sliceId);
				statements.insertActiveFlowTableOne.setInt(2, linkId);
				statements.insertActiveFlowTableOne.setString(3, flowMatch);
				statements.insertActiveFlowTableOne.setLong(4, switchId);
				statements.insertActiveFlowTableOne.executeUpdate();
					FVLog.log(LogLevel.DEBUG, null, "vtopology: insertActiveFlow: insertActiveFlowTableOne: insert => "
							+ " sliceId = " + sliceId + " linkId = " + linkId + " flowMatch = " + flowMatch
							 + " switchId = " + switchId);
			}
		}
		conn.commit();	
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
	public boolean sqlGetNewLinkId(String sliceId, String linkName, VTConfigInterface vtConfig) 
	throws SQLException {
		boolean ret = true;

		vtConfig.linkId = sqlGetLeaseLinkInfo(sliceId, linkName);
		
		if (vtConfig.linkId == 0) {
			statements.selectSwitchTableThree.setString(1, sliceId);
			ResultSet rs = statements.selectSwitchTableThree.executeQuery();
			while (rs.next())
				vtConfig.linkId = rs.getInt("linkId")+1;
			rs.close ();
		}
		if (vtConfig.linkId == 0)
			vtConfig.linkId = 1;
		return ret;
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
 * @info This function remove all the information stored in the database regarding a flow, 
 * when a flow_rem arrived to flowvisor
 * @param String sliceId = name of the slice 
 * @param long switchId = dpid of the switch
 * @param String flowMatch = packet match (L2+L3)  
 * @exception SQLException
 * @return ret = boolean return code (TRUE: ok, FALSE: error in the function)  
 */
	public boolean sqlRemoveFlowInfo (String sliceId, long switchId, String flowMatch) 
	throws SQLException {
		boolean ret = true;
		statements.deleteFlowTableFour.setString(1, sliceId);
		statements.deleteFlowTableFour.setString(2, flowMatch);
		statements.deleteFlowTableFour.execute();
		FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlRemoveFlowInfo: deleteFlowTableFour: delete => "
				+ " sliceId = " + sliceId + " flowMatch = " + flowMatch);	
		statements.deleteActiveFlowTableTwo.setString(1, sliceId);
		statements.deleteActiveFlowTableTwo.setString(2, sliceId);
		statements.deleteActiveFlowTableTwo.setLong(3, switchId);
		statements.deleteActiveFlowTableTwo.execute();
		FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlRemoveFlowInfo: deleteActiveFlowTableTwo: delete => "
				+ " sliceId = " + sliceId + " flowMatch = " + flowMatch + " switchId = " + switchId);	
		conn.commit();		
		return ret;
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
		statements.deleteFlowTableTwo.setString(1, sliceId);
		statements.deleteFlowTableTwo.execute();
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
				 + " switchId = " + switchId + " phyPortId = " + phyPortId + "virtPortId = " + virtPortId);
		conn.commit();
		return ret;
	}
	
	
	public int sqlGetLeaseLinkInfo(String sliceId, String linkName) 
	throws SQLException {
		int linkId = 0;
		statements.selectLeaseTableOne.setString(1, sliceId);
		statements.selectLeaseTableOne.setString(2, linkName);
		FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlGetLeaseInfo: selectLeaseTableOne: variables => "
				+ " sliceId = " + sliceId + " linkName = " + linkName);
		ResultSet rs = statements.selectLeaseTableOne.executeQuery();
		while (rs.next()) {
			linkId = rs.getInt("linkId");
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
				+ " sliceId = " + sliceId + " linkId = " + linkId + " switchId = " + switchId + " phyPortId = " + phyPortId);
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
 * @name StorePktInFlowInfo
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function is used to store a flowMatch associated with an unique bufferId
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @param String flowMatch = packet match (L2+L3) 
 * @param int bufferId = packet In identifier
 * @exception SQLException
 */
	public boolean sqlStorePktInFlowInfo(String flowMatch, int bufferId, String sliceId, long switchId) 
	throws SQLException {
		boolean result = true;
		statements.insertBufferTable.setString(1, flowMatch);
		statements.insertBufferTable.setInt(2, bufferId);
		statements.insertBufferTable.setString(3, sliceId);
		statements.insertBufferTable.setLong(4, switchId);
		statements.insertBufferTable.execute();
		conn.commit();
		return result;
	}


/**
 * @name sqlGetPktInFlowInfo
 * @authors roberto.doriguzzi matteo.gerola
 * @info This function get a flowMatch from a bufferId
 * @param String sliceId = name of the slice
 * @param long switchId = dpid of the switch
 * @param int bufferId = packet In identifier
 * @return String flowMatch = packet match (L2+L3) 
 * @exception SQLException
 */
	public boolean sqlGetPktInFlowInfo(int bufferId, String sliceId, long switchId, VTConfigInterface vtConfig) 
	throws SQLException {
		boolean ret = true;
		
		statements.selectBufferTable.setInt(1, bufferId);
		statements.selectBufferTable.setString(2, sliceId);
		statements.selectBufferTable.setLong(3, switchId);
		ResultSet rs = statements.selectBufferTable.executeQuery();
		while (rs.next ())
			vtConfig.flowMatch.fromString(rs.getString("flowMatch"));
		rs.close ();
		
		statements.deleteBufferTableTwo.setInt(1, bufferId);
		statements.deleteBufferTableTwo.setString(2, sliceId);
		statements.deleteBufferTableTwo.setLong(3, switchId);
		statements.deleteBufferTableTwo.execute();
		conn.commit();		
		return ret;
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
	public long sqlGetNextHop(String sliceId, int linkId, long switchId, int outPortId) 
	throws SQLException {
		long nextHop = 0;
		statements.selectLinkTableOne.setString(1, sliceId);
		statements.selectLinkTableOne.setInt(2, linkId);
		statements.selectLinkTableOne.setLong(3, switchId);
		statements.selectLinkTableOne.setInt(4, outPortId);
		FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlGetNextHop: selectLinkTableOne: variables => "
				+ " sliceId = " + sliceId + " switchId = " + switchId + " linkId = " + linkId + " outPortId = " + outPortId);
		ResultSet rs = statements.selectLinkTableOne.executeQuery();
		while (rs.next ()) {
			nextHop = rs.getLong("nextHop");
			FVLog.log(LogLevel.DEBUG, null, "vtopology: sqlGetNextHop: selectLinkTableOne: results => "
					+ " nextHop = " + nextHop);
		}
		
		return nextHop;
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
				+ " sliceId = " + sliceId + " switchId = " + switchId + " phyPortId = " + phyPortId);
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
						+" with variables => sliceId = " + sliceId + " switchId = " + switchId + " linkId = " + 0
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
						+" with variables => sliceId = " + sliceId + " switchId = " + switchId + " linkId = " + 0
						+" virtPortId = " + phyPortId);
				statements.updateSwitchTable.executeUpdate();
				isChanged = true;
			}
		}
		conn.commit();
		return isChanged;
	}
	
}
