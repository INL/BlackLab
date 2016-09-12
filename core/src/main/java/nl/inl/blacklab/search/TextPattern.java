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

import java.util.List;

import nl.inl.blacklab.search.TextPatternPositionFilter.Operation;

/**
 * Describes some pattern of words in a content field. The point of this interface is to provide an
 * abstract layer to describe the pattern we're interested in, which can then be translated into,
 * for example, a SpanQuery object or a String, depending on our needs.
 */
public abstract class TextPattern implements Cloneable {
	/**
	 * Default constructor; does nothing.
	 */
	public TextPattern() {
		//
	}

	/**
	 * Translate this TextPattern into some other representation.
	 *
	 * For example, TextPatternTranslatorSpanQuery translates it into Lucene SpanQuerys.
	 *
	 * @param translator
	 *            the translator to use
	 * @param context
	 *            query execution context to use
	 *
	 * @param <T>
	 *            type of object to translate to
	 * @return result of the translation
	 */
	public abstract <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context);

	/**
	 * Translate this TextPattern into some other representation.
	 *
	 * For example, TextPatternTranslatorSpanQuery translates it into Lucene SpanQuerys.
	 *
	 * Uses the searcher's initial query execution context.
	 *
	 * @param translator
	 *            the translator to use
	 * @param searcher
	 * 			  our searcher, to get the inital query execution context from
	 *
	 *
	 * @param <T>
	 *            type of object to translate to
	 * @return result of the translation
	 */
	public <T> T translate(TextPatternTranslator<T> translator, Searcher searcher) {
		return translate(translator, searcher.getDefaultExecutionContext());
	}

	@Override
	public abstract String toString();

	/**
	 * @param searcher searcher object
	 * @return string representation
	 * @deprecated toString() shouldn't depend on external information. Use {@link #toString()}.
	 */
	@Deprecated
	public String toString(Searcher searcher) {
		return toString(searcher.getDefaultExecutionContext());
	}

	/**
	 * @param searcher searcher object
	 * @param fieldName contents field name
	 * @return string representation
	 * @deprecated toString() shouldn't depend on external information. Use {@link #toString()}.
	 */
	@Deprecated
	public String toString(Searcher searcher, String fieldName) {
		return toString(searcher.getDefaultExecutionContext());
	}

	/**
	 * @param context the execution context
	 * @return string representation
	 * @deprecated toString() shouldn't depend on external information. Use {@link #toString()}.
	 */
	@Deprecated
	public abstract String toString(QueryExecutionContext context);

	protected String clausesToString(List<TextPattern> clauses) {
		StringBuilder b = new StringBuilder();
		for (TextPattern clause: clauses) {
			if (b.length() > 0)
				b.append(", ");
			b.append(clause.toString());
		}
		return b.toString();
	}

	@Deprecated
	protected String clausesToString(List<TextPattern> clauses,
			QueryExecutionContext context) {
		StringBuilder b = new StringBuilder();
		for (TextPattern clause: clauses) {
			if (b.length() > 0)
				b.append(", ");
			b.append(context == null ? clause.toString() : clause.toString(context));
		}
		return b.toString();
	}

	/**
	 * Rewrite the TextPattern before translation.
	 *
	 * This changes the structure of certain queries so they can be executed
	 * more efficiently.
	 *
	 * @return either the original TextPattern (if no rewriting was necessary),
	 * or the rewritten TextPattern
	 */
	public TextPattern rewrite() {
		return this;
	}

	/**
	 * Does this TextPattern match the empty sequence?
	 *
	 * For example, the query [word="cow"]* matches the empty sequence. We need to know this so we
	 * can generate the appropriate queries. A query of the form "AB*" would be translated into
	 * "A|AB+", so each component of the query actually generates non-empty matches.
	 *
	 * When translating, the translator will usually generate a query that doesn't match the empty
	 * sequence, because this is not a practical query to execute. It is up to the translator to
	 * make sure the empty-sequence-matching property of the pattern is correctly dealt with at a
	 * higher level (usually by executing two alternative queries, one with and one without the part
	 * that would match the empty sequence). It uses this method to do so.
	 *
	 * We default to no because most queries don't match the empty sequence.
	 *
	 * @return true if this pattern matches the empty sequence, false otherwise
	 */
	public boolean matchesEmptySequence() {
		return false;
	}

	/**
	 * Return a version of this clause that cannot match the empty sequence.
	 * @return a version that doesn't match the empty sequence
	 */
	TextPattern noEmpty() {
		if (!matchesEmptySequence())
			return this;
		throw new UnsupportedOperationException("noEmpty() must be implemented!");
	}

	/**
	 * Return an inverted version of this TextPattern.
	 *
	 * @return the inverted TextPattern
	 */
	public TextPattern inverted() {
		return new TextPatternNot(this);
	}

	/**
	 * Is it okay to invert this TextPattern for optimization?
	 *
	 * Heuristic used to determine when to optimize
	 * a query by inverting one or more of its subqueries.
	 *
	 * @return true if it is, false if not
	 */
	boolean okayToInvertForOptimization() {
		return false;
	}

	/**
	 * Is this (sub)pattern only a negative clause, producing all tokens that
	 * don't satisfy certain conditions?
	 *
	 * Used for optimization decisions, i.e. in TextPatternOr.rewrite().
	 *
	 * @return true if it's negative-only, false if not
	 */
	boolean isSingleTokenNot() {
		return false;
	}

	/**
	 * Try to combine with the previous part into a repetition pattern.
	 *
	 * This optimized queries like "blah" "blah" into "blah"{2}, which
	 * executes more efficiently.
	 *
	 * @param previousPart the part occurring before this one in a sequence
	 * @return a combined repetition text pattern, or null if it can't be combined
	 */
	TextPattern combineWithPrecedingPart(TextPattern previousPart) {
		if (previousPart instanceof TextPatternRepetition) {
			// Repetition clause.
			TextPatternRepetition rep = (TextPatternRepetition) previousPart;
			TextPattern prevCl = rep.getClause();
			if (equals(prevCl)) {
				// Same clause; add one to rep's min and max
				return new TextPatternRepetition(this, 1 + rep.getMin(), addRepetitionMaxValues(rep.getMax(), 1));
			}
		}
		if (equals(previousPart)) {
			// Same clause; create repetition with min and max equals 2.
			return new TextPatternRepetition(this, 2, 2);
		}
		if (previousPart instanceof TextPatternAnyToken) {
			TextPatternAnyToken tp = (TextPatternAnyToken)previousPart;
			return new TextPatternExpansion(this, true, tp.getMinLength(), tp.getMaxLength());
		}
		if (previousPart instanceof TextPatternExpansion) {
			TextPatternExpansion tp = (TextPatternExpansion)previousPart;
			if (tp.isExpandToLeft() && tp.getMinExpand() != tp.getMaxExpand()) {
				// Expand to left with a range of tokens. Combine with this part to likely
				// reduce the number of hits we'll have to expand.
				TextPattern seq = new TextPatternSequence(tp.getClause(), this);
				seq = seq.rewrite();
				return new TextPatternExpansion(seq, true, tp.getMinExpand(), tp.getMaxExpand());
			}
		}
		if (hasConstantLength()) {
			if (previousPart instanceof TextPatternPositionFilter) {
				// We are "gobbled up" by the previous part and adjust its right matching edge inward.
				// This should make filtering more efficient, since we will likely have fewer hits to filter.
				try {
					TextPatternPositionFilter result = (TextPatternPositionFilter)previousPart.clone();
					result.clauses.set(0, new TextPatternSequence(result.clauses.get(0), this));
					result.adjustRight(-getMinLength());
					return result;
				} catch (CloneNotSupportedException e) {
					throw new RuntimeException(e);
				}
			}
			if (isSingleTokenNot() && previousPart.hasConstantLength()) {
				// Negative, single-token child after constant-length part.
				// Rewrite to NOTCONTAINING clause, incorporating previous part.
				int prevLen = previousPart.getMinLength();
				TextPattern container = new TextPatternExpansion(previousPart, false, 1, 1);
				TextPatternPositionFilter result = new TextPatternPositionFilter(container, inverted(), Operation.CONTAINING, true);
				result.adjustLeft(prevLen);
				return result;
			}
			if (previousPart.isSingleTokenNot()) {
				// Constant-length child after negative, single-token part.
				// Rewrite to NOTCONTAINING clause, incorporating previous part.
				int myLen = getMinLength();
				TextPattern container = new TextPatternExpansion(this, true, 1, 1);
				TextPatternPositionFilter result = new TextPatternPositionFilter(container, previousPart.inverted(), Operation.CONTAINING, true);
				result.adjustRight(-myLen);
				return result;
			}
		}

		return null;
	}

	public boolean producesSingleTokens() {
		return hasConstantLength() && getMinLength() == 1;
	}

	public abstract boolean hasConstantLength();

	public abstract int getMinLength();

	public abstract int getMaxLength();

	/**
	 * Add two values for maximum number of repetitions, taking "infinite" into account.
	 *
	 * -1 repetitions means infinite. Adding infinite to any other value
	 * produces infinite again.
	 *
	 * @param a first max. repetitions value
	 * @param b first max. repetitions value
	 * @return sum of the max. repetitions values
	 */
	protected static int addRepetitionMaxValues(int a, int b) {
		// Is either value infinite?
		if (a == -1 || b == -1)
			return -1; // Yes, result is infinite
		// Add regular values
		return a + b;
	}

	@Override
	public abstract boolean equals(Object obj);

	@Override
	public abstract int hashCode();

}
