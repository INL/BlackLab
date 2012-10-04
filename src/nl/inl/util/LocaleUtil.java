/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.util;

import java.util.Locale;

/**
 * Utilities to do with Locales.
 *
 * Locales abstract per-country differences like date formats, sorting rules, etc.
 */
public class LocaleUtil {

	/**
	 * Our default dutch locale
	 */
	private static Locale dutchLocale = new Locale("nl", "NL");

	/**
	 * Our default dutch locale
	 */
	private static Locale englishLocale = new Locale("en", "GB");

	/**
	 * Get the dutch locale
	 *
	 * @return the dutch locale
	 */
	public static Locale getDutchLocale() {
		return dutchLocale;
	}

	/**
	 * Get the dutch locale
	 *
	 * @return the dutch locale
	 */
	public static Locale getEnglishLocale() {
		return englishLocale;
	}

}
