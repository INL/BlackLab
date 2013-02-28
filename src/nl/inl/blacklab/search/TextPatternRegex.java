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
		return translator.regex(context, translator.optCaseInsensitive(context, value));
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
		// Try to convert to a wildcard query.
		String wildcard = value;

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
		// TO DO: kind of ugly to use string markers like this.. a better way is to
		//   walk through the string, detecting stuff as we go. When we detect anything
		//   that doesn't fit in a wildcard query, we know we have to use regex.
		//   Otherwise, we do the required replacements and create a wildcard query.
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
			return new TextPatternWildcard(wildcard).rewrite();
		}

		// Bummer, it's a real regex.
		return this;
	}

}
