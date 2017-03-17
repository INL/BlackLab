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
package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.fimatch.NfaFragment;
import nl.inl.blacklab.search.fimatch.TokenPropMapper;

/**
 * A required interface for a BlackLab SpanQuery. All our queries must be
 * derived from this so we know they will produce BLSpans (which
 * contains extra methods for optimization).
 */
public abstract class BLSpanQuery extends SpanQuery {

	/**
	 * Rewrite a SpanQuery after rewrite() to a BLSpanQuery equivalent.
	 *
	 * This is used for BLSpanOrQuery and BLSpanMultiTermQueryWrapper: we
	 * let Lucene rewrite these for us, but the result needs to be BL-ified
	 * so we know we'll get BLSpans (which contain extra methods for optimization).
	 *
	 * @param spanQuery the SpanQuery to BL-ify (if it isn't a BLSpanQuery already)
	 * @return resulting BLSpanQuery, or the input query if it was one already
	 */
	public static BLSpanQuery wrap(SpanQuery spanQuery) {
		if (spanQuery instanceof BLSpanQuery) {
			// Already BL-derived, no wrapper needed.
			return (BLSpanQuery) spanQuery;
		} else if (spanQuery instanceof SpanOrQuery) {
			// Translate to a BLSpanOrQuery, recursively translating the clauses.
			return BLSpanOrQuery.from((SpanOrQuery) spanQuery);
		} else if (spanQuery instanceof SpanTermQuery) {
			// Translate to a BLSpanTermQuery.
			return BLSpanTermQuery.from((SpanTermQuery) spanQuery);
		} else {
			// After rewrite, we shouldn't encounter any other non-BLSpanQuery classes.
			throw new UnsupportedOperationException("Cannot BL-ify " + spanQuery.getClass().getSimpleName());
		}
	}

	@Override
	public abstract String toString(String field);

	@Override
	public abstract int hashCode();

	@Override
	public abstract boolean equals(Object obj);

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		return this;
	}

	@Override
	public abstract BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException;

	/**
	 * Does this query match the empty sequence?
	 *
	 * For example, the query [word="cow"]* matches the empty sequence. We need to know this so we
	 * can rewrite to the appropriate queries. A query of the form "AB*" would be translated into
	 * "A|AB+", so each component of the query actually generates non-empty matches.
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
	BLSpanQuery noEmpty() {
		if (!matchesEmptySequence())
			return this;
		throw new UnsupportedOperationException("noEmpty() must be implemented!");
	}

	/**
	 * Return an inverted version of this TextPattern.
	 *
	 * @return the inverted TextPattern
	 */
	BLSpanQuery inverted() {
		return new SpanQueryNot(this);
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
	 * @param reader the index reader
	 * @return a combined repetition text pattern, or null if it can't be combined
	 * @throws IOException
	 */
	BLSpanQuery combineWithPrecedingPart(BLSpanQuery previousPart, IndexReader reader) throws IOException {
		if (previousPart instanceof SpanQueryRepetition) {
			// Repetition clause.
			SpanQueryRepetition rep = (SpanQueryRepetition) previousPart;
			BLSpanQuery prevCl = rep.getClause();
			if (equals(prevCl)) {
				// Same clause; add one to rep's min and max
				return new SpanQueryRepetition(this, 1 + rep.getMinRep(), addRepetitionMaxValues(rep.getMaxRep(), 1));
			}
		}
		if (equals(previousPart)) {
			// Same clause; create repetition with min and max equals 2.
			return new SpanQueryRepetition(this, 2, 2);
		}
		if (previousPart instanceof SpanQueryAnyToken) {
			SpanQueryAnyToken tp = (SpanQueryAnyToken)previousPart;
			SpanQueryExpansion result = new SpanQueryExpansion(this, true, tp.hitsLengthMin(), tp.hitsLengthMax());
			result.setIgnoreLastToken(tp.getAlwaysHasClosingToken());
			return result;
		}
		if (previousPart instanceof SpanQueryExpansion) {
			SpanQueryExpansion tp = (SpanQueryExpansion)previousPart;
			if (tp.isExpandToLeft() && tp.getMinExpand() != tp.getMaxExpand()) {
				// Expand to left with a range of tokens. Combine with this part to likely
				// reduce the number of hits we'll have to expand.
				BLSpanQuery seq = new SpanQuerySequence(tp.getClause(), this);
				seq = seq.rewrite(reader);
				SpanQueryExpansion result = new SpanQueryExpansion(seq, true, tp.getMinExpand(), tp.getMaxExpand());
				result.setIgnoreLastToken(tp.ignoreLastToken);
				return result;
			}
		}
		if (hitsAllSameLength()) {
			if (previousPart instanceof SpanQueryPositionFilter) {
				// We are "gobbled up" by the previous part and adjust its right matching edge inward.
				// This should make filtering more efficient, since we will likely have fewer hits to filter.
				SpanQueryPositionFilter result = ((SpanQueryPositionFilter)previousPart).copy();
				result.clauses.set(0, new SpanQuerySequence(result.clauses.get(0), this));
				result.adjustRight(-hitsLengthMin());
				return result;
			}
			if (isSingleTokenNot() && previousPart.hitsAllSameLength()) {
				// Negative, single-token child after constant-length part.
				// Rewrite to NOTCONTAINING clause, incorporating previous part.
				int prevLen = previousPart.hitsLengthMin();
				BLSpanQuery container = new SpanQueryExpansion(previousPart, false, 1, 1);
				SpanQueryPositionFilter result = new SpanQueryPositionFilter(container, inverted(), SpanQueryPositionFilter.Operation.CONTAINING, true);
				result.adjustLeft(prevLen);
				return result;
			}
			if (previousPart.isSingleTokenNot()) {
				// Constant-length child after negative, single-token part.
				// Rewrite to NOTCONTAINING clause, incorporating previous part.
				int myLen = hitsLengthMin();
				BLSpanQuery container = new SpanQueryExpansion(this, true, 1, 1);
				SpanQueryPositionFilter result = new SpanQueryPositionFilter(container, previousPart.inverted(), SpanQueryPositionFilter.Operation.CONTAINING, true);
				result.adjustRight(-myLen);
				return result;
			}
		}

		return null;
	}

	/**
	 * Are all our hits single tokens?
	 * @return true if they are, false if not
	 */
	public boolean producesSingleTokens() {
		return hitsAllSameLength() && hitsLengthMin() == 1;
	}

	/**
	 * Do our hits have constant length?
	 * @return true if they do, false if not
	 */
	public abstract boolean hitsAllSameLength();

	/**
	 * How long could our shortest hit be?
	 * @return length of the shortest hit possible
	 */
	public abstract int hitsLengthMin();

	/**
	 * How long could our longest hit be?
	 * @return length of the longest hit possible, or Integer.MAX_VALUE if unlimited
	 */
	public abstract int hitsLengthMax();

	/**
	 * When hit B follows hit A, is it guaranteed that B.end &gt;= A.end?
	 * Also, if A.end == B.end, is B.start &gt; A.start?
	 *
	 * @return true if this is guaranteed, false if not
	 */
	public abstract boolean hitsEndPointSorted();

	/**
	 * When hit B follows hit A, is it guaranteed that B.start &gt;= A.start?
	 * Also, if A.start == B.start, is B.end &gt; A.end?
	 *
	 * @return true if this is guaranteed, false if not
	 */
	public abstract boolean hitsStartPointSorted();

	/**
	 * Is it guaranteed that no two hits have the same start position?
	 * @return true if this is guaranteed, false if not
	 */
	public abstract boolean hitsHaveUniqueStart();

	/**
	 * Is it guaranteed that no two hits have the same end position?
	 * @return true if this is guaranteed, false if not
	 */
	public abstract boolean hitsHaveUniqueEnd();

	/**
	 * Is it guaranteed that no two hits have the same start and end position?
	 * @return true if this is guaranteed, false if not
	 */
	public abstract boolean hitsAreUnique();

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
	static int addRepetitionMaxValues(int a, int b) {
		// Is either value infinite?
		if (a == -1 || b == -1)
			return -1; // Yes, result is infinite
		// Add regular values
		return a + b;
	}

	static <T extends SpanQuery> String clausesToString(String field, List<T> clauses) {
		StringBuilder b = new StringBuilder();
		for (T clause: clauses) {
			if (b.length() > 0)
				b.append(", ");
			b.append(clause.toString(field));
		}
		return b.toString();
	}

	@SafeVarargs
	static <T extends SpanQuery> String clausesToString(String field, T... clauses) {
		return clausesToString(field, Arrays.asList(clauses));
	}

	public static BLSpanQuery ensureSortedUnique(BLSpanQuery spanQuery) {
		if (spanQuery.hitsStartPointSorted()) {
			if (spanQuery.hitsAreUnique())
				return spanQuery;
			return new SpanQueryUnique(spanQuery);
		}
		return new SpanQuerySorted(spanQuery, false, !spanQuery.hitsAreUnique());
	}

	public static BLSpanQuery ensureSorted(BLSpanQuery spanQuery) {
		if (spanQuery.hitsStartPointSorted()) {
			return spanQuery;
		}
		return new SpanQuerySorted(spanQuery, false, false);
	}

	public NfaFragment getNfa(TokenPropMapper propMapper, int direction) {
		throw new UnsupportedOperationException("Cannot create NFA; query should have been rewritten or cannot be matched using forward index");
	}

	public boolean canMakeNfa() {
		return false;
	}

	/**
	 * Return an very rough indication of how many hits this
	 * clause might return.
	 *
	 * Used to decide what parts of the query
	 * to match using the forward index.
	 *
	 * Based on term frequency, which are combined using simple
	 * rules of thumb.
	 *
	 * Another way to think of this is an indication of how much
	 * computation this clause will require when matching using the
	 * reverse index.
	 *
	 * @param reader the index reader
	 *
	 * @return rough estimation of the number of hits
	 */
	public abstract long estimatedNumberOfHits(IndexReader reader);

	@Override
	public String getField() {
		// Return only base name of complex field!
		return ComplexFieldUtil.getBaseName(getRealField());
	}

	public abstract String getRealField();

}
