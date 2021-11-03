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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.Nfa;
import nl.inl.blacklab.search.fimatch.NfaState;
import nl.inl.blacklab.search.fimatch.NfaStateAnyToken;

/**
 * Expands the source spans to the left and right by the given ranges.
 *
 * This is used to support sequences including subsequences of completely
 * unknown tokens (like "apple" []{2, 4} "pear" to find apple and pear with 2 to
 * 4 tokens in between).
 *
 * Note that this class will generate all possible expansions, so if you call it
 * with left-expansion of between 2 to 4 tokens, it will generate 3 new hits for
 * every hit from the source spans: one hit with 2 more tokens to the left, one
 * hit with 3 more tokens to the left, and one hit with 4 more tokens to the
 * left.
 *
 * Spans generated from this query will be sorted by start point and then by end
 * point, and any duplicates generated will be discarded.
 */
public class SpanQueryExpansion extends BLSpanQueryAbstract {

    public enum Direction {
        LEFT,
        RIGHT;

        @Override
        public String toString() {
            return this == LEFT ? "L" : "R";
        }
    };

    /** Whether to expand to left or right */
    Direction direction;

    /** Minimum number of tokens to expand */
    int min;

    /** Maximum number of tokens to expand (MAX_UNLIMITED = infinite) */
    int max;

    public SpanQueryExpansion(BLSpanQuery clause, Direction direction, int min, int max) {
        super(clause);
        this.direction = direction;
        this.min = min;
        this.max = max == -1 ? MAX_UNLIMITED : max;
        if (min > this.max)
            throw new IllegalArgumentException("min > max");
        if (min < 0 || this.max < 0)
            throw new IllegalArgumentException("Expansions cannot be negative");
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        List<BLSpanQuery> rewritten = rewriteClauses(reader);
        if (rewritten == null)
            return this;
        if (min == 0 && max == 0)
            return rewritten.get(0); // not really an expansion
        return new SpanQueryExpansion(rewritten.get(0), direction, min, max);
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
        return new SpanQueryExpansion(clauses.get(0).noEmpty(), direction, newMin, max);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        BLSpanWeight weight = clauses.get(0).createWeight(searcher, needsScores);
        return new SpanWeightExpansion(weight, searcher, needsScores ? getTermContexts(weight) : null);
    }

    class SpanWeightExpansion extends BLSpanWeight {

        final BLSpanWeight weight;

        public SpanWeightExpansion(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermContext> terms)
                throws IOException {
            super(SpanQueryExpansion.this, searcher, terms);
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
            return new SpansExpansionRaw(context.reader(), clauses.get(0).getField(),
                    spansSource, direction, min, max);
        }

    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((direction == null) ? 0 : direction.hashCode());
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
        SpanQueryExpansion other = (SpanQueryExpansion) obj;
        if (direction != other.direction)
            return false;
        if (max != other.max)
            return false;
        if (min != other.min)
            return false;
        return true;
    }

    @Override
    public String toString(String field) {
        return "EXPAND(" + clauses.get(0) + ", " + direction + ", " + min + ", " + inf(max) + ")";
    }

    public boolean isExpandToLeft() {
        return direction == Direction.LEFT;
    }

    public int getMinExpand() {
        return min;
    }

    public int getMaxExpand() {
        return max;
    }

    public BLSpanQuery getClause() {
        return clauses.get(0);
    }

    @Override
    public boolean hitsAllSameLength() {
        return clauses.get(0).hitsAllSameLength() && min == max;
    }

    @Override
    public int hitsLengthMin() {
        return clauses.get(0).hitsLengthMin() + min;
    }

    @Override
    public int hitsLengthMax() {
        return addMaxValues(clauses.get(0).hitsLengthMax(), max);
    }

    @Override
    public boolean hitsEndPointSorted() {
        return clauses.get(0).hitsEndPointSorted() && (direction == Direction.LEFT || direction == Direction.RIGHT && min == max);
    }

    @Override
    public boolean hitsStartPointSorted() {
        return clauses.get(0).hitsStartPointSorted() && (direction == Direction.RIGHT || direction == Direction.LEFT && min == max);
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
        return clauses.get(0).hitsAreUnique() && min == max;
    }

    @Override
    public Nfa getNfa(ForwardIndexAccessor fiAccessor, int nfaDirection) {
        if (max == MAX_UNLIMITED)
            throw new UnsupportedOperationException("Unlimited expansion using forward index not implemented");
        Nfa nfa = clauses.get(0).getNfa(fiAccessor, nfaDirection);
        NfaState any = new NfaStateAnyToken(clauses.get(0).getRealField(), null);
        Nfa frag = new Nfa(any, Arrays.asList(any));
        frag.repeat(min, max);
        if (direction == Direction.LEFT && nfaDirection == SpanQueryFiSeq.DIR_TO_RIGHT || direction == Direction.RIGHT && nfaDirection == SpanQueryFiSeq.DIR_TO_LEFT) {
            // Prepend nfa with stretch of anytokens
            frag.append(nfa);
            nfa = frag;
        } else {
            // Append stretch of anytokens to nfa
            nfa.append(frag);
        }
        return nfa;
    }

    @Override
    public boolean canMakeNfa() {
        return max != MAX_UNLIMITED && clauses.get(0).canMakeNfa();
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        int numberOfExpansionSteps = max == MAX_UNLIMITED ? 50 : max - min + 1;
        return clauses.get(0).reverseMatchingCost(reader) * numberOfExpansionSteps;
    }

    @Override
    public int forwardMatchingCost() {
        int cost = clauses.get(0).forwardMatchingCost();
        int nMax = max == MAX_UNLIMITED ? 50 : max;
        for (int i = min; i <= nMax; i++) {
            cost += i;
        }
        return cost;
    }

    public BLSpanQuery addExpand(int addMin, int addMax) {
        int nMin = min + addMin;
        int nMax = addMaxValues(max, addMax);
        return new SpanQueryExpansion(clauses.get(0), direction, nMin, nMax);
    }

    @Override
    public boolean canInternalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        if (onTheRight && direction == Direction.LEFT || !onTheRight && direction == Direction.RIGHT) {
            // Internalization on the side of our non-expanded clause. Always possible.
            return true;
        }
        // Internalization on the side of our expansion. Only possible for any token clauses.
        return clause instanceof SpanQueryAnyToken;
    }

    @Override
    public BLSpanQuery internalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        boolean expandToLeft = direction == Direction.LEFT;
        if (onTheRight != expandToLeft && !(clause instanceof SpanQueryAnyToken)) {
            throw new IllegalArgumentException("Cannot internalize " + clause + " into " + this + " on the "
                    + (onTheRight ? "right" : "left") + "side");
        }
        if (onTheRight == expandToLeft) {
            // "Gobble up" a clause into the clause we're expanding.
            // If we're expanding to the left, the clause is added to the right of what we were expanding, and vice versa.
            SpanQuerySequence seq = SpanQuerySequence.sequenceInternalize(clauses.get(0), clause, expandToLeft);
            return new SpanQueryExpansion(seq, direction, min, max);
        }
        // Add any token to our expansion.
        return addExpand(clause.hitsLengthMin(), clause.hitsLengthMax());
    }

}
