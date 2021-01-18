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
 * Return n-grams contained in the from the source spans hits.
 */
public class SpanQueryFilterNGrams extends BLSpanQueryAbstract {

    /** How to expand the hits */
    SpanQueryPositionFilter.Operation op;

    /** Minimum number of tokens to expand */
    int min;

    /** Maximum number of tokens to expand (MAX_UNLIMITED = infinite) */
    int max;

    /** How to adjust left n-gram border relative to the filter clause */
    private int leftAdjust;

    /** How to adjust right n-gram border relative to the filter clause */
    private int rightAdjust;

    public SpanQueryFilterNGrams(BLSpanQuery clause, SpanQueryPositionFilter.Operation op, int min, int max, int leftAdjust, int rightAdjust) {
        super(clause);
        this.op = op;
        this.min = min;
        this.max = max == -1 ? MAX_UNLIMITED : max;
        if (min > this.max)
            throw new IllegalArgumentException("min > max");
        if (min < 0 || this.max < 0)
            throw new IllegalArgumentException("min, max cannot be negative");
        this.leftAdjust = leftAdjust;
        this.rightAdjust = rightAdjust;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        List<BLSpanQuery> rewritten = rewriteClauses(reader);
        if (rewritten == null)
            return this;
        return new SpanQueryFilterNGrams(rewritten.get(0), op, min, max, leftAdjust, rightAdjust);
    }

    @Override
    public boolean matchesEmptySequence() {
        return clauses.get(0).matchesEmptySequence() && min == 0;
    }

    @Override
    public BLSpanQuery noEmpty() {
        if (!matchesEmptySequence())
            return this;
        int newMin = min == 0 ? 1 : min;
        return new SpanQueryFilterNGrams(clauses.get(0).noEmpty(), op, newMin, max, leftAdjust, rightAdjust);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        BLSpanWeight weight = clauses.get(0).createWeight(searcher, needsScores);
        return new SpanWeightFilterNGrams(weight, searcher, needsScores ? getTermContexts(weight) : null);
    }

    class SpanWeightFilterNGrams extends BLSpanWeight {

        final BLSpanWeight weight;

        public SpanWeightFilterNGrams(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermContext> terms)
                throws IOException {
            super(SpanQueryFilterNGrams.this, searcher, terms);
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
            BLSpans spansSource = weight.getSpans(context, requiredPostings);
            if (spansSource == null)
                return null;
            return new SpansFilterNGramsRaw(context.reader(), clauses.get(0).getField(),
                    spansSource, op, min, max, leftAdjust, rightAdjust);
        }

    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + leftAdjust;
        result = prime * result + max;
        result = prime * result + min;
        result = prime * result + ((op == null) ? 0 : op.hashCode());
        result = prime * result + rightAdjust;
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
        SpanQueryFilterNGrams other = (SpanQueryFilterNGrams) obj;
        if (leftAdjust != other.leftAdjust)
            return false;
        if (max != other.max)
            return false;
        if (min != other.min)
            return false;
        if (op != other.op)
            return false;
        if (rightAdjust != other.rightAdjust)
            return false;
        return true;
    }

    @Override
    public String toString(String field) {
        return "FILTERNGRAMS(" + clauses.get(0) + ", " + op + ", " + min + ", " + inf(max)
                + ")";
    }

    @Override
    public boolean hitsAllSameLength() {
        return min == max;
    }

    @Override
    public int hitsLengthMin() {
        return min;
    }

    @Override
    public int hitsLengthMax() {
        return max;
    }

    @Override
    public boolean hitsEndPointSorted() {
        return hitsAllSameLength();
    }

    @Override
    public boolean hitsStartPointSorted() {
        return clauses.get(0).hitsStartPointSorted() && clauses.get(0).hitsLengthMax() >= min;
    }

    @Override
    public boolean hitsHaveUniqueStart() {
        return min == max;
    }

    @Override
    public boolean hitsHaveUniqueEnd() {
        return min == max;
    }

    @Override
    public boolean hitsAreUnique() {
        return clauses.get(0).hitsAreUnique() && clauses.get(0).hitsLengthMax() >= min;
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        int numberOfExpansionSteps = max == MAX_UNLIMITED ? 50 : max - min + 1;
        return clauses.get(0).reverseMatchingCost(reader) * numberOfExpansionSteps;
    }

    @Override
    public int forwardMatchingCost() {
        int numberOfExpansionSteps = max == MAX_UNLIMITED ? 50 : max - min + 1;
        return clauses.get(0).forwardMatchingCost() * numberOfExpansionSteps;
    }

}
