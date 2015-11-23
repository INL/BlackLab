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

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;

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
	public SpanQueryPositionFilter(SpanQuery producer, SpanQuery filter, TextPatternPositionFilter.Operation op, boolean invert) {
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
	public SpanQueryPositionFilter(SpanQuery producer, SpanQuery filter, TextPatternPositionFilter.Operation op, boolean invert, int leftAdjust, int rightAdjust) {
		super(producer, filter);
		this.op = op;
		this.invert = invert;
		this.leftAdjust = leftAdjust;
		this.rightAdjust = rightAdjust;
	}

	/**
	 * Produce hits containing filter hits.
	 *
	 * @param producer hits we may be interested in
	 * @param filter how we determine what producer hits we're interested in
	 * @param invert produce hits that don't match filter instead?
	 * @deprecated specify operation explicitly
	 */
	@Deprecated
	public SpanQueryPositionFilter(SpanQuery producer, SpanQuery filter, boolean invert) {
		this(producer, filter, TextPatternPositionFilter.Operation.CONTAINING, invert, 0, 0);
	}

	@Override
	public Spans getSpans(LeafReaderContext context, Bits acceptDocs, Map<Term,TermContext> termContexts)  throws IOException {
		Spans spansProd = clauses[0].getSpans(context, acceptDocs, termContexts);
		Spans spansFilter = clauses[1].getSpans(context, acceptDocs, termContexts);
		Spans result = new SpansPositionFilter(spansProd, spansFilter, op, invert, leftAdjust, rightAdjust);
		return result;
	}

	@Override
	public String toString(String field) {
		String not = invert ? "not " : "";
		switch(op) {
		case WITHIN:
			return "SpanQueryContaining(" + clausesToString(field, " " + not + "within ") + ")";
		case CONTAINING:
			return "SpanQueryContaining(" + clausesToString(field, " " + not + "contains ") + ")";
		case ENDS_AT:
			return "SpanQueryContaining(" + clausesToString(field, " " + not + "ends at ") + ")";
		case STARTS_AT:
			return "SpanQueryContaining(" + clausesToString(field, " " + not + "start at ") + ")";
		case MATCHES:
			return "SpanQueryContaining(" + clausesToString(field, " " + not + "matches ") + ")";
		default:
			throw new RuntimeException("Unknown filter operation " + op);
		}
	}
}
