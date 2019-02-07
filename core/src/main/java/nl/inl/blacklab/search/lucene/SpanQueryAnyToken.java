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
import nl.inl.util.LuceneUtil;

/**
 * A SpanQuery matching a number of tokens without any restrictions.
 */
public class SpanQueryAnyToken extends BLSpanQuery {

    /** The minimum number of tokens in this stretch. */
    protected int min;

    /** The maximum number of tokens in this stretch. */
    protected int max;

    String luceneField;

    public SpanQueryAnyToken(int min, int max, String luceneField) {
        this.min = min;
        this.max = max;
        this.luceneField = luceneField;
    }

    @Override
    public boolean matchesEmptySequence() {
        return min == 0;
    }

    @Override
    public BLSpanQuery noEmpty() {
        if (min > 0)
            return this;
        return new SpanQueryAnyToken(1, max, luceneField);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SpanQueryAnyToken) {
            SpanQueryAnyToken tp = ((SpanQueryAnyToken) obj);
            return min == tp.min && max == tp.max;
        }
        return false;
    }

    @Override
    public BLSpanWeight createWeight(final IndexSearcher searcher, boolean needsScores) throws IOException {
        final int realMin = min == 0 ? 1 : min; // always rewritten unless the whole query is optional
        return new BLSpanWeight(this, searcher, null) {
            @Override
            public void extractTerms(Set<Term> terms) {
                // No terms
            }

            @Override
            public void extractTermContexts(Map<Term, TermContext> contexts) {
                // No terms
            }

            @Override
            public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
                return new SpansNGrams(context.reader(), getField(), realMin, max);
            }
        };
    }

    @Override
    public String toString(String field) {
        return "ANYTOKEN(" + min + ", " + inf(max) + ")";
    }

    @Override
    public String getRealField() {
        return luceneField;
    }

    @Override
    public int hashCode() {
        return min + 31 * max + luceneField.hashCode();
    }

    @Override
    public BLSpanQuery inverted() {
        return new SpanQueryNoHits(luceneField); // Just return our clause, dropping the NOT operation
    }

    @Override
    protected boolean okayToInvertForOptimization() {
        // Yes, inverting is actually an improvement
        return min == 1 && max == 1;
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
        return true;
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
        return true;
    }

    @Override
    public Nfa getNfa(ForwardIndexAccessor fiAccessor, int direction) {
        final int realMin = min == 0 ? 1 : min; // always rewritten unless the whole query is optional
        NfaState state = NfaState.anyToken(luceneField, null);
        Nfa frag = new Nfa(state, Arrays.asList(state));
        if (realMin != 1 || max != 1) {
            frag.repeat(realMin, max);
        }
        return frag;
    }

    @Override
    public boolean canMakeNfa() {
        return true;
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        // Should be rewritten, and if not, it matches all positions in the index.
        int numberOfExpansionSteps = max < 0 ? 50 : max - min + 1;
        return LuceneUtil.getSumTotalTermFreq(reader, luceneField) * numberOfExpansionSteps;
    }

    @Override
    public int forwardMatchingCost() {
        int cost = 0;
        int nMax = max == MAX_UNLIMITED ? 50 : max;
        for (int i = min; i <= nMax; i++) {
            cost += i;
        }
        return cost;
    }

    public BLSpanQuery addRep(int addMin, int addMax) {
        int nMin = min + addMin;
        int nMax = BLSpanQuery.addMaxValues(max, addMax);
        return new SpanQueryAnyToken(nMin, nMax, luceneField);
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        return this;
    }

}
