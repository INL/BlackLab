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
import java.util.IdentityHashMap;
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
import nl.inl.blacklab.search.fimatch.NfaTwoWay;

/**
 * Find hits that match the specified NFA, starting from the specified anchor
 * query (either the start of end of those hits, with a possible offset added),
 * in the specified direction.
 */
public class SpanQueryFiSeq extends BLSpanQueryAbstract {

    public static final int DIR_TO_LEFT = -1;

    public static final int DIR_TO_RIGHT = 1;

    public static final boolean START_OF_ANCHOR = true;

    public static final boolean END_OF_ANCHOR = false;

    boolean startOfAnchor;

    /** Our NFA, both in our own direction and the opposite direction. */
    NfaTwoWay nfa;

    private BLSpanQuery nfaQuery;

    int direction;

    ForwardIndexAccessor fiAccessor;

    /**
     *
     * @param anchor hits to use as anchor to start NFA matching
     * @param startOfAnchor if true, use the starts of anchor hits; if false, use
     *            the ends
     * @param nfa the NFA to use for matching
     * @param nfaQuery the query that generated the NFA, so we can still use its
     *            guarantee methods for optimization
     * @param direction the direction to match in (-1 = right-to-left, 1 =
     *            left-to-right; use DIR_TO_RIGHT / DIR_TO_LEFT)
     * @param fiAccessor maps between term strings and term indices for each
     *            annotation
     */
    public SpanQueryFiSeq(BLSpanQuery anchor, boolean startOfAnchor, NfaTwoWay nfa, BLSpanQuery nfaQuery, int direction,
            ForwardIndexAccessor fiAccessor) {
        super(anchor);
        this.startOfAnchor = startOfAnchor;
        this.nfa = nfa;
        this.nfaQuery = nfaQuery;
        this.direction = direction;
        this.fiAccessor = fiAccessor;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery rewritten = clauses.get(0).rewrite(reader);
        if (rewritten != clauses.get(0)) {
            return new SpanQueryFiSeq(rewritten, startOfAnchor, nfa, nfaQuery, direction, fiAccessor);
        }
        return this;
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {

        // Finalize our NFA, so it looks up annotation numbers for its annotation names.
        nfa.finish();
        nfa.lookupPropertyNumbers(fiAccessor, new IdentityHashMap<NfaState, Boolean>());

        BLSpanWeight anchorWeight = clauses.get(0).createWeight(searcher, needsScores);
        Map<Term, TermContext> contexts = needsScores ? getTermContexts(anchorWeight) : null;
        return new SpanWeightFiSeq(anchorWeight, searcher, contexts);
    }

    class SpanWeightFiSeq extends BLSpanWeight {

        final BLSpanWeight anchorWeight;

        public SpanWeightFiSeq(BLSpanWeight anchorWeight, IndexSearcher searcher, Map<Term, TermContext> terms)
                throws IOException {
            super(SpanQueryFiSeq.this, searcher, terms);
            this.anchorWeight = anchorWeight;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            anchorWeight.extractTerms(terms);
        }

        @Override
        public void extractTermContexts(Map<Term, TermContext> contexts) {
            anchorWeight.extractTermContexts(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans anchorSpans = anchorWeight.getSpans(context, requiredPostings);
            if (anchorSpans == null)
                return null;
            if (!clauses.get(0).hitsAreUnique())
                anchorSpans = BLSpans.optSortUniq(anchorSpans, !clauses.get(0).hitsStartPointSorted(), true);
            return new SpansFiSeq(anchorSpans, startOfAnchor, nfa.getNfa().getStartingState(), direction,
                    fiAccessor.getForwardIndexAccessorLeafReader(context.reader()));
        }
    }

    @Override
    public String toString(String field) {
        return "FISEQ(" + clausesToString(field) + ", " + nfa.getNfa() + ", " + direction + ")";
    }

    // public SpanQueryFiSeq copy() {
    // return new SpanQueryFiSeq(clauses.get(0), startOfAnchor, nfaFrag, direction, fiAccessor);
    // }

    @Override
    public boolean matchesEmptySequence() {
        return false; // can't be used if clause matches empty sequence, we need anchors
    }

    @Override
    public BLSpanQuery noEmpty() {
        return this;
    }

    @Override
    public boolean hitsAllSameLength() {
        return clauses.get(0).hitsAllSameLength() && nfaQuery.hitsAllSameLength();
    }

    @Override
    public int hitsLengthMin() {
        if (startOfAnchor && direction == DIR_TO_LEFT || !startOfAnchor && direction == DIR_TO_RIGHT) {
            // Non-overlapping; add the two values
            return clauses.get(0).hitsLengthMin() + nfaQuery.hitsLengthMin();
        }
        // Overlapping; use the largest value
        return Math.max(clauses.get(0).hitsLengthMin(), nfaQuery.hitsLengthMin());
    }

    @Override
    public int hitsLengthMax() {
        if (startOfAnchor && direction == DIR_TO_LEFT || !startOfAnchor && direction == DIR_TO_RIGHT) {
            // Non-overlapping; add the two values
            return clauses.get(0).hitsLengthMax() + nfaQuery.hitsLengthMax();
        }
        // Overlapping; use the largest value
        return Math.min(clauses.get(0).hitsLengthMax(), nfaQuery.hitsLengthMax());
    }

    @Override
    public boolean hitsStartPointSorted() {
        if (direction == DIR_TO_RIGHT)
            return clauses.get(0).hitsStartPointSorted();
        return clauses.get(0).hitsStartPointSorted() && nfaQuery.hitsAllSameLength();
    }

    @Override
    public boolean hitsEndPointSorted() {
        if (direction == DIR_TO_LEFT)
            return clauses.get(0).hitsEndPointSorted();
        return clauses.get(0).hitsEndPointSorted() && nfaQuery.hitsAllSameLength();
    }

    @Override
    public boolean hitsHaveUniqueStart() {
        if (direction == DIR_TO_RIGHT)
            return clauses.get(0).hitsHaveUniqueStart();
        return clauses.get(0).hitsHaveUniqueStart() && nfaQuery.hitsAllSameLength() || nfaQuery.hitsHaveUniqueStart();
    }

    @Override
    public boolean hitsHaveUniqueEnd() {
        if (direction == DIR_TO_LEFT)
            return clauses.get(0).hitsHaveUniqueEnd();
        return clauses.get(0).hitsHaveUniqueEnd() && nfaQuery.hitsAllSameLength() || nfaQuery.hitsHaveUniqueEnd();
    }

    @Override
    public boolean hitsAreUnique() {
        return hitsHaveUniqueStart() || hitsHaveUniqueEnd();
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return clauses.get(0).reverseMatchingCost(reader);
    }

    @Override
    public int forwardMatchingCost() {
        return clauses.get(0).forwardMatchingCost() + nfaQuery.forwardMatchingCost();
    }

    public int getDirection() {
        return direction;
    }

    /**
     * Append a new NFA fragment to (a copy of) our existing NFA, and return a new
     * SpanQueryFiSeq using it.
     *
     * @param originalQuery query that yielded this NFA
     * @return newly created, expanded SpanQueryFiSeq
     */
    public SpanQueryFiSeq appendNfa(BLSpanQuery originalQuery) {
        NfaTwoWay newNfa = nfa.copy();
        newNfa.append(originalQuery.getNfaTwoWay(fiAccessor, direction));
        boolean addToRight = direction == DIR_TO_RIGHT;
        SpanQuerySequence newNfaQuery = SpanQuerySequence.sequenceInternalize(nfaQuery, originalQuery, addToRight);
        return new SpanQueryFiSeq(clauses.get(0), startOfAnchor, newNfa, newNfaQuery, direction, fiAccessor);
    }

    public ForwardIndexAccessor getFiAccessor() {
        return fiAccessor;
    }

    @Override
    public Nfa getNfa(ForwardIndexAccessor fiAccessor, int direction) {
        // Convert our anchor to an NFA.
        Nfa anchorNfa = clauses.get(0).getNfa(fiAccessor, direction);

        // Is the direction we want the same as the current direction?
        if (direction == this.direction) {
            // Yes, same direction. Append original NFA to new anchor NFA.
            anchorNfa.append(nfa.getNfa().copy());
            return anchorNfa;
        }
        // Reverse direction. Reverse our original NFA and append the anchor NFA.
        Nfa result = nfa.getNfaReverse().copy();
        result.append(anchorNfa);
        return result;
    }

    @Override
    public boolean canMakeNfa() {
        return clauses.get(0).canMakeNfa();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        // NOTE: we don't need to use nfa or fiAccessor because they follow from nfaQuery
        result = prime * result + direction;
        result = prime * result + ((nfaQuery == null) ? 0 : nfaQuery.hashCode());
        result = prime * result + (startOfAnchor ? 1231 : 1237);
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
        SpanQueryFiSeq other = (SpanQueryFiSeq) obj;
        // NOTE: we don't need to use nfa or fiAccessor because they follow from nfaQuery
        if (direction != other.direction)
            return false;
        if (nfaQuery == null) {
            if (other.nfaQuery != null)
                return false;
        } else if (!nfaQuery.equals(other.nfaQuery))
            return false;
        if (startOfAnchor != other.startOfAnchor)
            return false;
        return true;
    }




}
