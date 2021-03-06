package org.flowvisor.vtopology.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class VTLog {
	
	public static short enabledLog = 0;
	
	private final static short PORTMAPPER = 0x000001;
	private final static short HASHMAP = 0x000002;
	private final static short CONFIGINTERFACE = 0x000004;
	private final static short SQLDB = 0x000008;	
	private final static short STATSDB = 0x000010;
	
	private static SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd-HH:mm:ss:SSS");
	
	public static void VTPortMapper(String text) {
		if((enabledLog & PORTMAPPER) > 0) {
			text = "VTPORTMAPPER-" + formatter.format(new Date()) + ": "  + text;
			System.out.println(text);
		}
	}
	
	public static void VTHashMap(String text) {
		if((enabledLog & HASHMAP) > 0) {
			text = "VTHASHMAP-" + formatter.format(new Date()) + ": "  + text;
			System.out.println(text);
		}
	}
	
	public static void VTSqlDb(String text) {
		if((enabledLog & SQLDB) > 0) {
			text = "VTSQLDB-" + formatter.format(new Date()) + ": "  + text;
			System.out.println(text);
		}
	}
	
	public static void VTConfigInterface(String text) {	
		if((enabledLog & CONFIGINTERFACE) > 0) {
			text = "VTCONFIGINTERFACE-" + formatter.format(new Date()) + ": "  + text;
			System.out.println(text);
		}
	}
	
	public static void VTStatsDb(String text) {		
		if((enabledLog & STATSDB) > 0) {
			text = "VTSTATSDB-" + formatter.format(new Date()) + ": "  + text;
			System.out.println(text);
		}
	}

}
