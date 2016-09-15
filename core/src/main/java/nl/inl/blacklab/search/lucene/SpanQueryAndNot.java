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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanWeight;

/**
 * A SpanQuery for an AND NOT query.
 * Produces all spans from the "include" part, except for those
 * match a span in the "exclude" part.
 */
public class SpanQueryAndNot extends BLSpanQuery {

	private List<BLSpanQuery> include;

	private List<BLSpanQuery> exclude;

	public SpanQueryAndNot(List<BLSpanQuery> include, List<BLSpanQuery> exclude) {
		this.include = include;
		this.exclude = exclude;
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
		List<BLSpanQuery> rewrittenCl = new ArrayList<>();
		List<BLSpanQuery> rewrittenNotCl = new ArrayList<>();
		boolean isNot = false;
		for (List<BLSpanQuery> cl: Arrays.asList(include, exclude)) {
			for (BLSpanQuery child: cl) {
				List<BLSpanQuery> clPos = isNot ? rewrittenNotCl : rewrittenCl;
				List<BLSpanQuery> clNeg = isNot ? rewrittenCl : rewrittenNotCl;
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

		if (rewrittenCl.isEmpty()) {
			// All-negative; node should be rewritten to OR.
			if (rewrittenNotCl.size() == 1)
				return rewrittenCl.get(0).inverted().rewrite(reader);
			return (new BLSpanOrQuery(rewrittenNotCl.toArray(new BLSpanQuery[0]))).inverted().rewrite(reader);
		}

		if (!anyRewritten) {
			rewrittenCl = include;
			rewrittenNotCl = exclude;
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
			return includeResult.rewrite(reader);
		BLSpanQuery excludeResult = rewrittenNotCl.size() == 1 ? rewrittenNotCl.get(0) : new SpanQueryAnd(rewrittenNotCl);
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
	public boolean hasConstantLength() {
		if (include.isEmpty())
			return true;
		return include.get(0).hasConstantLength();
	}

	@Override
	public int getMinLength() {
		if (include.isEmpty())
			return 1;
		return include.get(0).getMinLength();
	}

	@Override
	public int getMaxLength() {
		if (include.isEmpty())
			return 1;
		return include.get(0).getMaxLength();
	}

	@Override
	public int hashCode() {
		return include.hashCode() + exclude.hashCode();
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		throw new RuntimeException("Query should have been rewritten!");
	}

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

	public List<BLSpanQuery> getIncludeClauses() {
		return include;
	}

	public List<BLSpanQuery> getExcludeClauses() {
		return include;
	}
}
