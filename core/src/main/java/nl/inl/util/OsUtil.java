/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.util;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 * OS-specific utilities
 */
public class OsUtil {
	private OsUtil() {
	}

	/**
	 * Are we running on windows?
	 *
	 * @return true if we're on Windows
	 */
	public static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().indexOf("windows") > -1;
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
		long processId = Long.parseLong(jvmName.split("@")[0]);
		return processId;
	}

}
