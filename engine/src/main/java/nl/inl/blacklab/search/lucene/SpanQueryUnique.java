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

import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Makes sure the resulting hits do not contain consecutive duplicate hits.
 * These may arise when e.g. combining multiple SpanFuzzyQueries with OR.
 */
class SpanQueryUnique extends BLSpanQuery {
    BLSpanQuery src;

    public SpanQueryUnique(BLSpanQuery src) {
        super(src.queryInfo);
        this.src = BLSpanQuery.ensureSorted(src);
        this.queryInfo = src.queryInfo;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery rewritten = src.rewrite(reader);
        if (rewritten != src) {
            if (rewritten.hitsAreUnique())
                return rewritten;
            BLSpanQuery r = new SpanQueryUnique(rewritten);
            r.setQueryInfo(queryInfo);
            return r;
        }
        return this;
    }

    @Override
    public boolean matchesEmptySequence() {
        return src.matchesEmptySequence();
    }

    @Override
    public BLSpanQuery noEmpty() {
        return new SpanQueryUnique(src.noEmpty());
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        BLSpanWeight weight = src.createWeight(searcher, needsScores);
        return new SpanWeightUnique(weight, searcher, needsScores ? getTermContexts(weight) : null);
    }

    class SpanWeightUnique extends BLSpanWeight {

        final BLSpanWeight weight;

        public SpanWeightUnique(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermContext> terms)
                throws IOException {
            super(SpanQueryUnique.this, searcher, terms);
            this.weight = weight;
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
            BLSpans srcSpans = weight.getSpans(context, requiredPostings);
            if (srcSpans == null)
                return null;
            if (!src.hitsStartPointSorted())
                return PerDocumentSortedSpans.startPointEliminateDuplicates(srcSpans);
            return new SpansUnique(srcSpans);
        }
    }

    @Override
    public String toString(String field) {
        return "UNIQ(" + src + ")";
    }

    @Override
    public String getField() {
        return src.getField();
    }

    @Override
    public String getRealField() {
        return src.getRealField();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((src == null) ? 0 : src.hashCode());
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
        SpanQueryUnique other = (SpanQueryUnique) obj;
        if (src == null) {
            if (other.src != null)
                return false;
        } else if (!src.equals(other.src))
            return false;
        return true;
    }

    @Override
    public boolean hitsAllSameLength() {
        return src.hitsAllSameLength();
    }

    @Override
    public int hitsLengthMin() {
        return src.hitsLengthMin();
    }

    @Override
    public int hitsLengthMax() {
        return src.hitsLengthMax();
    }

    @Override
    public boolean hitsStartPointSorted() {
        return true;
    }

    @Override
    public boolean hitsEndPointSorted() {
        return src.hitsEndPointSorted();
    }

    @Override
    public boolean hitsHaveUniqueStart() {
        return src.hitsHaveUniqueStart();
    }

    @Override
    public boolean hitsHaveUniqueEnd() {
        return src.hitsHaveUniqueEnd();
    }

    @Override
    public boolean hitsAreUnique() {
        return true;
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return src.reverseMatchingCost(reader);
    }

    @Override
    public int forwardMatchingCost() {
        return src.forwardMatchingCost();
    }

    @Override
    public void setQueryInfo(QueryInfo queryInfo) {
        super.setQueryInfo(queryInfo);
        src.setQueryInfo(queryInfo);
    }
}
