package org.flowvisor.log;

/**
 * The Java-side wrapper around the syslog-jni library
 *
 * @author capveg
 *
 */

public class Syslog {
	public enum Priority {
		LOG_EMERG(0), /* system is unusable */
		LOG_ALERT(1), /* action must be taken immediately */
		LOG_CRIT(2), /* critical conditions */
		LOG_ERR(3), /* error conditions */
		LOG_WARNING(4), /* warning conditions */
		LOG_NOTICE(5), /* normal but significant condition */
		LOG_INFO(6), /* informational */
		LOG_DEBUG(7); /* debug-level messages */

		int value;

		Priority(int val) {
			this.value = val;
		}

		int getValue() {
			return this.value;
		}

	}

	public enum Facility {
		LOG_KERN(0 << 3), /* kernel messages */
		LOG_USER(1 << 3), /* random user-level messages */
		LOG_MAIL(2 << 3), /* mail system */
		LOG_DAEMON(3 << 3), /* system daemons */
		LOG_AUTH(4 << 3), /* security/authorization messages */
		LOG_SYSLOG(5 << 3), /* messages generated internally by syslogd */
		LOG_LPR(6 << 3), /* line printer subsystem */
		LOG_NEWS(7 << 3), /* network news subsystem */
		LOG_UUCP(8 << 3), /* UUCP subsystem */
		LOG_CRON(9 << 3), /* clock daemon */
		LOG_AUTHPRIV(10 << 3), /* security/authorization messages (private) */
		LOG_FTP(11 << 3), /* ftp daemon */
		/* other codes through 15 reserved for system use */
		LOG_LOCAL0(16 << 3), /* reserved for local use */
		LOG_LOCAL1(17 << 3), /* reserved for local use */
		LOG_LOCAL2(18 << 3), /* reserved for local use */
		LOG_LOCAL3(19 << 3), /* reserved for local use */
		LOG_LOCAL4(20 << 3), /* reserved for local use */
		LOG_LOCAL5(21 << 3), /* reserved for local use */
		LOG_LOCAL6(22 << 3), /* reserved for local use */
		LOG_LOCAL7(23 << 3); /* reserved for local use */

		int value;

		private Facility(int value) {
			this.value = value;
		}

		int getValue() {
			return this.value;
		}

	}

	static {
		String dir = System.getProperty("org.flowvisor.install_dir");
		if (dir == null) {
			throw new UnsatisfiedLinkError(
					"Property 'org.flowvisor.install_dir' not set; can't find libsyslog_jni.so");
		}
		System.load(dir + "/libsyslog_jni.so");
	}

	private final String ident;
	private final Facility facility;

	native void open(int facility, String ident);

	native void log(int priority, String msg);

	public void log(Priority priority, String msg) {
		this.log(priority.getValue(), msg);
	}

	public Syslog(Facility facility, String ident) {
		this.facility = facility;
		this.ident = ident;
		open(this.facility.getValue(), this.ident);
	}

	static public void main(String args[]) {
		if (args.length == 0) {
			System.err.println("Usage: Syslog LOG_priority <msg>");
			System.exit(1);
		}
		Syslog syslog = new Syslog(Facility.LOG_USER, "flowvisor");
		Priority priority = Priority.valueOf(args[0]);
		if (priority == null) {
			System.err.println("Invalid priority :" + args[0]);
			System.exit(1);
		}
		syslog.log(priority, args[1]);
	}
}
