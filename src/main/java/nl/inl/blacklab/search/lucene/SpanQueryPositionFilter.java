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

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;

/**
 * Filters hits from a producer query based on the hit positions of a filter query.
 * This allows us to do several things, such as:
 * * find hits from the producer that contain one or more hits from the filter
 * * find hits from the producer are contained by hit(s) from the filter
 * * find hits from the producer that start at the same position as a hit from the filter
 * * find hits from the producer that end at the same position as a hit from the filter
 */
public class SpanQueryPositionFilter extends SpanQueryBase {

	/** The different positional operations */
	public static enum Filter {

		/** Producer hit contains filter hit */
		CONTAINING,

		/** Producer hit contained in filter hit */
		WITHIN,

		/** Producer hit starts at filter hit */
		STARTS_AT,

		/** Producer hit ends at filter hit */
		ENDS_AT
	}

	/** Filter operation to apply */
	private Filter op;

	public SpanQueryPositionFilter(SpanQuery producer, SpanQuery filter, Filter op) {
		super(producer, filter);
		this.op = op;
	}

	public SpanQueryPositionFilter(SpanQuery producer, SpanQuery filter) {
		this(producer, filter, Filter.CONTAINING);
	}

	@Override
	public Spans getSpans(AtomicReaderContext context, Bits acceptDocs, Map<Term,TermContext> termContexts)  throws IOException {
		Spans result = new SpansPositionFilter(clauses[0].getSpans(context, acceptDocs, termContexts), clauses[1].getSpans(context, acceptDocs, termContexts), op);

		return result;
	}

	@Override
	public String toString(String field) {
		switch(op) {
		case WITHIN:
			return "SpanQueryContaining(" + clausesToString(field, " within ") + ")";
		case CONTAINING:
			return "SpanQueryContaining(" + clausesToString(field, " contains ") + ")";
		case ENDS_AT:
			return "SpanQueryContaining(" + clausesToString(field, " ends at ") + ")";
		case STARTS_AT:
			return "SpanQueryContaining(" + clausesToString(field, " start at ") + ")";
		default:
			throw new RuntimeException("Unknown filter operation " + op);
		}
	}
}
