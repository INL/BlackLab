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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;

/**
 * Filters a SpanQuery.
 */
public class SpanQueryFiltered extends BLSpanQueryAbstract {

    private Query filter;

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
        if (rewrittenFilter instanceof MultiTermQuery) {
            // Wrap it so it is rewritten to a BooleanQuery and we avoid the
            // "doesn't implement createWeight" problem.
            rewrittenFilter = new BLSpanMultiTermQueryWrapper<>(queryInfo, (MultiTermQuery) rewrittenFilter).rewrite(reader);
        }
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
        Query rewrite = filter.rewrite(searcher.getIndexReader());
        if (rewrite instanceof MultiTermQuery) {
            // Wrap it so it is rewritten to a BooleanQuery and we avoid the
            // "doesn't implement createWeight" problem.
            rewrite = new BLSpanMultiTermQueryWrapper<>(queryInfo, (MultiTermQuery) rewrite).rewrite(searcher.getIndexReader());
        }
        if (rewrite instanceof MatchNoDocsQuery)
            rewrite = new TermQuery(new Term("_nonexistentfield_", "_nonexistentvalue_")); // HACK. This "fixes" the 'Query does not implement createWeight issue'
        Weight filterWeight = rewrite.createWeight(searcher, false);
        return new SpanWeightFiltered(weight, filterWeight, searcher, needsScores ? getTermContexts(weight) : null);
    }

    class SpanWeightFiltered extends BLSpanWeight {

        final BLSpanWeight weight;

        final Weight filterWeight;

        public SpanWeightFiltered(BLSpanWeight weight, Weight filterWeight, IndexSearcher searcher,
                Map<Term, TermContext> terms) throws IOException {
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
    public long reverseMatchingCost(IndexReader reader) {
        return clauses.get(0).reverseMatchingCost(reader);
    }

    @Override
    public int forwardMatchingCost() {
        return clauses.get(0).forwardMatchingCost();
    }

   @Override
    public boolean isSingleAnyToken() {
        return clauses.stream().allMatch(c -> c.isSingleAnyToken());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((filter == null) ? 0 : filter.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        SpanQueryFiltered other = (SpanQueryFiltered) obj;
        if (filter == null) {
            if (other.filter != null)
                return false;
        } else if (!filter.equals(other.filter))
            return false;
        return true;
    }

}
