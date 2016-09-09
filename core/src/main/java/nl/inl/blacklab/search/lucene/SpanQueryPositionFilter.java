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
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.search.TextPatternPositionFilter;

/**
 * Filters hits from a producer query based on the hit positions of a filter query.
 * This allows us to do several things, such as:
 * * find hits from the producer that contain one or more hits from the filter
 * * find hits from the producer are contained by hit(s) from the filter
 * * find hits from the producer that start at the same position as a hit from the filter
 * * find hits from the producer that end at the same position as a hit from the filter
 */
public class SpanQueryPositionFilter extends SpanQueryBase {

	/** Filter operation to apply */
	private TextPatternPositionFilter.Operation op;

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
	public SpanQueryPositionFilter(BLSpanQuery producer, BLSpanQuery filter, TextPatternPositionFilter.Operation op, boolean invert) {
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
	public SpanQueryPositionFilter(BLSpanQuery producer, BLSpanQuery filter, TextPatternPositionFilter.Operation op, boolean invert, int leftAdjust, int rightAdjust) {
		super(producer, filter);
		this.op = op;
		this.invert = invert;
		this.leftAdjust = leftAdjust;
		this.rightAdjust = rightAdjust;
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		BLSpanQuery[] rewritten = rewriteClauses(reader);
		return rewritten == null ? this : new SpanQueryPositionFilter(rewritten[0], rewritten[1], op, invert, leftAdjust, rightAdjust);
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		SpanWeight prodWeight = clauses[0].createWeight(searcher, needsScores);
		SpanWeight filterWeight = clauses[1].createWeight(searcher, needsScores);
		Map<Term, TermContext> contexts = needsScores ? getTermContexts(prodWeight, filterWeight) : null;
		return new SpanWeightPositionFilter(prodWeight, filterWeight, searcher, contexts);
	}

	public class SpanWeightPositionFilter extends SpanWeight {

		final SpanWeight prodWeight, filterWeight;

		public SpanWeightPositionFilter(SpanWeight prodWeight, SpanWeight filterWeight, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
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
		public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
			Spans spansProd = prodWeight.getSpans(context, requiredPostings);
			if (spansProd == null)
				return null;
			Spans spansFilter = filterWeight.getSpans(context, requiredPostings);
			if (spansFilter == null) {
				// No filter hits. If it's a positive filter, that means no producer hits can match.
				// If it's a negative filter, all producer hits match.
				return invert ? spansProd : null;
			}
			return new SpansPositionFilter(spansProd, spansFilter, op, invert, leftAdjust, rightAdjust);
		}
	}

	@Override
	public String toString(String field) {
		String not = invert ? "NOT" : "";
		String adj = (leftAdjust != 0 || rightAdjust != 0 ? ", " + leftAdjust + ", " + rightAdjust : "");
		switch(op) {
		case WITHIN:
			return "SQPositionFilter(" + clausesToString(field) + ", " + not + "WITHIN" + adj + ")";
		case CONTAINING:
			return "SQPositionFilter(" + clausesToString(field) + ", " + not + "CONTAINING" + adj + ")";
		case ENDS_AT:
			return "SQPositionFilter(" + clausesToString(field) + ", " + not + "ENDS_AT" + adj + ")";
		case STARTS_AT:
			return "SQPositionFilter(" + clausesToString(field) + ", " + not + "STARTS_AT" + adj + ")";
		case MATCHES:
			return "SQPositionFilter(" + clausesToString(field) + ", " + not + "MATCHES" + adj + ")";
		default:
			throw new IllegalArgumentException("Unknown filter operation " + op);
		}
	}
}
