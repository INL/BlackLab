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

/**
 * Returns either the left edge or right edge of the specified query.
 *
 * Note that the results of this query are zero-length spans.
 */
public class SpanQueryEdge extends SpanQueryBase {

	/** if true, return the right edges; if false, the left */
	private boolean rightEdge;

	/**
	 * Construct SpanQueryEdge object.
	 * @param query the query to determine edges from
	 * @param rightEdge if true, return the right edges; if false, the left
	 */
	public SpanQueryEdge(BLSpanQuery query, boolean rightEdge) {
		super(query);
		this.rightEdge = rightEdge;
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		BLSpanQuery[] rewritten = rewriteClauses(reader);
		return rewritten == null ? this : new SpanQueryEdge(rewritten[0], rightEdge);
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		SpanWeight weight = clauses[0].createWeight(searcher, needsScores);
		return new SpanWeightEdge(weight, searcher, needsScores ? getTermContexts(weight) : null);
	}

	public class SpanWeightEdge extends SpanWeight {

		final SpanWeight weight;

		public SpanWeightEdge(SpanWeight weight, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQueryEdge.this, searcher, terms);
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
			Spans spans = weight.getSpans(context, requiredPostings);
			if (spans == null)
				return null;
			return new SpansEdge(spans, rightEdge);
		}

	}

	@Override
	public String toString(String field) {
		return "SpanQueryEdge(" + clausesToString(field) + ")";
	}
}
