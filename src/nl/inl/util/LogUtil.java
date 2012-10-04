/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.util;

import java.io.File;
import java.util.Properties;

import org.apache.log4j.BasicConfigurator;
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

}
