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
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;

/**
 * A SpanQuery for a document-level AND NOT query.
 * Produces all spans from the "include" part, except for those
 * in documents that occur in the "exclude" part.
 */
public class SpanQueryDocLevelAndNot extends SpanQueryBase {

	public SpanQueryDocLevelAndNot(SpanQuery include, SpanQuery exclude) {
		super(include, exclude);
	}

	@Override
	public Query rewrite(IndexReader reader) throws IOException {
		SpanQuery[] rewritten = rewriteClauses(reader);
		return rewritten == null ? this : new SpanQueryDocLevelAndNot(rewritten[0], rewritten[1]);
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		SpanWeight includeWeight = clauses[0].createWeight(searcher, needsScores);
		SpanWeight excludeWeight = clauses[1].createWeight(searcher, needsScores);
		Map<Term, TermContext> contexts = needsScores ? getTermContexts(includeWeight) : null;
		return new SpanWeightDocLevelAndNot(includeWeight, excludeWeight, searcher, contexts);
	}

	public class SpanWeightDocLevelAndNot extends SpanWeight {

		final SpanWeight includeWeight, excludeWeight;

		public SpanWeightDocLevelAndNot(SpanWeight includeWeight, SpanWeight excludeWeight, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQueryDocLevelAndNot.this, searcher, terms);
			this.includeWeight = includeWeight;
			this.excludeWeight = excludeWeight;
		}

		@Override
		public void extractTerms(Set<Term> terms) {
			includeWeight.extractTerms(terms);
			excludeWeight.extractTerms(terms);
		}

		@Override
		public void extractTermContexts(Map<Term, TermContext> contexts) {
			includeWeight.extractTermContexts(contexts);
			excludeWeight.extractTermContexts(contexts);
		}

		@Override
		public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
			Spans includeSpans = includeWeight.getSpans(context, requiredPostings);
			if (includeSpans == null)
				return null;
			Spans excludeSpans = excludeWeight.getSpans(context, requiredPostings);
			if (excludeSpans == null)
				return includeSpans;
			return new SpansDocLevelAndNot(includeSpans, excludeSpans);
		}
	}

	@Override
	public String toString(String field) {
		return "spanAndNot([include=" + clauses[0].toString(field) + ", exclude="
				+ clauses[1].toString(field) + "])";
	}
}
