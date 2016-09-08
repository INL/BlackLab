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
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;

/**
 * Filters a SpanQuery.
 */
@SuppressWarnings("deprecation")
public class SpanQueryFiltered extends SpanQueryBase {

	private Query filter;

	/**
	 * Filter a SpanQuery.
	 *
	 * @param source the query to filter
	 * @param filter the filter query
	 * @deprecated supply a Query as a filter instead of the deprecated Filter class
	 */
	@Deprecated
	public SpanQueryFiltered(SpanQuery source, Filter filter) {
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
	public SpanQueryFiltered(SpanQuery source, Query filter) {
		super(source);
		this.filter = filter;
	}

	@Override
	public Query rewrite(IndexReader reader) throws IOException {
		SpanQuery[] rewritten = rewriteClauses(reader);
		Query rewrittenFilter = filter.rewrite(reader);
		return rewritten == null ? this : new SpanQueryFiltered(rewritten[0], rewrittenFilter);
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		SpanWeight weight = clauses[0].createWeight(searcher, needsScores);
		Weight filterWeight = filter.createWeight(searcher, false);
		return new SpanWeightFiltered(weight, filterWeight, searcher, needsScores ? getTermContexts(weight) : null);
	}

	public class SpanWeightFiltered extends SpanWeight {

		final SpanWeight weight;

		final Weight filterWeight;

		public SpanWeightFiltered(SpanWeight weight, Weight filterWeight, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
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
		public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
			Spans result = weight.getSpans(context, requiredPostings);
			if (result == null)
				return null;
			return new SpansFiltered(result, filterWeight.scorer(context));
		}

	}

	@Override
	public String toString(String field) {
		return "SpanQueryFiltered(" + clausesToString(field) + ", " + filter + ")";
	}
}
