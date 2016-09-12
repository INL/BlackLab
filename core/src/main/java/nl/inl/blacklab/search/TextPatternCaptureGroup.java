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
import nl.inl.blacklab.search.lucene.SpanQueryCaptureGroup;

/**
 * TextPattern for capturing a subclause as a named "group".
 */
public class TextPatternCaptureGroup extends TextPattern {

	private TextPattern input;

	private String groupName;

	/**
	 * Indicate that we want to use a different list of alternatives for this
	 * part of the query.
	 * @param input the clause to tag with this name
	 * @param groupName the tag name
	 */
	public TextPatternCaptureGroup(TextPattern input, String groupName) {
		this.input = input;
		this.groupName = groupName;
	}

	@Override
	public BLSpanQuery translate(QueryExecutionContext context) {
		return new SpanQueryCaptureGroup(input.translate(context), groupName);
	}

	@Override
	public boolean equals(Object obj) {
		// Capture group clauses are unique.
		return obj == this;
	}

	@Override
	public int hashCode() {
		return input.hashCode() + groupName.hashCode();
	}

	@Deprecated
	@Override
	public String toString(QueryExecutionContext context) {
		return "CAPTURE(" + input.toString(context) + ", " + groupName + ")";
	}

	@Override
	public String toString() {
		return "CAPTURE(" + input.toString() + ", " + groupName + ")";
	}

}
