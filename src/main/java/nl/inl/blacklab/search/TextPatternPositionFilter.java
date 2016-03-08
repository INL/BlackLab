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
import nl.inl.blacklab.search.sequences.TextPatternFilterNGrams;
import nl.inl.blacklab.search.sequences.TextPatternSequence;

/**
 * A TextPattern searching for TextPatterns that contain a hit from another TextPattern. This may be
 * used to search for sentences containing a certain word, etc.
 */
public class TextPatternPositionFilter extends TextPatternCombiner {

	/** The different positional operations */
	public enum Operation {

		/** Producer hit contains filter hit */
		CONTAINING,

		/** Producer hit contained in filter hit */
		WITHIN,

		/** Producer hit starts at filter hit */
		STARTS_AT,

		/** Producer hit ends at filter hit */
		ENDS_AT,

		/** Producer hit exactly matches filter hit */
		MATCHES,

		/** Producer hit contains filter hit, at its end */
		CONTAINING_AT_START,

		/** Producer hit contains filter hit, at its start*/
		CONTAINING_AT_END

	}

	/** The hits we're (possibly) looking for */
	TextPatternPositionFilter.Operation op;

	/** Whether to invert the filter operation */
	boolean invert;

	/** How to adjust the left edge of the producer hits while matching */
	int leftAdjust = 0;

	/** How to adjust the right edge of the producer hits while matching */
	int rightAdjust = 0;

	public TextPatternPositionFilter(TextPattern producer, TextPattern filter, TextPatternPositionFilter.Operation op) {
		this(producer, filter, op, false);
	}

	public TextPatternPositionFilter(TextPattern producer, TextPattern filter, TextPatternPositionFilter.Operation op, boolean invert) {
		super(producer, filter);
		this.op = op;
		this.invert = invert;
	}

	/**
	 * Adjust the left edge of the producer hits for matching only.
	 *
	 * That is, the original producer hit is returned, not the adjusted one.
	 *
	 * @param delta how to adjust the edge
	 */
	public void adjustLeft(int delta) {
		leftAdjust += delta;
	}

	/**
	 * Adjust the right edge of the producer hits for matching only.
	 *
	 * That is, the original producer hit is returned, not the adjusted one.
	 *
	 * @param delta how to adjust the edge
	 */
	public void adjustRight(int delta) {
		rightAdjust += delta;
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		T trContainers = clauses.get(0).translate(translator, context);
		T trSearch = clauses.get(1).translate(translator, context);
		return translator.positionFilter(context, trContainers, trSearch, op, invert, leftAdjust, rightAdjust);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternPositionFilter) {
			return super.equals(obj) && ((TextPatternPositionFilter)obj).invert == invert;
		}
		return false;
	}

	@Override
	public boolean hasConstantLength() {
		return clauses.get(0).hasConstantLength();
	}

	@Override
	public int getMinLength() {
		return clauses.get(0).getMinLength();
	}

	@Override
	public int getMaxLength() {
		return clauses.get(0).getMaxLength();
	}

	@Override
	public TextPattern combineWithPrecedingPart(TextPattern previousPart) {
		if (previousPart.hasConstantLength()) {
			// We "gobble up" the previous part and adjust our left matching edge.
			// This should make filtering more efficient, since we will likely have fewer hits to filter.
			TextPatternPositionFilter result = (TextPatternPositionFilter)clone();
			result.clauses.set(0, new TextPatternSequence(previousPart, clauses.get(0)));
			result.adjustLeft(previousPart.getMinLength());
			return result;
		}
		return super.combineWithPrecedingPart(previousPart);
	}

	@Override
	public TextPattern rewrite() {
		TextPattern producer = clauses.get(0).rewrite();
		TextPattern filter = clauses.get(1).rewrite();

		if (!invert && op != Operation.STARTS_AT && op != Operation.ENDS_AT && producer instanceof TextPatternAnyToken) {
			// We're filtering "all n-grams of length min-max".
			// Use the special optimized TextPatternFilterNGrams.
			TextPatternAnyToken tp = (TextPatternAnyToken)producer;
			return new TextPatternFilterNGrams(filter, op, tp.getMinLength(), tp.getMaxLength());
		}

		if (producer != clauses.get(0) || filter != clauses.get(1)) {
			TextPatternPositionFilter result = new TextPatternPositionFilter(producer, filter, op, invert);
			result.leftAdjust = leftAdjust;
			result.rightAdjust = rightAdjust;
			return result;
		}
		return this;
	}

	@Override
	public int hashCode() {
		return super.hashCode() + op.hashCode() + (invert ? 13 : 0) + leftAdjust * 31 + rightAdjust * 37;
	}

	@Override
	public String toString(QueryExecutionContext context) {
		String producer = clauses.get(0).toString(context);
		String filter = clauses.get(1).toString(context);
		String adj = (leftAdjust != 0 || rightAdjust != 0 ? ", " + leftAdjust + ", " + rightAdjust : "");
		return "POSFILTER(" + producer + ", " + filter + ", " + (invert ? "NOT" : "") + op + adj + ")";
	}
}
