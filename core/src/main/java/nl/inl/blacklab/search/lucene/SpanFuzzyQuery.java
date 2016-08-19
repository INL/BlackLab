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
/**
 *
 */
package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;

/*
 * This is my SpanFuzzyQuery. It is released under the Apache licensence. Just paste it in. (Karl
 * Wettin, http://issues.apache.org/jira/browse/LUCENE-522 )
 *
 * Bugfix JN: FuzzyQuery sometimes returns a TermQuery instead of a BooleanQuery (when there are no
 * fuzzy alternatives).
 * JN: ported to Lucene 4.0
 *     replaced SpanOrQuery with BLSpanOrQuery
 */

/**
 * A fuzzy (approximate) query with spans.
 *
 * @author Karl Wettin <kalle@snigel.net>
 */
public class SpanFuzzyQuery extends SpanQuery {
	public final static int defaultMaxEdits = 2;

	public final static int defaultPrefixLength = 0;

	private final Term term;

	private final int maxEdits;

	private final int prefixLength;

	private Query rewrittenFuzzyQuery = null;

	public SpanFuzzyQuery(Term term) {
		this(term, defaultMaxEdits, defaultPrefixLength);
	}

	public SpanFuzzyQuery(Term term, int maxEdits, int prefixLength) {
		this.term = term;
		this.maxEdits = maxEdits;
		this.prefixLength = prefixLength;

		if (maxEdits <= 0) {
			throw new IllegalArgumentException("maxEdits <= 0");
		}
		if (prefixLength < 0) {
			throw new IllegalArgumentException("prefixLength < 0");
		}

	}

	@Override
	public Query rewrite(IndexReader reader) throws IOException {
		FuzzyQuery fuzzyQuery = new FuzzyQuery(term, maxEdits, prefixLength);

		rewrittenFuzzyQuery = fuzzyQuery.rewrite(reader);
		if (rewrittenFuzzyQuery instanceof BooleanQuery) {
			// BooleanQuery; make SpanQueries from each of the TermQueries and combine with OR
			BooleanClause[] clauses = ((BooleanQuery) rewrittenFuzzyQuery).getClauses();
			SpanQuery[] spanQueries = new SpanQuery[clauses.length];
			for (int i = 0; i < clauses.length; i++) {
				BooleanClause clause = clauses[i];

				TermQuery termQuery = (TermQuery) clause.getQuery();

				// ONLY DIFFERENCE WITH SpanFuzzyQuery:
				// Use a BLSpanTermQuery instead of default Lucene one
				// because we need to override getField() to only return the base field name,
				// not the complete field name with the property.
				spanQueries[i] = new BLSpanTermQuery(termQuery.getTerm());
				spanQueries[i].setBoost(termQuery.getBoost());
			}
			SpanQuery query = new BLSpanOrQuery(spanQueries);
			query.setBoost(fuzzyQuery.getBoost());
			return query;
		}

		// Not a BooleanQuery, just a TermQuery. Convert to a SpanTermQuery.
		SpanQuery query = new BLSpanOrQuery(new BLSpanTermQuery(
				((TermQuery) rewrittenFuzzyQuery).getTerm()));
		query.setBoost(fuzzyQuery.getBoost());
		return query;

	}

	/**
	 * Expert: Returns the matches for this query in an index. Used internally to search for spans.
	 *
	 * @return the spans object
	 */
	@Override
	public Spans getSpans(LeafReaderContext context, Bits acceptDocs, Map<Term, TermContext> termContexts) {
		throw new UnsupportedOperationException("Query should have been rewritten");
	}

	/**
	 * Returns the name of the field matched by this query.
	 *
	 * @return name of the field
	 */
	@Override
	public String getField() {
		return term.field();
	}

	/**
	 * Add all terms to the supplied set
	 *
	 * @param terms
	 *            the set the terms should be added to
	 */
	@Override
	public void extractTerms(Set<Term> terms) {
		if (rewrittenFuzzyQuery == null) {
			throw new RuntimeException("Query must be rewritten prior to calling extractTerms()!");
		}
		if (rewrittenFuzzyQuery instanceof BooleanQuery) {
			// Extract terms from clauses
			BooleanClause[] clauses = ((BooleanQuery) rewrittenFuzzyQuery).getClauses();
			for (BooleanClause clause: clauses) {
				TermQuery termQuery = (TermQuery) clause.getQuery();
				terms.add(termQuery.getTerm());
			}
		} else {
			// Just a single term, not a BooleanQuery
			terms.add(((TermQuery) rewrittenFuzzyQuery).getTerm());
		}
	}

	/**
	 * Prints a query to a string, with <code>field</code> as the default field for terms.
	 * <p>
	 * The representation used is one that is supposed to be readable by
	 * org.apache.lucene.queryParser.QueryParser.QueryParser. However, there are the following
	 * limitations:
	 * <ul>
	 * <li>If the query was created by the parser, the printed representation may not be exactly
	 * what was parsed. For example, characters that need to be escaped will be represented without
	 * the required backslash.</li>
	 * <li>Some of the more complicated queries (e.g. span queries) don't have a representation that
	 * can be parsed by QueryParser.</li>
	 * </ul>
	 *
	 * @param field
	 * @return the string representation
	 */
	@Override
	public String toString(String field) {
		if (rewrittenFuzzyQuery == null)
			return "SpanFuzzyQuery(" + term.text() + ")";
		return "SpanFuzzyQuery(" + rewrittenFuzzyQuery.toString() + ")";
	}
}
