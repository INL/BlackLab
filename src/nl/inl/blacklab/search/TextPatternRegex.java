/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search;

import nl.inl.util.StringUtil;

/**
 * A TextPattern matching a regular expression.
 */
public class TextPatternRegex extends TextPatternTerm {
	/**
	 * Instantiate a regex TextPattern.
	 *
	 * NOTE: consider using the factory method getAppropriatePattern() instead. It will choose the
	 * (likely) fastest TextPattern class, given your regular expression.
	 *
	 * @param value
	 */
	public TextPatternRegex(String value) {
		super(value);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, String fieldName) {
		return translator.regex(fieldName, value);
	}

	/**
	 * Instantiate the "best" TextPattern class for the given regex. Tries to make a
	 * TextPatternTerm, TextPatternPrefix or TextPatternWildcard because those tend to be faster
	 * than TextPatternRegex in Lucene.
	 *
	 * @param regex
	 *            the regex we want a TextPattern for
	 * @return the TextPattern
	 */
	public static TextPattern getAppropriatePattern(String regex) {
		// Try to convert to a wildcard query.
		String wildcard = regex;

		// NOTE: Backslashes (like in \w or \s) and pipes (|) were not detected by
		// StringUtil.escapeRegexCharacters until recently. This if statement can be removed soon.
		if (regex.contains("\\") || regex.contains("|")) {
			return new TextPatternRegex(regex);
		}

		// Wildcard expressions always start at beginning
		if (wildcard.charAt(0) == '^') {
			wildcard = wildcard.substring(1);
		} else {
			wildcard = ".*" + wildcard;
		}

		// Wildcard expressions always end at end
		if (wildcard.charAt(wildcard.length() - 1) == '$') {
			wildcard = wildcard.substring(0, wildcard.length() - 1);
		} else {
			wildcard += ".*";
		}

		// Mark asterisk and questionmark candidates
		// TODO: kind of ugly to use string markers like this.. better way?
		wildcard = wildcard.replaceAll("\\.\\*", "##ASTERISK##"); // .* -> *
		wildcard = wildcard.replaceAll("\\.\\+", "##QUESTIONMARK####ASTERISK##"); // .+ -> ?*
		wildcard = wildcard.replaceAll("\\.", "##QUESTIONMARK##"); // . -> ?

		// See if there's any regex stuff left
		if (StringUtil.escapeRegexCharacters(wildcard).equals(wildcard)) {
			// Nope! Safe to turn this into a wildcard query.

			// Turn into wildcard query
			wildcard = wildcard.replaceAll("##ASTERISK##", "*");
			wildcard = wildcard.replaceAll("##QUESTIONMARK##", "?");

			// Let TextPatternWildcard sort out the rest.
			return TextPatternWildcard.getAppropriatePattern(wildcard);
		}

		// Bummer, it's a real regex.
		return new TextPatternRegex(regex);
	}

}
