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

/**
 * Returns either the left edge or right edge of the specified query.
 *
 * Note that the results of this query are zero-length spans.
 */
public class SpanQueryEdge extends BLSpanQueryAbstract {

    /** if true, return the right edges; if false, the left */
    boolean rightEdge;

    /**
     * Construct SpanQueryEdge object.
     * 
     * @param query the query to determine edges from
     * @param rightEdge if true, return the right edges; if false, the left
     */
    public SpanQueryEdge(BLSpanQuery query, boolean rightEdge) {
        super(query);
        this.rightEdge = rightEdge;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        List<BLSpanQuery> rewritten = rewriteClauses(reader);
        return rewritten == null ? this : new SpanQueryEdge(rewritten.get(0), rightEdge);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        BLSpanWeight weight = clauses.get(0).createWeight(searcher, needsScores);
        return new SpanWeightEdge(weight, searcher, needsScores ? getTermContexts(weight) : null);
    }

    class SpanWeightEdge extends BLSpanWeight {

        final BLSpanWeight weight;

        public SpanWeightEdge(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermContext> terms)
                throws IOException {
            super(SpanQueryEdge.this, searcher, terms);
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
            BLSpans spans = weight.getSpans(context, requiredPostings);
            if (spans == null)
                return null;
            return new SpansEdge(spans, rightEdge);
        }

    }

    @Override
    public String toString(String field) {
        return "EDGE(" + clausesToString(field) + ", " + (rightEdge ? "R" : "L") + ")";
    }

    public boolean isRightEdge() {
        return rightEdge;
    }

    public String getElementName() {
        BLSpanQuery cl = clauses.get(0);
        if (cl instanceof SpanQueryTags) {
            return ((SpanQueryTags) cl).getElementName();
        }
        return null;
    }

    public BLSpanQuery getClause() {
        return clauses.get(0);
    }

    @Override
    public boolean hitsAllSameLength() {
        return true;
    }

    @Override
    public int hitsLengthMin() {
        return 0;
    }

    @Override
    public int hitsLengthMax() {
        return 0;
    }

    @Override
    public boolean hitsStartPointSorted() {
        return rightEdge ? clauses.get(0).hitsEndPointSorted() : clauses.get(0).hitsStartPointSorted();
    }

    @Override
    public boolean hitsEndPointSorted() {
        return hitsStartPointSorted();
    }

    @Override
    public boolean hitsHaveUniqueStart() {
        return rightEdge ? clauses.get(0).hitsHaveUniqueEnd() : clauses.get(0).hitsHaveUniqueStart();
    }

    @Override
    public boolean hitsHaveUniqueEnd() {
        return hitsHaveUniqueStart();
    }

    @Override
    public boolean hitsAreUnique() {
        return hitsHaveUniqueStart();
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
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (rightEdge ? 1231 : 1237);
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
        SpanQueryEdge other = (SpanQueryEdge) obj;
        return rightEdge == other.rightEdge;
    }
}
