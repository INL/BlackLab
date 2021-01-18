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

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.Nfa;

/**
 * Finds repeated consecutive hits.
 *
 * This generates all possible sequences of consecutive hits, so if we search
 * for B+ in the input string ABBBA, we'll get 3 hits of length 1, 2 hits of
 * length 2, and 1 hit of length 3. In the future, this should be made
 * configurable (to specifically support greedy/reluctant matching, etc.)
 */
public class SpanQueryRepetition extends BLSpanQueryAbstract {
    int min;

    int max;

    public SpanQueryRepetition(BLSpanQuery clause, int min, int max) {
        super(clause);
        this.min = min;
        this.max = max == -1 ? MAX_UNLIMITED : max;
        if (min > this.max)
            throw new IllegalArgumentException("min > max");
        if (min < 0 || this.max < 0)
            throw new IllegalArgumentException("min or max can't be negative");
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery base = clauses.get(0);
        BLSpanQuery baseRewritten = base.rewrite(reader);
        if (min == 1 && max == 1)
            return baseRewritten;
        if (baseRewritten instanceof SpanQueryAnyToken) {
            // Repeating anytoken clause can sometimes be expressed as simple anytoken clause
            SpanQueryAnyToken tp = (SpanQueryAnyToken) baseRewritten;
            if (tp.min == 1 && tp.max == 1) {
                // Repeat of a single any token
                BLSpanQuery r = new SpanQueryAnyToken(queryInfo, min, max, base.getRealField());
                return r;
            } else if (min == max && tp.min == tp.max) {
                // Exact number of any tokens
                int n = min * tp.min;
                BLSpanQuery r = new SpanQueryAnyToken(queryInfo, n, n, base.getRealField());
                return r;
            }
        } else if (baseRewritten.isSingleTokenNot() && min > 0) {
            // Rewrite to anytokens-not-containing form so we can optimize it
            // (note the check for min > 0 above, because position filter cannot match the empty sequence)
            BLSpanQuery container = new SpanQueryRepetition(new SpanQueryAnyToken(queryInfo, 1, 1, base.getRealField()), min, max);
            container = container.rewrite(reader);
            return new SpanQueryPositionFilter(container, baseRewritten.inverted(),
                    SpanQueryPositionFilter.Operation.CONTAINING, true);
        } else if (baseRewritten instanceof SpanQueryRepetition) {
            SpanQueryRepetition tp = (SpanQueryRepetition) baseRewritten;
            if (max == MAX_UNLIMITED && tp.max == MAX_UNLIMITED) {
                if (min >= 0 && min <= 1 && tp.min >= 0 && tp.min <= 1) {
                    // A++, A+*, A*+, A**. Rewrite to single repetition.
                    return new SpanQueryRepetition(tp.clauses.get(0), min * tp.min, max);
                }
            } else {
                if (min == 0 && max == 1 && tp.min == 0 && tp.max == 1) {
                    // A?? == A?
                    return tp;
                }
                // (A{x,y}{1,1} == A{x,y} has been rewritten above already)
                // (other cases like A{1,1}{x,y} should have been rewritten already)

                if (min == max && tp.min == tp.max) {
                    // Exact number of repetitions of exact number of repetitions. Just multiply.
                    // e.g. "ha"{3}{2} -> "ha"{6}
                    int n = min * tp.min;
                    return new SpanQueryRepetition(tp.clauses.get(0), n, n);
                }
            }
        }
        if (baseRewritten == base)
            return this;
        return new SpanQueryRepetition(baseRewritten, min, max);
    }

    /**
     * Repetition query matches the empty sequence iff min == 0 or its base clause
     * matches the empty sequence.
     */
    @Override
    public boolean matchesEmptySequence() {
        return clauses.get(0).matchesEmptySequence() || min == 0;
    }

    @Override
    public BLSpanQuery noEmpty() {
        if (!matchesEmptySequence())
            return this;
        int newMin = min == 0 ? 1 : min;
        return new SpanQueryRepetition(clauses.get(0).noEmpty(), newMin, max);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        BLSpanWeight weight = clauses.get(0).createWeight(searcher, needsScores);
        return new SpanWeightRepetition(weight, searcher, needsScores ? getTermContexts(weight) : null);
    }

    class SpanWeightRepetition extends BLSpanWeight {

        final BLSpanWeight weight;

        public SpanWeightRepetition(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermContext> terms)
                throws IOException {
            super(SpanQueryRepetition.this, searcher, terms);
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
            if (!hitsStartPointSorted() || !hitsAreUnique())
                spans = BLSpans.optSortUniq(spans, !hitsStartPointSorted(), !hitsAreUnique());
            return new SpansRepetition(spans, min == 0 ? 1 : min, max);
        }

    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + max;
        result = prime * result + min;
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
        SpanQueryRepetition other = (SpanQueryRepetition) obj;
        if (max != other.max)
            return false;
        if (min != other.min)
            return false;
        return true;
    }

    @Override
    public String toString(String field) {
        return "REP(" + clauses.get(0) + ", " + min + ", " + inf(max) + ")";
    }

    public BLSpanQuery getClause() {
        return clauses.get(0);
    }

    public int getMinRep() {
        return min;
    }

    public int getMaxRep() {
        return max;
    }

    @Override
    public boolean hitsAllSameLength() {
        return clauses.get(0).hitsAllSameLength() && min == max;
    }

    @Override
    public int hitsLengthMin() {
        return clauses.get(0).hitsLengthMin() * min;
    }

    @Override
    public int hitsLengthMax() {
        int clMax = clauses.get(0).hitsLengthMax();
        return max == MAX_UNLIMITED || clMax == MAX_UNLIMITED ? MAX_UNLIMITED : clMax * max;
    }

    @Override
    public boolean hitsEndPointSorted() {
        return clauses.get(0).hitsEndPointSorted() && min == max;
    }

    @Override
    public boolean hitsStartPointSorted() {
        return true;
    }

    @Override
    public boolean hitsHaveUniqueStart() {
        return clauses.get(0).hitsHaveUniqueStart() && min == max;
    }

    @Override
    public boolean hitsHaveUniqueEnd() {
        return clauses.get(0).hitsHaveUniqueEnd() && min == max;
    }

    @Override
    public boolean hitsAreUnique() {
        return true;
    }

    @Override
    public Nfa getNfa(ForwardIndexAccessor fiAccessor, int direction) {
        Nfa frag = clauses.get(0).getNfa(fiAccessor, direction);
        frag.repeat(min, max);
        return frag;
    }

    @Override
    public boolean canMakeNfa() {
        return clauses.get(0).canMakeNfa();
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return clauses.get(0).reverseMatchingCost(reader);
    }

    @Override
    public int forwardMatchingCost() {
        int nMax = max == MAX_UNLIMITED ? 50 : max;
        int clauseCost = clauses.get(0).forwardMatchingCost();
//		int cost = 0;
//		for (int i = min; i <= nMax; i++) {
//			cost += i * clauseCost;
//		}
//		return cost;

        // NOTE: you would expect a more correct cost estimation to be
        //   clauseCost * (min + (min + 1) + ... + max)
        // (each alternative path is taken, after all)
        // But it turns out NFA matching repetitions is actually pretty fast,
        // possibly because disk cache factors are more important than
        // how many NFA state branches are followed. So we pragmatically use
        // the somewhat lower cost of (clauseCost * max).

        return clauseCost * nMax;
    }

}
