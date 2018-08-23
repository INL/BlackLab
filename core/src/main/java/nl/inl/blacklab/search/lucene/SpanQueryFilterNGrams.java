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

    public SpanQueryFilterNGrams(BLSpanQuery clause, SpanQueryPositionFilter.Operation op, int min, int max) {
        super(clause);
        this.op = op;
        this.min = min;
        this.max = max == -1 ? MAX_UNLIMITED : max;
        if (min > this.max)
            throw new IllegalArgumentException("min > max");
        if (min < 0 || this.max < 0)
            throw new IllegalArgumentException("min, max cannot be negative");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!super.equals(o))
            return false;

        final SpanQueryFilterNGrams that = (SpanQueryFilterNGrams) o;
        return op == that.op && min == that.min && max == that.max;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        List<BLSpanQuery> rewritten = rewriteClauses(reader);
        if (rewritten == null)
            return this;
        return new SpanQueryFilterNGrams(rewritten.get(0), op, min, max);
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
        return new SpanQueryFilterNGrams(clauses.get(0).noEmpty(), op, newMin, max);
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
            BLSpans spans = new SpansFilterNGramsRaw(context.reader(), clauses.get(0).getField(),
                    spansSource, op, min, max);
            return spans;
        }

    }

    @Override
    public int hashCode() {
        int h = clauses.hashCode();
        h ^= (h << 10) | (h >>> 23);
        h ^= min << 10;
        h ^= max << 5;
        h ^= op.hashCode();
        return h;
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
