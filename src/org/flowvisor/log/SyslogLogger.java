/**
 *
 */
package org.flowvisor.log;

import org.flowvisor.config.ConfigError;
import org.flowvisor.config.FVConfig;
import org.flowvisor.events.FVEventHandler;
import org.flowvisor.log.Syslog.Facility;

/**
 * @author capveg
 *
 */
public class SyslogLogger implements FVLogInterface {

	Syslog syslog;
	static public Facility DEFAULT_LOGGING_FACILITY = Facility.LOG_LOCAL7;
	static public String DEFAULT_LOGGING_IDENT = "flowvisor";

	Facility facility;
	String ident;

	public SyslogLogger() {
		this.facility = DEFAULT_LOGGING_FACILITY;
		this.ident = DEFAULT_LOGGING_IDENT;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.flowvisor.log.FVLogInterface#init()
	 */
	@Override
	public boolean init() {
		boolean changedConfig = false;
		try {
			String fac = FVConfig.getString(FVConfig.LOG_FACILITY);
			this.facility = Syslog.Facility.valueOf(fac);
			if (this.facility == null) {
				this.facility = DEFAULT_LOGGING_FACILITY;
				System.err
						.println("Invalid logging facitily: failing back to default: '"
								+ fac + "'");
			}
		} catch (Exception e) {
			try {
				FVConfig.setString(FVConfig.LOG_FACILITY, this.facility
						.toString());
				changedConfig = true;
			} catch (ConfigError e1) {
				System.err.println("Failed to set " + FVConfig.LOG_FACILITY
						+ " to '" + this.facility + ": " + e1);
			}

		}
		try {
			this.ident = FVConfig.getString(FVConfig.LOG_IDENT);
		} catch (Exception e) {
			try {
				FVConfig.setString(FVConfig.LOG_IDENT, this.ident);
				changedConfig = true;
			} catch (ConfigError e1) {
				System.err.println("Failed to set " + FVConfig.LOG_IDENT
						+ " to '" + this.ident + ": " + e1);
			}

		}
		this.syslog = new Syslog(facility, ident);
		this.syslog.log(Syslog.Priority.LOG_INFO, "started flowvisor syslog");
		return changedConfig;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.flowvisor.log.FVLogInterface#log(org.flowvisor.log.LogLevel,
	 * long, org.flowvisor.events.FVEventHandler, java.lang.String)
	 */
	@Override
	public void log(LogLevel level, long time, FVEventHandler source, String msg) {
		if (level == LogLevel.MOBUG)
			return;
		if (source != null)
			syslog.log(level.getPriority(), String.format("%5s", level
					.toString())
					+ "-" + source.getName() + ": " + msg);
		else
			syslog.log(level.getPriority(), level.toString() + ": " + msg);
	}

}
