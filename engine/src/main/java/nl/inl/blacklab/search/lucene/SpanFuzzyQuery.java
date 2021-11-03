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
import java.util.List;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import nl.inl.blacklab.search.results.QueryInfo;

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
 * @author Karl Wettin &lt;kalle@snigel.net&gt;
 */
public class SpanFuzzyQuery extends BLSpanQuery {
    public final static int defaultMaxEdits = 2;

    public final static int defaultPrefixLength = 0;

    private final Term term;

    private final int maxEdits;

    private final int prefixLength;

    public SpanFuzzyQuery(QueryInfo queryInfo, Term term) {
        this(queryInfo, term, defaultMaxEdits, defaultPrefixLength);
    }

    public SpanFuzzyQuery(QueryInfo queryInfo, Term term, int maxEdits, int prefixLength) {
        super(queryInfo);
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
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        FuzzyQuery fuzzyQuery = new FuzzyQuery(term, maxEdits, prefixLength);

        Query rewrittenFuzzyQuery = fuzzyQuery.rewrite(reader);
        if (rewrittenFuzzyQuery instanceof BooleanQuery) {
            // BooleanQuery; make SpanQueries from each of the TermQueries and combine with OR
            List<BooleanClause> clauses = ((BooleanQuery) rewrittenFuzzyQuery).clauses();
            BLSpanQuery[] spanQueries = new BLSpanQuery[clauses.size()];
            for (int i = 0; i < clauses.size(); i++) {
                BooleanClause clause = clauses.get(i);

                TermQuery termQuery = (TermQuery) clause.getQuery();

                // ONLY DIFFERENCE WITH SpanFuzzyQuery:
                // Use a BLSpanTermQuery instead of default Lucene one.
                spanQueries[i] = new BLSpanTermQuery(queryInfo, termQuery.getTerm());
            }
            BLSpanOrQuery query = new BLSpanOrQuery(spanQueries);
            query.setHitsAreFixedLength(1);
            query.setClausesAreSimpleTermsInSameProperty(true);
            return query;
        }

        // Not a BooleanQuery, just a TermQuery. Convert to a SpanTermQuery.
        BLSpanQuery r = new BLSpanTermQuery(queryInfo, ((TermQuery) rewrittenFuzzyQuery).getTerm());
        return r;

    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        throw new UnsupportedOperationException("Query should have been rewritten");
    }

    @Override
    public String getRealField() {
        return term.field();
    }

    /**
     * Prints a query to a string, with <code>field</code> as the default field for
     * terms.
     * <p>
     * The representation used is one that is supposed to be readable by
     * org.apache.lucene.queryParser.QueryParser.QueryParser. However, there are the
     * following limitations:
     * <ul>
     * <li>If the query was created by the parser, the printed representation may
     * not be exactly what was parsed. For example, characters that need to be
     * escaped will be represented without the required backslash.</li>
     * <li>Some of the more complicated queries (e.g. span queries) don't have a
     * representation that can be parsed by QueryParser.</li>
     * </ul>
     *
     * @param field
     * @return the string representation
     */
    @Override
    public String toString(String field) {
        return "FUZZY(" + term.text() + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + maxEdits;
        result = prime * result + prefixLength;
        result = prime * result + ((term == null) ? 0 : term.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SpanFuzzyQuery other = (SpanFuzzyQuery) obj;
        if (maxEdits != other.maxEdits)
            return false;
        if (prefixLength != other.prefixLength)
            return false;
        if (term == null) {
            if (other.term != null)
                return false;
        } else if (!term.equals(other.term))
            return false;
        return true;
    }

    @Override
    public boolean hitsAllSameLength() {
        return true;
    }

    @Override
    public int hitsLengthMin() {
        return 1;
    }

    @Override
    public int hitsLengthMax() {
        return 1;
    }

    @Override
    public boolean hitsEndPointSorted() {
        return true;
    }

    @Override
    public boolean hitsStartPointSorted() {
        return true;
    }

    @Override
    public boolean hitsHaveUniqueStart() {
        return true;
    }

    @Override
    public boolean hitsHaveUniqueEnd() {
        return true;
    }

    @Override
    public boolean hitsAreUnique() {
        return true;
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return 0; // should be rewritten
    }

    @Override
    public int forwardMatchingCost() {
        return 0; // should be rewritten
    }
}
