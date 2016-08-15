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

import java.util.Enumeration;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

/**
 * Utilities for log4j logging.
 */
public class LogUtil {

	private LogUtil() {
	}

	/**
	 * Will initialize a simple console appender
	 * the root logger has no appenders set yet.
	 *
	 * This gives you a reasonable default if you don't want to create a
	 * log4j.properties.
	 *
	 * @param level level to use for the console appender, if created
	 */
	public static void initLog4jIfNotAlready(Level level) {
		Enumeration<?> allAppenders = Logger.getRootLogger().getAllAppenders();
		if (!allAppenders.hasMoreElements()) {
			// No appender yet; create simple console appender
			ConsoleAppender consoleAppender = new ConsoleAppender();
			//consoleAppender.setLayout(new PatternLayout("%r [%t] %p %c %x - %m%n"));
			consoleAppender.setLayout(new PatternLayout("%8r [%t] %-35c{2} %x %-5p %m%n"));
			consoleAppender.setTarget("System.out");
			consoleAppender.setThreshold(level);
			consoleAppender.activateOptions();
			Logger.getRootLogger().addAppender(consoleAppender);
		}
	}

	/**
	 * Will initialize a simple console appender at level WARN if
	 * the root logger has no appenders set yet.
	 *
	 * This gives you a reasonable default if you don't want to create a
	 * log4j.properties.
	 */
	public static void initLog4jIfNotAlready() {
		initLog4jIfNotAlready(Level.WARN); // Log warnings and up
	}

}
