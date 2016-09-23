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
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;

/**
 * Filters hits from a producer query based on the hit positions of a filter query.
 * This allows us to do several things, such as:
 * * find hits from the producer that contain one or more hits from the filter
 * * find hits from the producer are contained by hit(s) from the filter
 * * find hits from the producer that start at the same position as a hit from the filter
 * * find hits from the producer that end at the same position as a hit from the filter
 */
public class SpanQueryPositionFilter extends BLSpanQueryAbstract {

	static SpanComparatorStartPoint cmpStartPoint = new SpanComparatorStartPoint();

	/** Filter operation to apply */
	private SpanQueryPositionFilter.Operation op;

	/** Return producer spans that DON'T match the filter instead? */
	private boolean invert;

	/** How to adjust the left edge of the producer hits while matching */
	private int leftAdjust;

	/** How to adjust the right edge of the producer hits while matching */
	private int rightAdjust;

	/**
	 * Produce hits that match filter hits.
	 *
	 * @param producer hits we may be interested in
	 * @param filter how we determine what producer hits we're interested in
	 * @param op operation used to determine what producer hits we're interested in (containing, within, startsat, endsat)
	 * @param invert produce hits that don't match filter instead?
	 */
	public SpanQueryPositionFilter(BLSpanQuery producer, BLSpanQuery filter, SpanQueryPositionFilter.Operation op, boolean invert) {
		this(producer, filter, op, invert, 0, 0);
	}

	/**
	 * Produce hits that match filter hits.
	 *
	 * @param producer hits we may be interested in
	 * @param filter how we determine what producer hits we're interested in
	 * @param op operation used to determine what producer hits we're interested in (containing, within, startsat, endsat)
	 * @param invert produce hits that don't match filter instead?
	 * @param leftAdjust how to adjust the left edge of the producer hits while matching
	 * @param rightAdjust how to adjust the right edge of the producer hits while matching
	 */
	public SpanQueryPositionFilter(BLSpanQuery producer, BLSpanQuery filter, SpanQueryPositionFilter.Operation op, boolean invert, int leftAdjust, int rightAdjust) {
		super(producer, filter);
		this.op = op;
		this.invert = invert;
		this.leftAdjust = leftAdjust;
		this.rightAdjust = rightAdjust;
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		BLSpanQuery producer = clauses.get(0).rewrite(reader);
		BLSpanQuery filter = clauses.get(1).rewrite(reader);

		if (!invert && op != SpanQueryPositionFilter.Operation.STARTS_AT && op != SpanQueryPositionFilter.Operation.ENDS_AT && producer instanceof SpanQueryAnyToken) {
			// We're filtering "all n-grams of length min-max".
			// Use the special optimized SpanQueryFilterNGrams.
			SpanQueryAnyToken tp = (SpanQueryAnyToken)producer;
			return new SpanQueryFilterNGrams(filter, op, tp.hitsLengthMin(), tp.hitsLengthMax());
		}

		if (producer != clauses.get(0) || filter != clauses.get(1)) {
			SpanQueryPositionFilter result = new SpanQueryPositionFilter(producer, filter, op, invert);
			result.leftAdjust = leftAdjust;
			result.rightAdjust = rightAdjust;
			return result;
		}
		return this;
	}

	@Override
	public BLSpanQuery combineWithPrecedingPart(BLSpanQuery previousPart, IndexReader reader) throws IOException {
		BLSpanQuery result = super.combineWithPrecedingPart(previousPart, reader);
		if (result == null && previousPart.hitsAllSameLength()) {
			// We "gobble up" the previous part and adjust our left matching edge.
			// This should make filtering more efficient, since we will likely have fewer hits to filter.
			SpanQueryPositionFilter r = (SpanQueryPositionFilter)copy();
			r.clauses.set(0, new SpanQuerySequence(previousPart, clauses.get(0)));
			r.adjustLeft(previousPart.hitsLengthMin());
			result = r;
		}
		return result;
	}

	@Override
	public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		BLSpanWeight prodWeight = clauses.get(0).createWeight(searcher, needsScores);
		BLSpanWeight filterWeight = clauses.get(1).createWeight(searcher, needsScores);
		Map<Term, TermContext> contexts = needsScores ? getTermContexts(prodWeight, filterWeight) : null;
		return new SpanWeightPositionFilter(prodWeight, filterWeight, searcher, contexts);
	}

	public class SpanWeightPositionFilter extends BLSpanWeight {

		final BLSpanWeight prodWeight, filterWeight;

		public SpanWeightPositionFilter(BLSpanWeight prodWeight, BLSpanWeight filterWeight, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQueryPositionFilter.this, searcher, terms);
			this.prodWeight = prodWeight;
			this.filterWeight = filterWeight;
		}

		@Override
		public void extractTerms(Set<Term> terms) {
			prodWeight.extractTerms(terms);
			filterWeight.extractTerms(terms);
		}

		@Override
		public void extractTermContexts(Map<Term, TermContext> contexts) {
			prodWeight.extractTermContexts(contexts);
			filterWeight.extractTermContexts(contexts);
		}

		@Override
		public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
			BLSpans spansProd = prodWeight.getSpans(context, requiredPostings);
			if (spansProd == null)
				return null;
			if (!clauses.get(0).hitsStartPointSorted())
				spansProd = new PerDocumentSortedSpans(spansProd, PerDocumentSortedSpans.cmpStartPoint, false);
			BLSpans spansFilter = filterWeight.getSpans(context, requiredPostings);
			if (spansFilter == null) {
				// No filter hits. If it's a positive filter, that means no producer hits can match.
				// If it's a negative filter, all producer hits match.
				return invert ? spansProd : null;
			}
			boolean filterFixedLength = clauses.get(1).hitsAllSameLength();
			SpansInBuckets filter;
			if (clauses.get(1).hitsStartPointSorted()) {
				// Already start point sorted; no need to sort buckets again
				filter = new SpansInBucketsPerDocument(spansFilter);
			} else {
				// Not sorted yet; sort buckets
				filter = new SpansInBucketsPerDocumentSorted(spansFilter, cmpStartPoint);
			}
			return new SpansPositionFilter(spansProd, filter, filterFixedLength, op, invert, leftAdjust, rightAdjust);
		}
	}

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

	@Override
	public String toString(String field) {
		String not = invert ? "NOT" : "";
		String adj = (leftAdjust != 0 || rightAdjust != 0 ? ", " + leftAdjust + ", " + rightAdjust : "");
		return "POSFILTER(" + clausesToString(field) + ", " + not + op + adj + ")";
	}

	public SpanQueryPositionFilter copy() {
		return new SpanQueryPositionFilter(clauses.get(0), clauses.get(1), op, invert, leftAdjust, rightAdjust);
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
	public boolean matchesEmptySequence() {
		return clauses.get(0).matchesEmptySequence();
	}

	@Override
	public BLSpanQuery noEmpty() {
		return new SpanQueryPositionFilter(clauses.get(0).noEmpty(), clauses.get(1), op, invert, leftAdjust, rightAdjust);
	}

	@Override
	public boolean hitsAllSameLength() {
		return clauses.get(0).hitsAllSameLength();
	}

	@Override
	public int hitsLengthMin() {
		return clauses.get(0).hitsLengthMin();
	}

	@Override
	public int hitsLengthMax() {
		return clauses.get(0).hitsLengthMax();
	}

	@Override
	public boolean hitsStartPointSorted() {
		return true;
	}

	@Override
	public boolean hitsEndPointSorted() {
		return clauses.get(0).hitsEndPointSorted();
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return clauses.get(0).hitsHaveUniqueStart();
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return clauses.get(0).hitsHaveUniqueEnd();
	}

	@Override
	public boolean hitsAreUnique() {
		return clauses.get(0).hitsAreUnique();
	}

	@Override
	public long estimatedNumberOfHits(IndexReader reader) {
		return clauses.get(0).estimatedNumberOfHits(reader);
	}
}
