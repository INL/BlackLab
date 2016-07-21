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

import nl.inl.blacklab.search.sequences.TextPatternAnyToken;

/**
 * A textpattern matching a simple wildcard expression.
 *
 * Internally, the wildcard expression is translated into a regular expression.
 */
public class TextPatternWildcard extends TextPatternTerm {

	public TextPatternWildcard(String value) {
		super(value);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		return translator.wildcard(context, translator.optInsensitive(context, value));
	}

	/**
	 * Rewrite to the "best" TextPattern class for the given wildcard query. Tries to make a TextPatternTerm
	 * or TextPatternPrefix because those tend to be faster than TextPatternWildcard in Lucene.
	 *
	 * @return the TextPattern
	 */
	@Override
	public TextPattern rewrite() {
		// Hey, maybe it doesn't even contain wildcards?
		if (value.indexOf("*") < 0 && value.indexOf("?") < 0) {
			// Woot!
			return new TextPatternTerm(value);
		}

		// Replace multiple consecutive asterisks with a single one
		value = value.replaceAll("\\*+", "*");

		// Is is "any word"?
		if (value.equals("*"))
			return new TextPatternAnyToken(1, 1);

		// Is it a prefix query? ("bla*")
		if (value.indexOf('*') == value.length() - 1 && value.indexOf('?') < 0) {
			// Yes!
			String prefix = value.substring(0, value.length() - 1);
			return new TextPatternPrefix(prefix);
		}

		// Can't simplify, just return ourselves
		return this;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternWildcard) {
			return super.equals(obj);
		}
		return false;
	}

	@Override
	public String toString(QueryExecutionContext context) {
		return "WILDCARD(" + context.luceneField() + ", " + context.optDesensitize(value) + ")";
	}
}
