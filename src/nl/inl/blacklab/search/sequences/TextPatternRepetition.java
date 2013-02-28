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

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternTranslator;

/**
 * Repetition of a pattern.
 */
public class TextPatternRepetition extends TextPattern {
	private TextPattern base;

	private int min;

	private int max;

	public TextPatternRepetition(TextPattern base, int min, int max) {
		this.base = base;
		this.min = min;
		this.max = max;
		if (max != -1 && min > max)
			throw new RuntimeException("min > max");
		if (min < 0)
			throw new RuntimeException("min < 0");
	}

	/**
	 * Repetition query matches the empty sequence iff min == 0.
	 */
	@Override
	public boolean matchesEmptySequence() {
		return min == 0;
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		T baseTranslated = base.translate(translator, context);

		int realMin = min;
		if (realMin == 0) {
			// This can happen if the whole query is optional, so
			// it's impossible to build an alternative without this clause.
			// In this case, min == 0 has no real meaning and we simply
			// behave the same as if min == 1.
			realMin = 1;
		}
		if (realMin == 1 && max == 1)
			return baseTranslated; // no repetition

		// NOTE: the case min == 0 is handled higher up the TextPattern hierarchy
		// (by checking matchesEmptySequence()). When translating, we just pretend this
		// case is equal to min == 1.
		// A repetition with min == 0 in isolation would not make sense anyway, only
		// in terms of surrounding patterns.
		return translator.repetition(baseTranslated, realMin, max);
	}

	@Override
	public String toString() {
		return "TextPatternRepetition(" + base + ", " + min + ", " + max + ")";
	}

}
