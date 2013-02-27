/**
 *
 */
package org.flowvisor.log;

/**
 * Logging Priority Levels (very similar to syslog) Sorted in descending order
 * of importance.
 *
 * @author capveg
 *
 */
public enum LogLevel {
	FATAL(Syslog.Priority.LOG_EMERG), // The world is on fire
	CRIT(Syslog.Priority.LOG_CRIT), // Will always want to know
	ALERT(Syslog.Priority.LOG_ALERT), // Will typically want to know
	WARN(Syslog.Priority.LOG_WARNING), // Might want to know cuz it's possibly
	// bad
	INFO(Syslog.Priority.LOG_INFO), // Maybe worth knowing, maybe not -- not bad
	NOTE(Syslog.Priority.LOG_NOTICE),

	DEBUG(Syslog.Priority.LOG_DEBUG), /* Debug */
	MOBUG; // more debugging; rarely worth knowing

	@Override
	public String toString() {
		return this.name();
	}

	Syslog.Priority priority;

	LogLevel(Syslog.Priority priority) {
		this.priority = priority;
	}

	LogLevel() {
		this.priority = null;
	}

	public Syslog.Priority getPriority() {
		return this.priority;
	}
}
