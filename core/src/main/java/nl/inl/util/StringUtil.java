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
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * A collection of String-related utility methods and regular expression patterns.
 */
public class StringUtil {
	/** String containing nbsp character (decimal 160 = hex A0) */
	public static final String STR_NON_BREAKING_SPACE = "\u00A0";

	/** nbsp character (decimal 160 = hex A0) */
	public static final char CHAR_NON_BREAKING_SPACE = '\u00A0';

	/** Matches whitespace. */
	public static final Pattern PATT_WHITESPACE = Pattern.compile("\\s+");

	/** Matches trailing whitespace. */
	public final static Pattern PATT_LEADING_WHITESPACE = Pattern.compile("^\\s+");

	/** Matches trailing whitespace. */
	public final static Pattern PATT_TRAILING_WHITESPACE = Pattern.compile("\\s+$");

	/** Matcht een niet-lege string die alleen whitespace bevat */
	public final static Pattern PATT_ONLY_WHITESPACE = Pattern.compile("^\\s+$");

	/** Match een XML tag */
	private final static Pattern PATT_XML_TAG = Pattern.compile("<[^>]+>");

	/** Match een dubbele quote */
	private final static Pattern PATT_DOUBLE_QUOTE = Pattern.compile("\"");

	/** Match een enkele quote */
	private final static Pattern PATT_SINGLE_QUOTE = Pattern.compile("'");

	/** Pattern matching nbsp character (decimal 160 = hex A0) */
	private static final Pattern PATT_NON_BREAKING_SPACE = Pattern.compile(STR_NON_BREAKING_SPACE);

	/**
	 * Matches Unicode diacritics composition characters, which are separated out by the Normalizer
	 * and then discarded using this regex.
	 */
	private static final Pattern PATT_DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

	/** Whitespace and/or punctuation at end */
	final static Pattern PATT_WS_PUNCT_AT_END = Pattern.compile("[\\p{P}\\s]+$");

	/** Whitespace and/or punctuation at start */
	final static Pattern PATT_WS_PUNCT_AT_START = Pattern.compile("^[\\p{P}\\s]+");

	/** Punctuation. */
	public static final Pattern PATT_PUNCTUATION = Pattern.compile("\\p{P}");

	/** Dutch locale */
	private static Locale dutchLocale = new Locale("nl", "NL");

	/** English locale */
	private static Locale englishLocale = new Locale("en", "GB");

	/** The default collator: Dutch, case-insensitive */
	protected static Collator dutchInsensitiveCollator = null;

	private static Collator englishInsensitiveCollator;

	private StringUtil() {
	}

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
	public static String abbreviate(String str, int preferredLength, int overshootAllowed, boolean addEllipsis) {
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

	static final Pattern regexCharacters = Pattern.compile("([\\|\\\\\\?\\*\\+\\(\\)\\[\\]\\-\\^\\$\\{\\}\\.])");

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
		if (source == null)
			source = "";
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
	 * Escape a string for inclusion in JSON output.
	 *
	 * Conforms to the JSON.org spec for strings.
	 *
	 * @param input the input string
	 * @return the JSON-escaped output string
	 */
	public static String escapeJson(String input) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			// One of the characters with its own escape?
			int x = "\"\\\b\f\n\r\t".indexOf(c);
			if (x < 0) {
				// No; other control character?
				if (c < 32) {
					// Yes. Use numeric escape
					result.append(String.format("\\u%04x", (int)c));
				} else {
					// No, normal char; no problem
					result.append(c);
				}
			} else {
				// Double quote, backslash, bell, form feed,
				// newline, return or tab. Add special escape
				// depending on the character.
				switch (x) {
				case 0: result.append("\\\""); break;
				case 1: result.append("\\\\"); break;
				case 2: result.append("\\b"); break;
				case 3: result.append("\\f"); break;
				case 4: result.append("\\n"); break;
				case 5: result.append("\\r"); break;
				case 6: result.append("\\t"); break;
				}
			}
		}
		return result.toString();
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
			dutchInsensitiveCollator = Collator.getInstance(dutchLocale);
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
			englishInsensitiveCollator = Collator.getInstance(englishLocale);
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
			T value = iter.next();
			builder.append(value == null ? "(null)" : value.toString());
			if (!iter.hasNext()) {
				break;
			}
			builder.append(delimiter);
		}
		return builder.toString();
	}

	/**
	 * Join a number of (string representations of) items to a single string using a delimiter
	 *
	 * @param <T>
	 *            the type of items to join
	 * @param parts
	 *            the array of parts to join
	 * @param delimiter
	 *            the delimiter to use
	 * @return the joined string
	 */
	public static <T> String join(T[] parts, String delimiter) {
		StringBuilder builder = new StringBuilder();
		for (T value: parts) {
			if (builder.length() > 0)
				builder.append(delimiter);
			builder.append(value == null ? "(null)" : value.toString());
		}
		return builder.toString();
	}

	/**
	 * Join keys and values from a map to produce a string.
	 *
	 * @param map the map to join
	 * @param delimiter how to delimit map entries
	 * @param keyValueDelimiter what to put between key and value
	 * @return the resulting string
	 */
	public static <T, U> String join(Map<T, U> map, String delimiter, String keyValueDelimiter) {
		StringBuilder builder = new StringBuilder();
		for (Map.Entry<T, U> e: map.entrySet()) {
			if (builder.length() > 0)
				builder.append(delimiter);
			U value = e.getValue();
			builder.append(e.getKey().toString()).append(keyValueDelimiter).append(value == null ? "(null)" : value.toString());
		}
		return builder.toString();
	}

	/**
	 * Join keys and values from a map to produce a string.
	 *
	 * Uses an equals sign between key and value.
	 *
	 * @param map the map to join
	 * @param delimiter how to delimit map entries
	 * @return the resulting string
	 */
	public static <T, U> String join(Map<T, U> map, String delimiter) {
		return join(map, delimiter, "=");
	}

	/**
	 * Join keys and values from a map to produce a string.
	 *
	 * Uses an equals sign between key and value and a semicolon and
	 * space between entries.
	 *
	 * @param map the map to join
	 * @return the resulting string
	 */
	public static <T, U> String join(Map<T, U> map) {
		return join(map, "; ");
	}

	/**
	 * Custom stringifier for a type
	 * @param <T> the type we can stringify
	 */
	public interface Stringifier<T> {
		String toString(T o);
	}

	/**
	 * Join a number of (string representations of) items to a single string using a delimiter
	 *
	 * @param <T> the type of items to join
	 * @param parts the parts to join
	 * @param j how to convert objects to string
	 * @param delimiter the delimiter to use
	 * @return the joined string
	 */
	public static <T> String join(Iterable<T> parts, Stringifier<T> j, String delimiter) {
		StringBuilder builder = new StringBuilder();
		Iterator<T> iter = parts.iterator();
		while (iter.hasNext()) {
			builder.append(j.toString(iter.next()));
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
	public static String limitStringToLength(String str, int preferredLength, int overshootAllowed, boolean addEllipsis) {
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
		return m.replaceAll(" ");
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
		return source.replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&amp;", "&").replaceAll("&quot;", "\"");
	}

	/**
	 * Wrap text at the specified number of characters,
	 * adding newlines at the right places.
	 *
	 * @param message
	 *            the text to wrap
	 * @param wrapAt
	 *            the maximum number of characters per line
	 * @return the wrapped text as a newline-joined string
	 */
	public static String wrapToString(String message, int wrapAt) {
		return join(wrap(message, wrapAt), "\n");
	}

	/**
	 * Wrap text at the specified number of characters,
	 * returning a list of lines.
	 *
	 * @param message
	 *            the text to wrap
	 * @param wrapAt
	 *            the maximum number of characters per line
	 * @return the wrapped text as a newline-joined string
	 */
	public static List<String> wrap(String message, int wrapAt) {
		List<String> wrapped = new ArrayList<>();
		String lines[] = message.split("\n");
		for (String line: lines) {
			if (line.length() > 0) {
				while (line.length() > 0) {
					int i = wrapAt + 1;
					if (i < line.length()) {
						while (i > 0) {
							char c = line.charAt(i);
							if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
								i++;
								break;
							}
							i--;
						}
						if (i == 0)
							i = wrapAt + 1;
					} else
						i = line.length();
					wrapped.add(rtrim(line.substring(0, i)));
					line = rtrim(line.substring(i));
				}
			} else {
				wrapped.add("");
			}
		}
		return wrapped;
	}

	/**
	 * Remove leading whitespace from a string.
	 *
	 * @param input a string
	 * @return the string without leading whitespace
	 */
	public static String ltrim(String input) {
		return PATT_LEADING_WHITESPACE.matcher(input).replaceAll("");
	}

	/**
	 * Remove trailing whitespace from a string.
	 *
	 * @param input a string
	 * @return the string without trailing whitespace
	 */
	public static String rtrim(String input) {
		return PATT_TRAILING_WHITESPACE.matcher(input).replaceAll("");
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

	/**
	 * Replace any punctuation characters with a space.
	 *
	 * @param input
	 *            the input string
	 * @return the string without punctuation
	 */
	public static String removePunctuation(String input) {
		return PATT_PUNCTUATION.matcher(input).replaceAll(" ");
	}

	/**
	 * Remove any punctuation and whitespace at the start and end of input.
	 *
	 * @param input
	 *            the input string
	 * @return the string without punctuation or whitespace at the edges.
	 */
	public static String trimWhitespaceAndPunctuation(String input) {
		input = PATT_WS_PUNCT_AT_END.matcher(input).replaceAll("");
		input = PATT_WS_PUNCT_AT_START.matcher(input).replaceAll("");
		return input;
	}

	/**
	 * Return the singular or the plural form of a noun depending on a number.
	 *
	 * This version of the method simply appends an "s" to form the plural.
	 * For irregular plural forms, use the version that takes 3 parameters.
	 *
	 * @param singular the singular to 'pluralize'
	 * @param number if this equals 1, no s is added
	 * @return the possibly pluralized form
	 */
	public static String pluralize(String singular, long number) {
		return pluralize(singular, singular + "s", number);
	}

	/**
	 * Return the singular or the plural form of a noun depending on a number.
	 *
	 * @param singular the singular form of the word
	 * @param plural the plural form of the word
	 * @param number if this equals 1, the sinular is returned, otherwise the plural
	 * @return the possibly pluralized form
	 */
	public static String pluralize(String singular, String plural, long number) {
		return number == 1 ? singular : plural;
	}

	/**
	 * Returns a new collator that takes spaces into account (unlike the default Java collators,
	 * which ignore spaces), so we can sort "per word".
	 *
	 * Example: with the default collator, "cat dog" would be sorted after "catapult" (a after d).
	 * With the per-word collator, "cat dog" would be sorted before "catapult" (cat before
	 * catapult).
	 *
	 * NOTE: the base collator must be a RuleBasedCollator, but the argument has type Collator for
	 * convenience (not having to explicitly cast when calling)
	 *
	 * @param base
	 *            the collator to base the per-word collator on.
	 * @return the per-word collator
	 */
	public static RuleBasedCollator getPerWordCollator(Collator base) {
		if (!(base instanceof RuleBasedCollator))
			throw new IllegalArgumentException("Base collator must be rule-based!");

		try {
			// Insert a collation rule to sort the space character before the underscore
			RuleBasedCollator ruleBasedCollator = (RuleBasedCollator) base;
			String rules = ruleBasedCollator.getRules();
			return new RuleBasedCollator(rules.replaceAll("<'_'", "<' '<'_'"));
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns a new collator that sort digits at the end of the alphabet instead of the beginning.
	 *
	 * NOTE: the base collator must be a RuleBasedCollator, but the argument has type Collator for
	 * convenience (not having to explicitly cast when calling)
	 *
	 * @param base
	 *            the collator to base the new collator on.
	 * @return the new collator
	 */
	public static RuleBasedCollator getSortDigitsAtEndCollator(Collator base) {
		if (!(base instanceof RuleBasedCollator))
			throw new IllegalArgumentException("Base collator must be rule-based!");

		try {
			// Insert a collation rule to sort the space character before the underscore
			RuleBasedCollator ruleBasedCollator = (RuleBasedCollator) base;
			String rules = ruleBasedCollator.getRules();
			rules = rules.replaceAll("<0<1<2<3<4<5<6<7<8<9", "");
			rules += "<0<1<2<3<4<5<6<7<8<9";
			return new RuleBasedCollator(rules);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Escape any apostrophes in the string, for when we want to single-quote it.
	 * @param str the string to escape
	 * @return the escaped string
	 */
	public static String escapeApostrophe(String str) {
		return PATT_SINGLE_QUOTE.matcher(str).replaceAll("\\\\'");
	}

	/**
	 * Escape double quote and backslash with a backslash character.
	 *
	 * Useful for putting strings between double quotes.
	 *
	 * @param str the string to escape
	 * @return the escaped string
	 */
	public static String escapeDoubleQuotedString(String str) {
		str = str.replaceAll("[\"\\\\]", "\\\\$0");
		str = str.replaceAll("\r", "\\\\r");
		str = str.replaceAll("\n", "\\\\n");
		return str;
	}

	/**
	 * A lowercase letter followed by an uppercase one,
	 * both matched in groups.
	 */
	static Pattern lcaseUcase = Pattern.compile("(\\p{Ll})(\\p{Lu})");

	/**
	 * Convert a string from a camel-case "identifier" style to
	 * a human-readable version, by putting spaces between words,
	 * uppercasing the first letter and lowercasing the rest.
	 *
	 * E.g. myCamelCaseString becomes "My camel case string".
	 *
	 * @param camelCaseString a string in camel case, i.e. multiple capitalized
	 *   words glued together.
	 * @return a human-readable version of the input string
	 */
	public static String camelCaseToDisplayable(String camelCaseString) {
		return camelCaseToDisplayable(camelCaseString, false);
	}

	/**
	 * Convert a string from a camel-case "identifier" style to
	 * a human-readable version, by putting spaces between words,
	 * uppercasing the first letter and lowercasing the rest.
	 *
	 * E.g. myCamelCaseString becomes "My camel case string".
	 *
	 * @param camelCaseString a string in camel case, i.e. multiple capitalized
	 *   words glued together.
	 * @param dashesToSpaces if true, also converts dashes to spaces
	 * @return a human-readable version of the input string
	 */
	public static String camelCaseToDisplayable(String camelCaseString, boolean dashesToSpaces) {
		String spaceified = lcaseUcase.matcher(camelCaseString).replaceAll("$1 $2");
		if (dashesToSpaces)
			spaceified = spaceified.replace('-', ' ');
		return StringUtils.capitalize(spaceified.toLowerCase());
		//		if (str.length() == 0)
		//			return str;
		//		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

	/**
	 * For a number n, return a string like "nth".
	 *
	 * So 1 returns "1st", 2 returns "2nd", and so on.
	 *
	 * @param docNumber number
	 * @return ordinal for that number
	 */
	public static String ordinal(int docNumber) {
		final String[] ordSuffix = {"th", "st", "nd", "rd"};
		int i = docNumber;
		if (i < 1 || i > 3)
			i = 0;
		return docNumber + ordSuffix[i];
	}

}
