/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.util;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Date;

/**
 * OS-specific utilities
 */
public class OsUtil {
	/**
	 * Are we running on windows?
	 *
	 * @return true if we're on Windows
	 */
	public static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().indexOf("windows") > -1;
	}

	/**
	 * Find the (default) Tomcat log path.
	 *
	 * @return the (default) Tomcat log path.
	 */
	public static String getTomcat5LogPath() {
		String path;
		if (isWindows()) {
			String logDir = "C:\\Program Files (x86)\\Apache Software Foundation\\Tomcat 5.5\\logs";
			path = logDir + "\\stdout_" + DateUtil.getSortableDateString(new Date(), false)
					+ ".log";
		} else {
			String logDir = "/var/log/tomcat5";
			path = logDir + "/catalina.out";
		}
		return path;
	}

	/**
	 * Returns the process ID. Note that this may be system/VM dependent. Tested on Windows with
	 * standard Oracle VM.
	 *
	 * @return the process ID.
	 */
	public static long getProcessId() {
		RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();

		//
		// Get name representing the running Java virtual machine.
		// It returns something like 6460@AURORA. Where the value
		// before the @ symbol is the PID.
		//
		String jvmName = bean.getName();

		//
		// Extract the process ID by splitting the string returned by the
		// bean.getName() method.
		//
		long processId = Long.valueOf(jvmName.split("@")[0]);
		return processId;
	}

}
