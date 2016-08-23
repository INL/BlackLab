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

/**
 * A TextPattern matching words that start with the specified prefix.
 */
public class TextPatternPrefix extends TextPatternTerm {
	public TextPatternPrefix(String value) {
		super(value);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		return translator.prefix(context, translator.optInsensitive(context, value));
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternPrefix) {
			return super.equals(obj);
		}
		return false;
	}

	@Deprecated
	@Override
	public String toString(QueryExecutionContext context) {
		return "PREFIX(" + context.luceneField() + ", " + context.optDesensitize(value) + ")";
	}

	@Override
	public String toString() {
		return "PREFIX(" + value + ")";
	}
}
