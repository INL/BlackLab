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
import nl.inl.blacklab.search.TextPatternPositionFilter;
import nl.inl.blacklab.search.TextPatternPositionFilter.Operation;
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
	 * Repetition query matches the empty sequence iff min == 0 or its
	 * base clause matches the empty sequence.
	 */
	@Override
	public boolean matchesEmptySequence() {
		return min == 0 || base.matchesEmptySequence();
	}

	@Override
	public TextPattern noEmpty() {
		if (!matchesEmptySequence())
			return this;
		int newMin = min == 0 ? 1 : min;
		return new TextPatternRepetition(base.noEmpty(), newMin, max);
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
	public TextPattern rewrite() {
		TextPattern baseRewritten = base.rewrite();
		if (min == 1 && max == 1)
			return baseRewritten;
		if (baseRewritten instanceof TextPatternAnyToken) {
			// Repeating anytoken clause can sometimes be expressed as simple anytoken clause
			TextPatternAnyToken tp = (TextPatternAnyToken)baseRewritten;
			if (tp.min == 1 && tp.max == 1) {
				// Repeat of a single any token
				return new TextPatternAnyToken(min, max);
			} else if (min == max && tp.min == tp.max) {
				// Exact number of any tokens
				int n = min * tp.min;
				return new TextPatternAnyToken(n, n);
			}
		} else if (baseRewritten.isSingleTokenNot() && min > 0) {
			// Rewrite to anytokens-not-containing form so we can optimize it
			// (note the check for min > 0 above, because position filter cannot match the empty sequence)
			int l = baseRewritten.getMinLength();
			TextPattern container = new TextPatternRepetition(new TextPatternAnyToken(l, l), min, max);
			container = container.rewrite();
			return new TextPatternPositionFilter(container, baseRewritten.inverted(), Operation.CONTAINING, true);
		} else if (baseRewritten instanceof TextPatternRepetition) {
			TextPatternRepetition tp = (TextPatternRepetition)baseRewritten;
			if (max == -1 && tp.max == -1) {
				if (min >= 0 && min <= 1 && tp.min >= 0 && tp.min <= 1) {
					// A++, A+*, A*+, A**. Rewrite to single repetition.
					return new TextPatternRepetition(tp.base, min * tp.min, max);
				}
			} else {
				if (min == 0 && max == 1 && tp.min == 0 && tp.max == 1) {
					// A?? == A?
					return tp;
				}
				if (min == 1 && max == 1) {
					// A{x,y}{1,1} == A{x,y}
					return new TextPatternRepetition(tp.base, tp.min, tp.max);
				}
				// (other cases like A{1,1}{x,y} should have been rewritten already)
			}
		}
		if (baseRewritten == base)
			return this;
		return new TextPatternRepetition(baseRewritten, min, max);
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
	public TextPattern combineWithPrecedingPart(TextPattern previousPart) {
		if (previousPart instanceof TextPatternRepetition) {
			// Repetition clause.
			TextPatternRepetition rep = (TextPatternRepetition) previousPart;
			TextPattern prevCl = rep.getClause();
			if (prevCl.equals(base)) {
				// Same clause; combine repetitions
				return new TextPatternRepetition(base, min + rep.getMin(), addRepetitionMaxValues(rep.getMax(), max));
			}
		} else {
			if (previousPart.equals(base)) {
				// Same clause; add one to min and max
				return new TextPatternRepetition(base, min + 1, addRepetitionMaxValues(max, 1));
			}
		}
		return super.combineWithPrecedingPart(previousPart);
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
	public boolean hasConstantLength() {
		return base.hasConstantLength() && min == max;
	}

	@Override
	public int getMinLength() {
		return base.getMinLength() * min;
	}

	@Override
	public int getMaxLength() {
		return max < 0 ? -1 : base.getMaxLength() * max;
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
