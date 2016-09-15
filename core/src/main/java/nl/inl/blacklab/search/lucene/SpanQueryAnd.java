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
import java.util.Collections;
import java.util.List;
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
		// Flatten nested AND queries, and invert negative-only clauses.
		// This doesn't change the query because the AND operator is associative.
		boolean anyRewritten = false;
		List<BLSpanQuery> rewrittenCl = new ArrayList<>();
		List<BLSpanQuery> rewrittenNotCl = new ArrayList<>();
		for (BLSpanQuery child: clauses) {
			List<BLSpanQuery> clPos = rewrittenCl;
			List<BLSpanQuery> clNeg = rewrittenNotCl;
			BLSpanQuery rewritten = child.rewrite(reader);
			boolean isTPAndNot = rewritten instanceof SpanQueryAndNot;
			if (!isTPAndNot && rewritten.isSingleTokenNot()) {
				// "Switch sides": invert the clause, and
				// swap the lists we add clauses to.
				rewritten = rewritten.inverted();
				List<BLSpanQuery> temp = clPos;
				clPos = clNeg;
				clNeg = temp;
				anyRewritten = true;
			}
			if (isTPAndNot) {
				// Flatten.
				// Child ANDNOT operation we want to flatten into this ANDNOT operation.
				// Replace the child, incorporating its children into this ANDNOT operation.
				clPos.addAll(((SpanQueryAndNot)rewritten).getIncludeClauses());
				clNeg.addAll(((SpanQueryAndNot)rewritten).getExcludeClauses());
				anyRewritten = true;
			} else if (rewritten instanceof SpanQueryAnd) {
				// Flatten.
				// Child AND operation we want to flatten into this AND operation.
				// Replace the child, incorporating its children into this AND operation.
				clPos.addAll(((SpanQueryAnd)rewritten).getClauses());
				anyRewritten = true;
			} else {
				// Just add it.
				clPos.add(rewritten);
				if (rewritten != child)
					anyRewritten = true;
			}
		}

		if (rewrittenCl.isEmpty()) {
			// All-negative; node should be rewritten to OR.
			if (rewrittenNotCl.size() == 1)
				return rewrittenCl.get(0).inverted().rewrite(reader);
			return (new BLSpanOrQuery(rewrittenNotCl.toArray(new BLSpanQuery[0]))).inverted().rewrite(reader);
		}

		if (!anyRewritten) {
			rewrittenCl = clauses;
			rewrittenNotCl = Collections.emptyList();
		}

		if (rewrittenCl.size() == 1 && rewrittenNotCl.isEmpty()) {
			// Single positive clause
			return rewrittenCl.get(0);
		} else if (rewrittenCl.isEmpty()) {
			// All negative clauses, so it's really just a NOT query. Should've been rewritten, but ok.
			return new SpanQueryNot(new SpanQueryAnd(rewrittenNotCl)).rewrite(reader);
		}

		// Combination of positive and possibly negative clauses
		BLSpanQuery includeResult = rewrittenCl.size() == 1 ? rewrittenCl.get(0) : new SpanQueryAnd(rewrittenCl);
		if (rewrittenNotCl.isEmpty())
			return includeResult;
		BLSpanQuery excludeResult = rewrittenNotCl.size() == 1 ? rewrittenNotCl.get(0) : new SpanQueryAnd(rewrittenNotCl);
		return new SpanQueryPositionFilter(includeResult, excludeResult, SpanQueryPositionFilter.Operation.MATCHES, true).rewrite(reader);
	}

	@Override
	public boolean hasConstantLength() {
		if (clauses.isEmpty())
			return true;
		return clauses.get(0).hasConstantLength();
	}

	@Override
	public int getMinLength() {
		if (clauses.isEmpty())
			return 1;
		return Math.max(clauses.get(0).getMinLength(), clauses.get(1).getMinLength());
	}

	@Override
	public int getMaxLength() {
		if (clauses.isEmpty())
			return 1;
		return Math.min(clauses.get(0).getMaxLength(), clauses.get(1).getMaxLength());
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		List<SpanWeight> weights = new ArrayList<>();
		for (BLSpanQuery clause: clauses) {
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
		return "AND(" + clausesToString(field) + ")";
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof SpanQueryAnd) {
			return clauses.equals(((SpanQueryAnd) obj).clauses);
		}
		return false;
	}
}
