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

import java.io.File;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.PropertyConfigurator;

/**
 * Utilities for log4j logging.
 */
public class LogUtil {
	/** Has log4j been initialised? */
	private static boolean log4jInitialized = false;

	/**
	 * Initialize the log4j library, according to the specified properties file found somewhere on
	 * the classpath.
	 *
	 * @param resourcePath
	 *            where to find the log4j properties file in the classpath
	 */
	public static void initLog4jFromResource(String resourcePath) {
		if (!log4jInitialized) {
			try {
				Properties properties = PropertiesUtil.getFromResource(resourcePath);
				PropertyConfigurator.configure(properties);
				log4jInitialized = true;
			} catch (Exception e) {
				throw ExUtil.wrapRuntimeException(e);
			}
		}
	}

	/**
	 * Initialize the log4j library, according to the log4j.properties file
	 *
	 * @param configDir
	 *            directory where log4j.properties can be found.
	 */
	public static void initLog4j(File configDir) {
		if (!log4jInitialized) {
			try {
				Properties p = PropertiesUtil.readFromFile(new File(configDir, "log4j.properties"));
				PropertyConfigurator.configure(p);
				log4jInitialized = true;
			} catch (Exception e) {
				throw ExUtil.wrapRuntimeException(e);
			}
		}
	}

	/**
	 * Initialize the log4j library
	 */
	public static void initLog4jBasic() {
		if (!log4jInitialized) {
			try {
				BasicConfigurator.configure();
				log4jInitialized = true;
			} catch (Exception e) {
				throw ExUtil.wrapRuntimeException(e);
			}
		}
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
			consoleAppender.setLayout(new PatternLayout("%8r %-35c{2} %x %-5p %m%n"));
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
