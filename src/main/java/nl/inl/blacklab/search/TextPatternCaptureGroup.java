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
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		return translator.captureGroup(input.translate(translator, context), groupName);
	}

	@Override
	public TextPattern rewrite() {
		TextPattern rewritten = input.rewrite();
		if (rewritten == input)
			return this; // Nothing to rewrite
		return new TextPatternCaptureGroup(rewritten, groupName);
	}

	@Override
	public boolean matchesEmptySequence() {
		return input.matchesEmptySequence();
	}

	@Override
	public boolean equals(Object obj) {
		// Capture group clauses are unique.
		return false;
	}

	@Override
	public boolean hasConstantLength() {
		return input.hasConstantLength();
	}

	@Override
	public int getMinLength() {
		return input.getMinLength();
	}

	@Override
	public int getMaxLength() {
		return input.getMaxLength();
	}
}
