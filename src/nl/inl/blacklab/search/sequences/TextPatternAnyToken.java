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
package nl.inl.blacklab.search.sequences;

import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternTranslator;

/**
 * A 'gap' of a number of tokens we don't care about, with minimum and maximum length.
 *
 * This may be used to implement a 'wildcard' token in a pattern language.
 */
public class TextPatternAnyToken extends TextPattern {
	/*
	 * The minimum number of tokens in this stretch.
	 */
	protected int min;

	/*
	 * The maximum number of tokens in this stretch.
	 */
	protected int max;

	public TextPatternAnyToken(int min, int max) {
		this.min = min;
		this.max = max;
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, String fieldName) {
		throw new RuntimeException(
				"Cannot translate the any-token pattern, must be done by TextPatternSequence");
	}

	@Override
	public String toString() {
		return "*[" + min + "," + max + "]";
	}

	@Override
	public boolean matchesEmptySequence() {
		return min == 0;
	}

}
