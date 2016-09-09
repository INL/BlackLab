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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;

/**
 * Combines SpanQueries using AND. Note that this means that only matches with the same document id,
 * the same start and the same end positions in all SpanQueries will be kept.
 */
public class SpanQueryAnd extends BLSpanQueryAbstract {
	public SpanQueryAnd(BLSpanQuery first, BLSpanQuery second) {
		super(first, second);
	}

	public SpanQueryAnd(Collection<BLSpanQuery> clauscol) {
		super(clauscol);
	}

	public SpanQueryAnd(BLSpanQuery[] _clauses) {
		super(_clauses);
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		BLSpanQuery[] rewritten = rewriteClauses(reader);
		return rewritten == null ? this : new SpanQueryAnd(rewritten);
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		List<SpanWeight> weights = new ArrayList<>();
		for (SpanQuery clause: clauses) {
			weights.add(clause.createWeight(searcher, needsScores));
		}
		Map<Term, TermContext> contexts = needsScores ? getTermContexts(weights.toArray(new SpanWeight[0])) : null;
		return new SpanWeightAnd(weights, searcher, contexts);
	}

	public class SpanWeightAnd extends SpanWeight {

		final List<SpanWeight> weights;

		public SpanWeightAnd(List<SpanWeight> weights, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQueryAnd.this, searcher, terms);
			this.weights = weights;
		}

		@Override
		public void extractTerms(Set<Term> terms) {
			for (SpanWeight weight: weights) {
				weight.extractTerms(terms);
			}
		}

		@Override
		public void extractTermContexts(Map<Term, TermContext> contexts) {
			for (SpanWeight weight: weights) {
				weight.extractTermContexts(contexts);
			}
		}

		@Override
		public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
			Spans combi = weights.get(0).getSpans(context, requiredPostings);
			for (int i = 1; i < weights.size(); i++) {
				Spans si = weights.get(i).getSpans(context, requiredPostings);
				if (combi == null || si == null)
					return null; // if no hits in one of the clauses, no hits in and query
				combi = new SpansAnd(combi, si);
			}
			return combi;
		}
	}

	@Override
	public String toString(String field) {
		return "SpanQueryAnd(" + clausesToString(field) + ")";
	}
}
