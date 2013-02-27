/**
 * 
 */
package org.flowvisor.vtopology.topology_configurator;

import org.flowvisor.config.ConfigError;
import org.flowvisor.config.FVConfig;

/**
 * @author gerola
 *
 */
public class VTDBConfig {
	public String dbType;
	public String dbAddress;
	public int dbPort;
	public String dbUser;
	public String dbPasswd;
	
	public VTDBConfig() {
		this.dbType=null;
		this.dbAddress=null;
		this.dbPort=0;
		this.dbUser=null;
		this.dbPasswd=null;
	}
	
	public void GetDBConfig() throws RuntimeException {
		try {
			this.dbType=FVConfig.getString(FVConfig.DB_TYPE);
			this.dbAddress=FVConfig.getString(FVConfig.DB_IP);
			this.dbPort=FVConfig.getInt(FVConfig.DB_PORT);
			this.dbUser=FVConfig.getString(FVConfig.DB_USER);
			this.dbPasswd=FVConfig.getString(FVConfig.DB_PASSWD);
		} catch (ConfigError e) {
			this.dbType=null;
			this.dbAddress="127.0.0.1";
			this.dbPort=3306;
			this.dbUser="flowvisor";
			this.dbPasswd="flowvisor";
		}	
	}
	
}
