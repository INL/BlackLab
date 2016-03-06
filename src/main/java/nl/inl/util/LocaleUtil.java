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

	private LocaleUtil() {
	}

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
