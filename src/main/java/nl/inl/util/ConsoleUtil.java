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
import java.io.InputStreamReader;

/**
 * Utilities for simple user input from stdin.
 */
public class ConsoleUtil {
	/**
	 * Easily read from standard input
	 */
	private static BufferedReader stdinReader = IoUtil
			.makeBuffered(new InputStreamReader(System.in));

	private ConsoleUtil() {
	}

	/**
	 * Prompt the user for a string
	 *
	 * @param prompt
	 *            the prompt
	 * @return the string the user entered
	 */
	public static String askString(String prompt) {
		return askString(prompt, "");
	}

	/**
	 * Prompt the user for a string
	 *
	 * @param prompt
	 *            the prompt
	 * @param defaultValue
	 *            value to use if the user enters nothing (also displayed)
	 * @return the string the user entered
	 */
	public static String askString(String prompt, String defaultValue) {
		try {
			System.out.print(prompt + (defaultValue == null ? "" : " [" + defaultValue + "] ")
					+ "> ");
			String str = stdinReader.readLine().trim();
			if (defaultValue != null && str.length() == 0)
				str = defaultValue;
			return str;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
