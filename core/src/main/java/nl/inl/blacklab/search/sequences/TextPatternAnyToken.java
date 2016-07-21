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

	public TextPattern repeat(int nmin, int nmax) {
		if (nmin == 1 && nmax == 1)
			return this;
		if (min == 1 && max == 1) {
			return new TextPatternAnyToken(nmin, nmax);
		}
		return new TextPatternRepetition(this, nmin, nmax);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		int realMin = min;
		if (realMin == 0) {
			// This can happen if the whole query is optional, so
			// it's impossible to build an alternative without this clause.
			// In this case, min == 0 has no real meaning and we simply
			// behave the same as if min == 1.
			realMin = 1;
		}
		return translator.any(context, realMin, max);

//		if (realMin == 1 && max == 1)
//			return any;
//
//		return translator.repetition(any, realMin, max);
	}

	@Override
	public boolean matchesEmptySequence() {
		return min == 0;
	}

	@Override
	public TextPattern noEmpty() {
		if (min > 0)
			return this;
		return new TextPatternAnyToken(1, max);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternAnyToken) {
			TextPatternAnyToken tp = ((TextPatternAnyToken) obj);
			return min == tp.min && max == tp.max;
		}
		return false;
	}

	@Override
	public TextPattern combineWithPrecedingPart(TextPattern previousPart) {
		if (previousPart instanceof TextPatternAnyToken) {
			TextPatternAnyToken tp = (TextPatternAnyToken)previousPart;
			return new TextPatternAnyToken(min + tp.min, (max == -1 || tp.max == -1) ? -1 : max + tp.max);
		} else if (previousPart instanceof TextPatternExpansion) {
			TextPatternExpansion tp = (TextPatternExpansion) previousPart;
			if (!tp.expandToLeft) {
				// Any token clause after expand to right; combine.
				return new TextPatternExpansion(tp.clause, tp.expandToLeft, tp.min + min, (max == -1 || tp.max == -1) ? -1 : tp.max + max);
			}
		}
		TextPattern combo = super.combineWithPrecedingPart(previousPart);
		if (combo == null) {
			combo = new TextPatternExpansion(previousPart, false, min, max);
		}
		return combo;
	}

	@Override
	public boolean hasConstantLength() {
		return min == max;
	}

	@Override
	public int getMinLength() {
		return min;
	}

	@Override
	public int getMaxLength() {
		return max;
	}

	@Override
	public int hashCode() {
		return min + 31 * max;
	}

	@Override
	public String toString(QueryExecutionContext context) {
		return "ANYTOKEN(" + min + ", " + max + ")";
	}
}
