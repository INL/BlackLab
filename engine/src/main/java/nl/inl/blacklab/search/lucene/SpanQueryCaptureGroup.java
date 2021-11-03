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
 * Captures its clause as a captured group.
 */
public class SpanQueryCaptureGroup extends BLSpanQueryAbstract {

    String name;

    /**
     * How to adjust the left edge of the captured hits while matching. (necessary
     * because we try to internalize constant-length neighbouring clauses into our
     * clause to speed up matching)
     */
    int leftAdjust;

    /**
     * How to adjust the right edge of the captured hits while matching. (necessary
     * because we try to internalize constant-length neighbouring clauses into our
     * clause to speed up matching)
     */
    int rightAdjust;

    /**
     * Construct SpanQueryCaptureGroup object.
     * 
     * @param query the query to determine edges from
     * @param name captured group name
     * @param leftAdjust how to adjust the captured group's start position
     * @param rightAdjust how to adjust the captured group's end position
     */
    public SpanQueryCaptureGroup(BLSpanQuery query, String name, int leftAdjust, int rightAdjust) {
        super(query);
        this.name = name;
        this.leftAdjust = leftAdjust;
        this.rightAdjust = rightAdjust;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        List<BLSpanQuery> rewritten = rewriteClauses(reader);
        return rewritten == null ? this : new SpanQueryCaptureGroup(rewritten.get(0), name, leftAdjust, rightAdjust);
    }

    @Override
    public boolean matchesEmptySequence() {
        return clauses.get(0).matchesEmptySequence();
    }

    @Override
    public BLSpanQuery noEmpty() {
        if (!matchesEmptySequence())
            return this;
        return new SpanQueryCaptureGroup(clauses.get(0).noEmpty(), name, leftAdjust, rightAdjust);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        BLSpanWeight weight = clauses.get(0).createWeight(searcher, needsScores);
        return new SpanWeightCaptureGroup(weight, searcher, needsScores ? getTermContexts(weight) : null);
    }

    class SpanWeightCaptureGroup extends BLSpanWeight {

        final BLSpanWeight weight;

        public SpanWeightCaptureGroup(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermContext> terms)
                throws IOException {
            super(SpanQueryCaptureGroup.this, searcher, terms);
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
            return new SpansCaptureGroup(spans, name, leftAdjust, rightAdjust);
        }

    }

    @Override
    public String toString(String field) {
        String adj = (leftAdjust != 0 || rightAdjust != 0 ? ", " + leftAdjust + ", " + rightAdjust : "");
        return "CAPTURE(" + clausesToString(field) + ", " + name + adj + ")";
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
        return clauses.get(0).hitsStartPointSorted();
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
    public boolean canInternalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        return clause.hitsAllSameLength();
    }

    @Override
    public BLSpanQuery internalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        if (!clause.hitsAllSameLength())
            throw new IllegalArgumentException("Can only internalize fixed-length clause!");
        // Check how to adjust the capture group edges after internalization
        int nla = leftAdjust, nra = rightAdjust;
        int clauseLength = clause.hitsLengthMin();
        if (onTheRight)
            nra -= clauseLength;
        else
            nla += clauseLength;
        return new SpanQueryCaptureGroup(SpanQuerySequence.sequenceInternalize(clauses.get(0), clause, onTheRight),
                name, nla, nra);
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
        result = prime * result + ((name == null) ? 0 : name.hashCode());
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
        SpanQueryCaptureGroup other = (SpanQueryCaptureGroup) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }
}
