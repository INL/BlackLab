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

import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * TextPattern for wrapping another TextPattern so that it applies to a certain word property.
 *
 * For example, to find lemmas starting with "bla": <code>
 * TextPattern tp = new TextPatternProperty("lemma", new TextPatternWildcard("bla*"));
 * </code>
 */
public class TextPatternProperty extends TextPattern {
	private TextPattern input;

	private String propertyName;

	public TextPatternProperty(String propertyName, TextPattern input) {
		this.propertyName = propertyName == null ? "" : propertyName;
		this.input = input;
	}

	@Override
	public BLSpanQuery translate(QueryExecutionContext context) {
		return input.translate(context.withProperty(propertyName));
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternProperty) {
			TextPatternProperty tp = ((TextPatternProperty) obj);
			return input.equals(tp.input) && propertyName.equals(tp.propertyName);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return input.hashCode() + propertyName.hashCode();
	}

	@Deprecated
	@Override
	public String toString(QueryExecutionContext context) {
		return input.toString(context.withProperty(propertyName));
	}

	@Override
	public String toString() {
		return "PROP(" + propertyName + ", " + input.toString() + ")";
	}

}
