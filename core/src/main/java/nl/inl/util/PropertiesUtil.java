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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Properties;

/**
 * Utilities for property files
 */
public class PropertiesUtil {

	private PropertiesUtil() {
	}

	/**
	 * Read Properties from the specified file
	 *
	 * @param file
	 *            the file to read
	 * @return the Properties read
	 */
	public static Properties readFromFile(File file) {
		try {
			if (!file.isFile()) {
				throw new RuntimeException("Property file " + file.getCanonicalPath()
						+ " does not exist or is not a regular file!");
			}

			Reader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "iso-8859-1"));
			try {
				Properties properties = new Properties();
				properties.load(in);
				return properties;
			} finally {
				in.close();
			}
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * Read Properties from a resource
	 *
	 * @param resourcePath
	 *            file path, relative to the classpath, where the properties file is
	 * @return the Properties read
	 * @throws IOException
	 */
	public static Properties getFromResource(String resourcePath) throws IOException {
		Properties properties;
		InputStream isProps = PropertiesUtil.class.getClassLoader().getResourceAsStream(
				resourcePath);
		if (isProps == null) {
			// TODO: FileNotFoundException?
			throw new RuntimeException("Properties file not found: " + resourcePath
					+ " (must be accessible from the classpath)");
		}
		try {
			properties = new Properties();
			properties.load(isProps);
			return properties;
		} finally {
			isProps.close();
		}
	}

	/**
	 * Get an integer property from a Properties object
	 *
	 * @param properties
	 *            where to read the value from
	 * @param name
	 *            the value's name
	 * @param defaultValue
	 *            default value if property not specified
	 * @return the integer value
	 */
	public static int getIntProp(Properties properties, String name, int defaultValue) {
		final String property = properties.getProperty(name);
		if (property == null)
			return defaultValue;
		try {
			return Integer.parseInt(property);
		} catch (NumberFormatException e) {
			throw new RuntimeException(name + " in property file must be a number!", e);
		}
	}

	/**
	 * Get a File property from a Properties object.
	 *
	 * This must be an absolute file path.
	 *
	 * @param properties
	 *            where to read the value from
	 * @param name
	 *            the value's name
	 * @return the file
	 */
	public static File getFileProp(Properties properties, String name) {
		return getFileProp(properties, name, null);
	}

	/**
	 * Get a File property from a Properties object.
	 *
	 * This may be an absolute file path (starts with / or \ or a Windows drive letter spec), or a
	 * path relative to basePath
	 *
	 * @param properties
	 *            where to read the value from
	 * @param name
	 *            the value's name
	 * @param basePath
	 *            base path the file path may be relative to
	 * @return the file, or null if not found
	 */
	public static File getFileProp(Properties properties, String name, File basePath) {
		return getFileProp(properties, name, null, basePath);
	}

	/**
	 * Get a File property from a Properties object.
	 *
	 * This may be an absolute file path (starts with / or \ or a Windows drive letter spec), or a
	 * path relative to basePath
	 *
	 * @param properties
	 *            where to read the value from
	 * @param name
	 *            the value's name
	 * @param defaultValue default value if the property was not specified
	 * @param basePath
	 *            base path the file path may be relative to
	 * @return the file, or null if not found
	 */
	public static File getFileProp(Properties properties, String name, String defaultValue, File basePath) {
		Object prop = properties.get(name);
		if (prop == null)
			prop = defaultValue;
		if (prop == null)
			return null;
		File filePath = new File(prop.toString());

		// Is it an absolute path, or no base path given?
		if (basePath == null || filePath.isAbsolute()) {
			// Yes; ignore our base directory
			return filePath;
		}
		// Relative path; concatenate with base directory
		return new File(basePath, filePath.getPath());
	}

	/**
	 * Get a boolean property from a Properties object.
	 *
	 * The property value must be "true" or "false", case-insensitive.
	 *
	 * @param properties
	 *            where to read the value from
	 * @param name
	 *            the value's name
	 * @param defaultValue default value if the property was not specified
	 * @return the boolean value
	 * @throws RuntimeException
	 *             on illegal value
	 */
	public static boolean getBooleanProp(Properties properties, String name, boolean defaultValue) {
		String s = properties.getProperty(name);
		if (s == null)
			return defaultValue;
		s = s.trim();
		if (s.length() == 0)
			return defaultValue;
		if (s.equalsIgnoreCase("true"))
			return true;
		if (s.equalsIgnoreCase("false"))
			return false;
		throw new RuntimeException("Value " + name
				+ " in properties file must be 'true' or 'false'");
	}

}
