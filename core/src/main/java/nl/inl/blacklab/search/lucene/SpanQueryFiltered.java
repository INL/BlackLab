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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.Weight;

/**
 * Filters a SpanQuery.
 */
@SuppressWarnings("deprecation")
public class SpanQueryFiltered extends BLSpanQueryAbstract {

	private Query filter;

	/**
	 * Filter a SpanQuery.
	 *
	 * @param source the query to filter
	 * @param filter the filter query
	 * @deprecated supply a Query as a filter instead of the deprecated Filter class
	 */
	@Deprecated
	public SpanQueryFiltered(BLSpanQuery source, Filter filter) {
		super(source);
		if (!(filter instanceof QueryWrapperFilter)) {
			throw new UnsupportedOperationException("Filter must be a QueryWrapperFilter!");
		}
		this.filter = ((QueryWrapperFilter) filter).getQuery();
	}

	/**
	 * Filter a SpanQuery.
	 *
	 * @param source the query to filter
	 * @param filter the filter query
	 */
	public SpanQueryFiltered(BLSpanQuery source, Query filter) {
		super(source);
		this.filter = filter;
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		List<BLSpanQuery> rewritten = rewriteClauses(reader);
		Query rewrittenFilter = filter.rewrite(reader);
		return rewritten == null ? this : new SpanQueryFiltered(rewritten.get(0), rewrittenFilter);
	}

	@Override
	public boolean matchesEmptySequence() {
		return clauses.get(0).matchesEmptySequence();
	}

	@Override
	public BLSpanQuery noEmpty() {
		return new SpanQueryFiltered(clauses.get(0).noEmpty(), filter);
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
	public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		BLSpanWeight weight = clauses.get(0).createWeight(searcher, needsScores);
		Weight filterWeight = filter.createWeight(searcher, false);
		return new SpanWeightFiltered(weight, filterWeight, searcher, needsScores ? getTermContexts(weight) : null);
	}

	public class SpanWeightFiltered extends BLSpanWeight {

		final BLSpanWeight weight;

		final Weight filterWeight;

		public SpanWeightFiltered(BLSpanWeight weight, Weight filterWeight, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQueryFiltered.this, searcher, terms);
			this.weight = weight;
			this.filterWeight = filterWeight;
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
		public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
			BLSpans result = weight.getSpans(context, requiredPostings);
			if (result == null)
				return null;
			return new SpansFiltered(result, filterWeight.scorer(context));
		}

	}

	@Override
	public String toString(String field) {
		return "FILTER(" + clausesToString(field) + ", " + filter + ")";
	}

	@Override
	public long estimatedNumberOfHits(IndexReader reader) {
		return clauses.get(0).estimatedNumberOfHits(reader);
	}
}
