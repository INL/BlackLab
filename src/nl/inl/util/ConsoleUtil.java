/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
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
