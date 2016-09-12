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
import nl.inl.blacklab.search.lucene.SpanQueryRepetition;

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
			throw new IllegalArgumentException("min > max");
		if (min < 0)
			throw new IllegalArgumentException("min < 0");
	}

	@Override
	public BLSpanQuery translate(QueryExecutionContext context) {
		BLSpanQuery baseTranslated = base.translate(context);

		if (min == 1 && max == 1)
			return baseTranslated; // no repetition

		return new SpanQueryRepetition(baseTranslated, min, max);
	}

	public TextPattern getClause() {
		return base;
	}

	public int getMin() {
		return min;
	}

	public int getMax() {
		return max;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternRepetition) {
			TextPatternRepetition tp = ((TextPatternRepetition) obj);
			return base.equals(tp.base) && min == tp.min && max == tp.max;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return base.hashCode() + 13 * min + 31 * max;
	}

	@Deprecated
	@Override
	public String toString(QueryExecutionContext context) {
		return "REP(" + base.toString(context) + ", " + min + ", " + max + ")";
	}

	@Override
	public String toString() {
		return "REP(" + base.toString() + ", " + min + ", " + max + ")";
	}

}
