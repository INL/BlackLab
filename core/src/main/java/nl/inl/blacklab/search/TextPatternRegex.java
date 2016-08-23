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
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		return translator.regex(context, translator.optInsensitive(context, value));
	}

	/**
	 * Rewrite to the "best" TextPattern class for the given regex. Tries to make a
	 * TextPatternTerm, TextPatternPrefix or TextPatternWildcard because those tend to be faster
	 * than TextPatternRegex in Lucene.
	 *
	 * @return the TextPattern
	 */
	@Override
	public TextPattern rewrite() {
		TextPattern result = this;

        // If there's a case-sensitivity toggle flag after a
        // start-of-string match, put the flag first so we can
		// easily detect it below.
		value = value.replaceAll("^\\^(\\(\\?\\-?\\w+\\))", "$1^");

		// Do we want to force a case-sensitive search?
		boolean forceCaseSensitive = false;
		boolean forceCaseInsensitive = false;
		if (value.startsWith("(?-i)")) {
			forceCaseSensitive = true;
			value = value.substring(5);
		} else if (value.startsWith("(?c)")) {
			forceCaseSensitive = true;
			value = value.substring(4);
		} else if (value.startsWith("(?i)")) {
			forceCaseInsensitive = true;
			value = value.substring(4);
		}

		// Try to convert to a wildcard query.
		String wildcard = value;
		if (wildcard.length() > 0) {
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
		}

		// Mark asterisk and questionmark candidates
		// TO DO: kind of ugly to use string markers like this.. a better way is to
		//   walk through the string, detecting stuff as we go. When we detect anything
		//   that doesn't fit in a wildcard query, we know we have to use regex.
		//   Otherwise, we do the required replacements and create a wildcard query.
		wildcard = wildcard.replaceAll("\\.\\*", "##ASTERISK##"); // .* -> *
		wildcard = wildcard.replaceAll("\\.\\+", "##QUESTIONMARK####ASTERISK##"); // .+ -> ?*
		wildcard = wildcard.replaceAll("\\.", "##QUESTIONMARK##"); // . -> ?

		// Does the regex pattern begin with (?i) (case-insensitive search)?
		boolean searchCaseInsensitively = false;
		if (wildcard.startsWith("(?i)")) { // not needed anymore?
			searchCaseInsensitively = true;
			wildcard = wildcard.substring(4);
		}

		// See if there's any regex stuff left
		if (StringUtil.escapeRegexCharacters(wildcard).equals(wildcard)) {
			// Nope! Safe to turn this into a wildcard query.

			// Turn into wildcard query
			wildcard = wildcard.replaceAll("##ASTERISK##", "*");
			wildcard = wildcard.replaceAll("##QUESTIONMARK##", "?");
			TextPattern wildcardPattern = new TextPatternWildcard(wildcard);

			// Optionally make it case-insensitive
			if (searchCaseInsensitively) {
				wildcardPattern = new TextPatternSensitive(false, false, wildcardPattern);
			}

			// Let TextPatternWildcard sort out the rest
			// (may be turned into a prefix or term query if possible).
			result = wildcardPattern.rewrite();
		}

		if (forceCaseSensitive) {
			// Pattern started with (?-i) or (?c) to force it to be case sensitive
			result = new TextPatternSensitive(true, true, result);
		} else if (forceCaseInsensitive) {
			// Pattern started with (?i) to force it to be case insensitive
			result = new TextPatternSensitive(false, false, result);
		}

		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternRegex) {
			return super.equals(obj);
		}
		return false;
	}

	@Deprecated
	@Override
	public String toString(QueryExecutionContext context) {
		return "REGEX(" + context.luceneField() + ", " + context.optDesensitize(value) + ")";
	}

	@Override
	public String toString() {
		return "REGEX(" + value + ")";
	}

}
