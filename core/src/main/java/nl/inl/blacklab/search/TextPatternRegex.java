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

import org.apache.lucene.index.Term;
import org.apache.lucene.search.RegexpQuery;

import nl.inl.blacklab.search.lucene.BLSpanMultiTermQueryWrapper;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.util.StringUtil;


/**
 * A TextPattern matching a regular expression.
 */
public class TextPatternRegex extends TextPatternTerm {
	/**
	 * Instantiate a regex TextPattern.
	 *
	 * @param value
	 */
	public TextPatternRegex(String value) {
		super(value);
	}

	@Override
	public BLSpanQuery translate(QueryExecutionContext context) {
		TextPattern result = rewrite();
		if (result != this)
			return result.translate(context);
		String valueNoStartEndMatch = optInsensitive(context, value).replaceAll("\\^|\\$", "");
		try {
			return new BLSpanMultiTermQueryWrapper<>(new RegexpQuery(
					new Term(context.luceneField(), context.subpropPrefix() + context.optDesensitize(valueNoStartEndMatch))));
		} catch (StackOverflowError e) {
			// If we pass in a really large regular expression, like a huge
			// list of words combined with OR, stack overflow occurs inside
			// Lucene's automaton building code and we may end up here.
			throw new RegexpTooLargeException();
		}
	}

	/**
	 * Rewrite to the "best" TextPattern class for the given regex. Tries to make a
	 * TextPatternTerm, TextPatternPrefix or TextPatternWildcard because those tend to be faster
	 * than TextPatternRegex in Lucene.
	 *
	 * @return the TextPattern
	 */
	public TextPattern rewrite() {
		TextPattern result = this;

        // If there's a case-sensitivity toggle flag after a
        // start-of-string match, put the flag first so we can
		// easily detect it below.
		String newValue = value.replaceAll("^\\^(\\(\\?\\-?\\w+\\))", "$1^");

		// Do we want to force a case-sensitive search?
		boolean forceCaseSensitive = false;
		boolean forceCaseInsensitive = false;
		if (newValue.startsWith("(?-i)")) {
			forceCaseSensitive = true;
			newValue = newValue.substring(5);
		} else if (newValue.startsWith("(?c)")) {
			forceCaseSensitive = true;
			newValue = newValue.substring(4);
		} else if (newValue.startsWith("(?i)")) {
			forceCaseInsensitive = true;
			newValue = newValue.substring(4);
		}

		// Try to convert to a wildcard query.
		String wildcard = newValue;
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

			// Turn into wildcard query (or prefix, or term, because we call rewrite())
			wildcard = wildcard.replaceAll("##ASTERISK##", "*");
			wildcard = wildcard.replaceAll("##QUESTIONMARK##", "?");
			TextPattern wildcardPattern = new TextPatternWildcard(wildcard).rewrite();

			// Optionally make it case-insensitive
			if (searchCaseInsensitively) {
				wildcardPattern = new TextPatternSensitive(false, false, wildcardPattern);
			}

			result = wildcardPattern;
		}

		if (result == this && !newValue.equals(value))
			result = new TextPatternRegex(newValue);

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
