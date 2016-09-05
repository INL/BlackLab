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

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.search.TextPatternPositionFilter.Operation;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.SpanQueryBase;
import nl.inl.blacklab.search.lucene.SpansUnique;

/**
 * Expands the source spans to the left and right by the given ranges.
 *
 * This is used to support sequences including subsequences of completely unknown tokens (like
 * "apple" []{2, 4} "pear" to find apple and pear with 2 to 4 tokens in between).
 *
 * Note that this class will generate all possible expansions, so if you call it with left-expansion
 * of between 2 to 4 tokens, it will generate 3 new hits for every hit from the source spans: one
 * hit with 2 more tokens to the left, one hit with 3 more tokens to the left, and one hit with 4
 * more tokens to the left.
 *
 * Spans generated from this query will be sorted by start point and then by end point, and any
 * duplicates generated will be discarded.
 */
public class SpanQueryFilterNGrams extends SpanQueryBase {

	/** How to expand the hits */
	private Operation op;

	/** Minimum number of tokens to expand */
	private int min;

	/** Maximum number of tokens to expand (-1 = infinite) */
	private int max;

	/** if true, we assume the last token is always a special closing token and ignore it */
	boolean ignoreLastToken = false;

	public SpanQueryFilterNGrams(SpanQuery clause, Operation op, int min, int max) {
		super(clause);
		this.op = op;
		this.min = min;
		this.max = max;
		if (max != -1 && min > max)
			throw new IllegalArgumentException("min > max");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!super.equals(o))
			return false;

		final SpanQueryFilterNGrams that = (SpanQueryFilterNGrams) o;
		return op == that.op && min == that.min && max == that.max;
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		SpanWeight weight = clauses[0].createWeight(searcher, needsScores);
		return new SpanWeightFilterNGrams(weight, searcher, needsScores ? getTermContexts(weight) : null);
	}

	public class SpanWeightFilterNGrams extends SpanWeight {

		final SpanWeight weight;

		public SpanWeightFilterNGrams(SpanWeight weight, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQueryFilterNGrams.this, searcher, terms);
			this.weight = weight;
		}

		@Override
		public void extractTerms(Set<Term> terms) {
			weight.extractTerms(terms);
		}

		@Override
		public void extractTermContexts(Map<Term, TermContext> contexts) {
			weight.extractTermContexts(contexts);
		}

		@Override
		public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
			Spans spansSource = weight.getSpans(context, requiredPostings);
			if (spansSource == null)
				return null;
			BLSpans spans = new SpansFilterNGramsRaw(ignoreLastToken, context.reader(), clauses[0].getField(), spansSource, op, min, max);

			// Note: the spans coming from SpansExpansion are not sorted properly.
			// Before returning the final spans, we wrap it in a per-document (start-point) sorter.

			// Sort the resulting spans by start point.
			// Note that duplicates may have formed by combining spans from left and right. Eliminate
			// these duplicates now (hence the 'true').
			boolean sorted = spans.hitsStartPointSorted();
			boolean unique = spans.hitsAreUnique();
			if (!sorted) {
				return new PerDocumentSortedSpans(spans, false, !unique);
			} else if (!unique) {
				return new SpansUnique(spans);
			}
			return spans;
		}

	}

	@Override
	public int hashCode() {
		int h = clauses.hashCode();
		h ^= (h << 10) | (h >>> 23);
		h ^= min << 10;
		h ^= max << 5;
		h ^= op.hashCode();
		h ^= Float.floatToRawIntBits(getBoost());
		return h;
	}

	@Override
	public String toString(String field) {
		return "SpanQueryExpansion(" + clauses[0] + ", " + op + ", " + min + ", " + max
				+ ")";
	}

	/** Set whether to ignore the last token.
	 *
	 * @param ignoreLastToken if true, we assume the last token is always a special closing token and ignore it
	 */
	public void setIgnoreLastToken(boolean ignoreLastToken) {
		this.ignoreLastToken = ignoreLastToken;
	}

}
