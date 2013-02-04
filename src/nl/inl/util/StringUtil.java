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

import java.text.Collator;
import java.text.Normalizer;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Een collectie reguliere expressies om verschillende patronen uit Strings te filteren.
 */
public class StringUtil {
	/** String containing nbsp character (decimal 160 = hex A0) */
	public static final String STR_NON_BREAKING_SPACE = "\u00A0";

	/** nbsp character (decimal 160 = hex A0) */
	public static final char CHAR_NON_BREAKING_SPACE = '\u00A0';

	/** Matches whitespace. */
	private static final Pattern PATT_WHITESPACE = Pattern.compile("\\s+");

	/** Match een XML tag */
	private final static Pattern PATT_XML_TAG = Pattern.compile("<[^>]+>");

	/** Match een dubbele quote */
	private final static Pattern PATT_DOUBLE_QUOTE = Pattern.compile("\"");

	/** Matcht een niet-lege string die alleen whitespace bevat */
	private final static Pattern PATT_ONLY_WHITESPACE = Pattern.compile("^\\s+$");

	private static final Pattern PATT_APOSTROPHE = Pattern.compile("'");

	/** Pattern matching nbsp character (decimal 160 = hex A0) */
	private static final Pattern PATT_NON_BREAKING_SPACE = Pattern.compile(STR_NON_BREAKING_SPACE);

	/**
	 * Matches Unicode diacritics composition characters, which are separated out by the Normalizer
	 * and then discarded using this regex.
	 */
	private static final Pattern PATT_DIACRITICS = Pattern
			.compile("\\p{InCombiningDiacriticalMarks}+");

	// FIXME: punctuation actually has a regex class: \\p{P} . Not sure if this matches diacritics
	// or not, and whether we want to anyway. TEST!
	/**
	 * Here, we define punctuation as anything that is not an ASCII character, digit, whitespace or
	 * diacritical mark. This is used to remove punctuation from a *normalized* String (i.e. one
	 * where the diacritical marks have been split into separate unicode codepoints).
	 *
	 * FIXME: Latin char assumption
	 */
	private static final Pattern PATT_PUNCTUATION = Pattern
			.compile("[^\\sA-Za-z0-9\\p{InCombiningDiacriticalMarks}]");

	/** The default collator: Dutch, case-insensitive */
	protected static Collator dutchInsensitiveCollator = null;

	private static Collator englishInsensitiveCollator;

	/**
	 * Replaces space with non-breaking space so the browser doesn't word-wrap
	 *
	 * @param input
	 *            the string with spaces
	 * @return the string with non-breaking spaces
	 */
	public static String makeNonBreaking(String input) {
		return PATT_WHITESPACE.matcher(input).replaceAll(STR_NON_BREAKING_SPACE);
	}

	/**
	 * Replaces non-breaking spaces with normal spaces.
	 *
	 * @param string
	 *            the input
	 * @return the result
	 */
	public static String convertNbspToSpace(String string) {
		return PATT_NON_BREAKING_SPACE.matcher(string).replaceAll(" ");
	}

	/**
	 * Abbreviates a string for display if necessary.
	 *
	 * Also replaces line breaks with spaces. Uses overshootAllowed of 0, and adds ellipsis if
	 * abbreviated.
	 *
	 * @param str
	 *            the string to abbreviate
	 * @param preferredLength
	 *            the maximum length we would like to see
	 * @return the (possibly) abbreviated string
	 */
	public static String abbreviate(String str, int preferredLength) {
		return abbreviate(str, preferredLength, 0, true);
	}

	/**
	 * Abbreviates a string for display if necessary.
	 *
	 * Also normalizes whitspace (replacing a line break with a space).
	 *
	 * @param str
	 *            the string to abbreviate
	 * @param preferredLength
	 *            the maximum length we would like to see
	 * @param overshootAllowed
	 *            how many more characters than the previous value is allowable
	 * @param addEllipsis
	 *            whether or not we should add "..." at the end if we abbreviated
	 * @return the (possibly) abbreviated string
	 */
	public static String abbreviate(String str, int preferredLength, int overshootAllowed,
			boolean addEllipsis) {
		String result = str.replaceAll("\\s+", " "); // normalize whitespace
		if (result.length() > preferredLength + overshootAllowed) {
			int i = result.substring(0, preferredLength + 1).lastIndexOf(" ");
			if (i >= 1)
				result = result.substring(0, i);
			else
				result = result.substring(0, preferredLength);
			if (addEllipsis)
				result += "...";
		}
		return result.trim();
	}

	/**
	 * Escape capital letters with the specified escape string.
	 *
	 * NOTE: this only works properly on ASCII strings!
	 *
	 * This can be used to compensate for case-insensitive filesystems.
	 *
	 * @param str
	 *            the string in which to escape capitals
	 * @param escapeString
	 *            the string to add in front of each capital
	 * @return the escaped string
	 */
	public static String escapeCapitals(String str, String escapeString) {
		// TODO: eliminate Latin char assumption; define static Pattern
		return str.replaceAll("[A-Z]", escapeString + "$0");
	}

	/**
	 * Escape csv special character (quote)
	 *
	 * @param termStr
	 *            the string to escape characters in
	 * @return the escaped string
	 */
	public static String escapeCsvCharacters(String termStr) {
		return PATT_DOUBLE_QUOTE.matcher(termStr).replaceAll("\"\"");
	}

	static final Pattern regexCharacters = Pattern
			.compile("([\\|\\\\\\?\\*\\+\\(\\)\\[\\]\\-\\^\\$\\{\\}\\.])");

	/**
	 * Escape regex special characters
	 *
	 * @param termStr
	 *            the string to escape characters in
	 * @return the escaped string
	 */
	public static String escapeRegexCharacters(String termStr) {
		Matcher m = regexCharacters.matcher(termStr);
		termStr = m.replaceAll("\\\\$1");
		return termStr;
	}

	/**
	 * Escape regex special characters
	 *
	 * @param termStr
	 *            the string to escape characters in
	 * @return the escaped string
	 */
	public static boolean containsRegexCharacters(String termStr) {
		Matcher m = regexCharacters.matcher(termStr);
		return m.matches();
	}

	/**
	 * Escape the wildcard characters * and ? in a string with a \
	 *
	 * @param result
	 *            the original string
	 * @return the string with the wildcard characters escaped with a \.
	 */
	public static String escapeWildcardCharacters(String result) {
		// Escape regex-characters
		Pattern p = Pattern.compile("([\\?\\*])");
		Matcher m = p.matcher(result);
		result = m.replaceAll("\\\\$1");
		return result;
	}

	/**
	 * Escape the special XML chars (<, >, &, ") with their named entity equivalents.
	 *
	 * @param source
	 *            the source string
	 * @return the escaped string
	 */
	public static String escapeXmlChars(String source) {
		int estResultLength = source.length() * 5 / 4; // reasonable estimate of max. space needed
		StringBuilder sb = new StringBuilder(estResultLength);
		int start = 0;
		for (int i = 0; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '<' || c == '>' || c == '&' || c == '"') {
				sb.append(source.substring(start, i));
				switch (c) {
				case '<':
					sb.append("&lt;");
					break;
				case '>':
					sb.append("&gt;");
					break;
				case '&':
					sb.append("&amp;");
					break;
				case '"':
					sb.append("&quot;");
					break;
				}
				start = i + 1;
			}
		}
		sb.append(source.substring(start));
		return sb.toString();
	}

	/**
	 * Get the default collator.
	 *
	 * @return the default collator.
	 */
	public static Collator getDefaultCollator() {
		return getDutchInsensitiveCollator();
	}

	/**
	 * Get a Dutch, case-insensitive collator.
	 *
	 * @return the Dutch, case-insensitive collator.
	 */
	public static Collator getDutchInsensitiveCollator() {
		if (dutchInsensitiveCollator == null) {
			dutchInsensitiveCollator = Collator.getInstance(LocaleUtil.getDutchLocale());
			dutchInsensitiveCollator.setStrength(Collator.SECONDARY);
		}
		return dutchInsensitiveCollator;
	}

	/**
	 * Get a Dutch, case-insensitive collator.
	 *
	 * @return the Dutch, case-insensitive collator.
	 */
	public static Collator getEnglishInsensitiveCollator() {
		if (englishInsensitiveCollator == null) {
			englishInsensitiveCollator = Collator.getInstance(LocaleUtil.getEnglishLocale());
			englishInsensitiveCollator.setStrength(Collator.SECONDARY);
		}
		return englishInsensitiveCollator;
	}

	/**
	 * Checks if the specified string is made up of whitespace only.
	 *
	 * @param string
	 *            the string to check
	 * @return true if the specified string is only whitespace, false otherwise
	 */
	public static boolean isWhitespace(String string) {
		return PATT_ONLY_WHITESPACE.matcher(string).matches();
	}

	/**
	 * Join a number of (string representations of) items to a single string using a delimiter
	 *
	 * @param <T>
	 *            the type of items to join
	 * @param parts
	 *            the parts to join
	 * @param delimiter
	 *            the delimiter to use
	 * @return the joined string
	 */
	public static <T> String join(Iterable<T> parts, String delimiter) {
		StringBuilder builder = new StringBuilder();
		Iterator<T> iter = parts.iterator();
		while (iter.hasNext()) {
			builder.append(iter.next().toString());
			if (!iter.hasNext()) {
				break;
			}
			builder.append(delimiter);
		}
		return builder.toString();
	}

	/**
	 * Limit a string to a certain length, adding an ellipsis if desired.
	 *
	 * Tries to cut the string at a word boundary, but will cut through words if necessary.
	 *
	 * @param str
	 *            the string
	 * @param preferredLength
	 *            the preferred maximum length for the result string
	 * @param overshootAllowed
	 *            the number of characters the string may run beyond the preferred length without
	 *            resorting to cutting
	 * @param addEllipsis
	 *            whether or not to add three periods (...) to a string after cutting
	 * @return the resulting string. This is at most
	 *         <code>preferredLength + max(overshootAllowed, 3)</code> long (if addEllipsis is
	 *         true).
	 */
	public static String limitStringToLength(String str, int preferredLength, int overshootAllowed,
			boolean addEllipsis) {
		String result = str;
		if (result.length() > preferredLength + overshootAllowed) {
			int i = result.substring(0, preferredLength + 1).lastIndexOf(" ");
			if (i >= 1)
				result = result.substring(0, i);
			else
				result = result.substring(0, preferredLength);
			if (addEllipsis)
				result += "...";
		}
		return result.trim();
	}

	/**
	 * Replace adjacent whitespace characters with a single space
	 *
	 * @param s
	 *            source string
	 * @return the result
	 */
	public static String normalizeWhitespace(String s) {
		Matcher m = PATT_WHITESPACE.matcher(s);
		String out0 = m.replaceAll(" ");
		return out0;
	}

	/**
	 * Removes apostrophes from a string
	 *
	 * @param str
	 *            the input string
	 * @return the string with apostrophes removed.
	 */
	public static String removeApostrophes(String str) {
		// TODO why is this in the library...? Clarify or remove!
		Matcher m = PATT_APOSTROPHE.matcher(str);
		str = m.replaceAll("");
		return str;
	}

	/**
	 * Remove XML tags from a string
	 *
	 * @param s
	 *            the source string
	 * @return the string with tags removed
	 */
	public static String removeTags(String s) {
		return PATT_XML_TAG.matcher(s).replaceAll(""); // tags verwijderen
	}

	/**
	 * Unescape XML special characters (<, >, & and ")
	 *
	 * @param source
	 *            the string with XML characters escaped as XML entities
	 * @return the unescaped string
	 */
	public static String unescapeXmlChars(String source) {
		// TODO: define static Patterns for these
		return source.replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&amp;", "&")
				.replaceAll("&quot;", "\"");
	}

	/**
	 * Wrap text at the specified number of characters
	 *
	 * @param message
	 *            the text to wrap
	 * @param wrapAt
	 *            the maximum number of characters per line
	 * @return the wrapped text
	 */
	public static String wrapText(String message, int wrapAt) {
		String wrapped = "";
		String lines[] = message.split("\n");
		for (String line : lines) {
			if (line.length() > 0) {
				while (line.length() > 0) {
					int i = wrapAt + 1;
					if (i < line.length()) {
						while (i > 0) {
							char c = line.charAt(i);
							if (c == ' ' || c == '\t' || c == '\r' || c == '\n')
								break;
							i--;
						}
						if (i == 0)
							i = wrapAt + 1;
					} else
						i = line.length();
					wrapped += line.substring(0, i).trim() + "\n";
					line = line.substring(i).trim();
				}
			} else {
				wrapped += "\n";
			}
		}
		return wrapped.trim();
	}

	/**
	 * When called with a null reference, returns the empty string. Otherwise, returns the string
	 * unchanged
	 *
	 * @param str
	 *            the input string (or a null reference)
	 * @return the original string, or the empty string
	 */
	public static String nullToEmpty(String str) {
		return str == null ? "" : str;
	}

	/**
	 * Convert accented letters to their unaccented counterparts.
	 *
	 * @param input
	 *            the string possibly containing accented letters.
	 * @return the unaccented version
	 */
	public static String removeAccents(String input) {
		// Separate characters into base character and diacritics characters
		String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);

		// Remove diacritics
		return PATT_DIACRITICS.matcher(normalized).replaceAll("");
	}

	// FIXME: when we use regex class for PATT_PUNCTUATION, we should check that we don't rely on
	// this
	// method removing diacritical marks (or if the punctuation class includes those too)
	/**
	 * Keep all [accented] characters, numbers and spaces. Replace everything else with a space.
	 *
	 * @param input
	 *            the input string
	 * @return the string without punctuation
	 */
	public static String removePunctuation(String input) {
		// Decompose (separate) characters into base character and diacritics characters
		input = Normalizer.normalize(input, Normalizer.Form.NFD);

		// Remove punctuation
		input = PATT_PUNCTUATION.matcher(input).replaceAll(" ");

		// Recompose accented characters
		input = Normalizer.normalize(input, Normalizer.Form.NFC);

		return input;
	}

	/**
	 * Optionally 'pluralize' a singular noun by appending an s if the
	 * number is not equal to 1.
	 *
	 * Obviously, this doesn't take irregular plural forms (sheep/sheep, man/men)
	 * into account.
	 *
	 * @param singular the singular to 'pluralize'
	 * @param number if this equals 1, no s is added
	 * @return the possibly pluralized form
	 */
	public static String pluralize(String singular, long number) {
		return singular + (number != 1 ? "s" : "");
	}
}
