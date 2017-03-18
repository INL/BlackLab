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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanWeight;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.fimatch.NfaFragment;
import nl.inl.blacklab.search.fimatch.NfaState;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;

/**
 * A SpanQuery for an AND NOT query.
 * Produces all spans matching all the "include" parts, except for those
 * that match any span in the "exclude" part.
 */
public class SpanQueryAndNot extends BLSpanQuery {

	private List<BLSpanQuery> include;

	private List<BLSpanQuery> exclude;

	public SpanQueryAndNot(List<BLSpanQuery> include, List<BLSpanQuery> exclude) {
		this.include = include == null ? new ArrayList<BLSpanQuery>() : include;
		this.exclude = exclude == null ? new ArrayList<BLSpanQuery>() : exclude;
		if (this.include.size() == 0 && this.exclude.size() == 0)
			throw new RuntimeException("ANDNOT query without clauses");
		checkBaseFieldName();
	}

	private void checkBaseFieldName() {
		if (include.size() > 0) {
			String baseFieldName = ComplexFieldUtil.getBaseName(include.get(0).getField());
			for (BLSpanQuery clause: include) {
				String f = ComplexFieldUtil.getBaseName(clause.getField());
				if (!baseFieldName.equals(f))
					throw new RuntimeException("Mix of incompatible fields in query ("
							+ baseFieldName + " and " + f + ")");
			}
		}
	}

	@Override
	public BLSpanQuery inverted() {
		if (exclude.isEmpty()) {
			// In this case, it's better to just wrap this in TextPatternNot,
			// so it will be recognized by other rewrite()s.
			return super.inverted();
		}

		// ! ( (a & b) & !(c & d) ) --> !a | !b | (c & d)
		List<BLSpanQuery> inclNeg = new ArrayList<>();
		for (BLSpanQuery tp: include) {
			inclNeg.add(tp.inverted());
		}
		if (exclude.size() == 1)
			inclNeg.add(exclude.get(0));
		else
			inclNeg.add(new SpanQueryAndNot(exclude, null));
		return new BLSpanOrQuery(inclNeg.toArray(new BLSpanQuery[0]));
	}

	@Override
	protected boolean okayToInvertForOptimization() {
		// Inverting is "free" if it will still be an AND NOT query (i.e. will have a positive component).
		return producesSingleTokens() && !exclude.isEmpty();
	}

	@Override
	public boolean isSingleTokenNot() {
		return producesSingleTokens() && include.isEmpty();
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {

		// Flatten nested AND queries, and invert negative-only clauses.
		// This doesn't change the query because the AND operator is associative.
		boolean anyRewritten = false;
		List<BLSpanQuery> flatCl = new ArrayList<>();
		List<BLSpanQuery> flatNotCl = new ArrayList<>();
		boolean isNot = false;
		for (List<BLSpanQuery> cl: Arrays.asList(include, exclude)) {
			for (BLSpanQuery child: cl) {
				List<BLSpanQuery> clPos = isNot ? flatNotCl : flatCl;
				List<BLSpanQuery> clNeg = isNot ? flatCl : flatNotCl;
				boolean isTPAndNot = child instanceof SpanQueryAndNot;
				if (!isTPAndNot && child.isSingleTokenNot()) {
					// "Switch sides": invert the clause, and
					// swap the lists we add clauses to.
					child = child.inverted();
					List<BLSpanQuery> temp = clPos;
					clPos = clNeg;
					clNeg = temp;
					anyRewritten = true;
					isTPAndNot = child instanceof SpanQueryAndNot;
				}
				if (isTPAndNot) {
					// Flatten.
					// Child AND operation we want to flatten into this AND operation.
					// Replace the child, incorporating its children into this AND operation.
					clPos.addAll(((SpanQueryAndNot)child).include);
					clNeg.addAll(((SpanQueryAndNot)child).exclude);
					anyRewritten = true;
				} else {
					// Just add it.
					clPos.add(child);
				}
			}
			isNot = true;
		}

		// Rewrite clauses, and again flatten/invert if necessary.
		List<BLSpanQuery> rewrCl = new ArrayList<>();
		List<BLSpanQuery> rewrNotCl = new ArrayList<>();
		isNot = false;
		for (List<BLSpanQuery> cl: Arrays.asList(flatCl, flatNotCl)) {
			for (BLSpanQuery child: cl) {
				List<BLSpanQuery> clPos = isNot ? rewrNotCl : rewrCl;
				List<BLSpanQuery> clNeg = isNot ? rewrCl : rewrNotCl;
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
					isTPAndNot = rewritten instanceof SpanQueryAndNot;
				}
				if (isTPAndNot) {
					// Flatten.
					// Child AND operation we want to flatten into this AND operation.
					// Replace the child, incorporating its children into this AND operation.
					clPos.addAll(((SpanQueryAndNot)rewritten).include);
					clNeg.addAll(((SpanQueryAndNot)rewritten).exclude);
					anyRewritten = true;
				} else {
					// Just add it.
					clPos.add(rewritten);
					if (rewritten != child)
						anyRewritten = true;
				}
			}
			isNot = true;
		}

		if (rewrCl.isEmpty()) {
			// All-negative; node should be rewritten to OR.
			if (rewrNotCl.size() == 1)
				return rewrCl.get(0).inverted().rewrite(reader);
			return (new BLSpanOrQuery(rewrNotCl.toArray(new BLSpanQuery[0]))).inverted().rewrite(reader);
		}

		if (rewrCl.size() == 1 && rewrNotCl.isEmpty()) {
			// Single positive clause
			return rewrCl.get(0);
		}

		if (!anyRewritten && exclude.isEmpty()) {
			// Nothing needs to be rewritten.
			return this;
		}

		// Combination of positive and possibly negative clauses
		BLSpanQuery includeResult = rewrCl.size() == 1 ? rewrCl.get(0) : new SpanQueryAndNot(rewrCl, null);
		if (rewrNotCl.isEmpty())
			return includeResult.rewrite(reader);
		BLSpanQuery excludeResult = rewrNotCl.size() == 1 ? rewrNotCl.get(0) : new BLSpanOrQuery(rewrNotCl.toArray(new BLSpanQuery[0]));
		return new SpanQueryPositionFilter(includeResult, excludeResult, SpanQueryPositionFilter.Operation.MATCHES, true).rewrite(reader);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof SpanQueryAndNot) {
			return include.equals(((SpanQueryAndNot) obj).include) &&
					exclude.equals(((SpanQueryAndNot) obj).exclude);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return include.hashCode() + exclude.hashCode();
	}

	@Override
	public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		if (exclude.size() > 0)
			throw new RuntimeException("Query should've been rewritten! (exclude clauses left)");

		List<BLSpanWeight> weights = new ArrayList<>();
		for (BLSpanQuery clause: include) {
			weights.add(clause.createWeight(searcher, needsScores));
		}
		Map<Term, TermContext> contexts = needsScores ? getTermContexts(weights.toArray(new SpanWeight[0])) : null;
		return new SpanWeightAnd(weights, searcher, contexts);
	}

	public class SpanWeightAnd extends BLSpanWeight {

		final List<BLSpanWeight> weights;

		public SpanWeightAnd(List<BLSpanWeight> weights, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQueryAndNot.this, searcher, terms);
			this.weights = weights;
		}

		@Override
		public void extractTerms(Set<Term> terms) {
			for (BLSpanWeight weight: weights) {
				weight.extractTerms(terms);
			}
		}

		@Override
		public void extractTermContexts(Map<Term, TermContext> contexts) {
			for (BLSpanWeight weight: weights) {
				weight.extractTermContexts(contexts);
			}
		}

		@Override
		public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
			BLSpans combi = weights.get(0).getSpans(context, requiredPostings);
			if (combi == null)
				return null; // if no hits in one of the clauses, no hits in AND query
			if (!((BLSpanQuery)weights.get(0).getQuery()).hitsStartPointSorted())
				combi = BLSpans.optSortUniq(combi, true, false);
			for (int i = 1; i < weights.size(); i++) {
				BLSpans si = weights.get(i).getSpans(context, requiredPostings);
				if (si == null)
					return null; // if no hits in one of the clauses, no hits in AND query
				if (!((BLSpanQuery)weights.get(i).getQuery()).hitsStartPointSorted())
					si = BLSpans.optSortUniq(si, true, false);
				combi = new SpansAnd(combi, si);
			}
			return combi;
		}
	}

	@Override
	public String toString(String field) {
		if (exclude.isEmpty())
			return "AND(" + clausesToString(field, include) + ")";
		return "ANDNOT([" + clausesToString(field, include) + "], [" + clausesToString(field, exclude) + "])";
	}

	@Override
	public String getField() {
		if (include.size() > 0)
			return include.get(0).getField();
		if (exclude.size() > 0)
			return exclude.get(0).getField();
		throw new RuntimeException("Query has no clauses");
	}

	@Override
	public String getRealField() {
		if (include.size() > 0)
			return include.get(0).getRealField();
		if (exclude.size() > 0)
			return exclude.get(0).getRealField();
		throw new RuntimeException("Query has no clauses");
	}

	public List<BLSpanQuery> getIncludeClauses() {
		return include;
	}

	public List<BLSpanQuery> getExcludeClauses() {
		return include;
	}

	@Override
	public boolean hitsAllSameLength() {
		if (include.isEmpty())
			return true;
		for (BLSpanQuery clause: include) {
			if (clause.hitsAllSameLength())
				return true;
		}
		return true;
	}

	@Override
	public int hitsLengthMin() {
		if (include.isEmpty())
			return 1;
		int l = 0;
		for (BLSpanQuery clause: include) {
			if (clause.hitsLengthMin() > l)
				l = clause.hitsLengthMin();
		}
		return l;
	}

	@Override
	public int hitsLengthMax() {
		if (include.isEmpty())
			return 1;
		int l = Integer.MAX_VALUE;
		for (BLSpanQuery clause: include) {
			if (clause.hitsLengthMax() < l)
				l = clause.hitsLengthMax();
		}
		return l;
	}

	@Override
	public boolean hitsEndPointSorted() {
		return hitsStartPointSorted() && hitsAllSameLength();
	}

	@Override
	public boolean hitsStartPointSorted() {
		return true;
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		if (include.isEmpty())
			return true;
		for (BLSpanQuery clause: include) {
			if (clause.hitsHaveUniqueStart())
				return true;
		}
		return true;
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		if (include.isEmpty())
			return true;
		for (BLSpanQuery clause: include) {
			if (clause.hitsHaveUniqueEnd())
				return true;
		}
		return true;
	}

	@Override
	public boolean hitsAreUnique() {
		if (include.isEmpty())
			return true;
		for (BLSpanQuery clause: include) {
			if (clause.hitsAreUnique())
				return true;
		}
		return true;
	}

	@Override
	public NfaFragment getNfa(ForwardIndexAccessor fiAccessor, int direction) {
		if (exclude.size() > 0)
			throw new RuntimeException("Query should've been rewritten! (exclude clauses left)");
		List<NfaState> nfaClauses = new ArrayList<>();
		List<NfaState> dangling = new ArrayList<>();
		for (BLSpanQuery clause: include) {
			NfaFragment nfa = clause.getNfa(fiAccessor, direction);
			nfaClauses.add(nfa.getStartingState());
			dangling.addAll(nfa.getDanglingArrows());
		}
		NfaState and = NfaState.and(nfaClauses);
		return new NfaFragment(and, dangling);
	}

	@Override
	public boolean canMakeNfa() {
		if (exclude.size() > 0)
			return false;
		for (BLSpanQuery clause: include) {
			if (!clause.canMakeNfa())
				return false;
		}
		return true;
	}

	@Override
	public long estimatedNumberOfHits(IndexReader reader) {
		// Excludes should have been rewritten, so we only look at includes.
		// We return the least frequent clause since we can skip over the more
		// frequent ones, or match them using the forward index.
		long cost = Integer.MAX_VALUE;
		for (BLSpanQuery clause: include) {
			cost = Math.min(cost, clause.estimatedNumberOfHits(reader));
		}
		return cost;
	}

}
