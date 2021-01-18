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
import java.util.Collection;
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
 * Returns all tokens that do not occur in the matches of the specified query.
 *
 * Each token is returned as a single hit.
 */
public class SpanQueryNot extends BLSpanQueryAbstract {

    public SpanQueryNot(BLSpanQuery query) {
        super(query);
    }

    public SpanQueryNot(Collection<BLSpanQuery> clauscol) {
        super(clauscol);
    }

    public SpanQueryNot(BLSpanQuery[] clauses) {
        super(clauses);
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery rewritten = clauses.get(0).rewrite(reader);

        // Can we cancel out a double not?
        if (rewritten.okayToInvertForOptimization())
            return rewritten.inverted(); // yes

        // No, must remain a NOT
        if (rewritten == clauses.get(0)) {
            return this;
        }

        return new SpanQueryNot(rewritten);
    }

    @Override
    public BLSpanQuery inverted() {
        return clauses.get(0); // Just return our clause, dropping the NOT operation
    }

    @Override
    protected boolean okayToInvertForOptimization() {
        // Yes, inverting is actually an improvement
        return true;
    }

    @Override
    public boolean isSingleTokenNot() {
        return true;
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        BLSpanQuery query = clauses.get(0);
        BLSpanWeight weight = query == null ? null : query.createWeight(searcher, needsScores);
        return new SpanWeightNot(weight, searcher, needsScores ? getTermContexts(weight) : null);
    }

    class SpanWeightNot extends BLSpanWeight {

        final BLSpanWeight weight;

        public SpanWeightNot(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermContext> terms)
                throws IOException {
            super(SpanQueryNot.this, searcher, terms);
            this.weight = weight;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            if (weight != null)
                weight.extractTerms(terms);
        }

        @Override
        public void extractTermContexts(Map<Term, TermContext> contexts) {
            if (weight != null)
                weight.extractTermContexts(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans spans = weight == null ? null : weight.getSpans(context, requiredPostings);
            if (!clauses.get(0).hitsStartPointSorted())
                spans = BLSpans.optSortUniq(spans, true, false);
            return new SpansNot(context.reader(), baseFieldName, spans);
        }

    }

    @Override
    public String toString(String field) {
        return "NOT(" + (clauses.get(0) == null ? "" : clausesToString(field)) + ")";
    }

    @Override
    public boolean hitsAllSameLength() {
        return true;
    }

    @Override
    public int hitsLengthMin() {
        return 1;
    }

    @Override
    public int hitsLengthMax() {
        return 1;
    }

    @Override
    public boolean hitsEndPointSorted() {
        return true;
    }

    @Override
    public boolean hitsStartPointSorted() {
        return true;
    }

    @Override
    public boolean hitsHaveUniqueStart() {
        return true;
    }

    @Override
    public boolean hitsHaveUniqueEnd() {
        return true;
    }

    @Override
    public boolean hitsAreUnique() {
        return true;
    }

    @Override
    public Nfa getNfa(ForwardIndexAccessor fiAccessor, int direction) {
        Nfa nfa = clauses.get(0).getNfa(fiAccessor, direction);
        //nfa.finish();
        nfa.invert();
        NfaState not = nfa.getStartingState();
        return new Nfa(not, Arrays.asList(not)); // ignore the dangling arrows in the clause we've inverted
    }

    @Override
    public boolean canMakeNfa() {
        return clauses.get(0).canMakeNfa();
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        // Should be rewritten, but if it can't, calculate a rough indication of the number of token hits
        long freq = clauses.get(0).reverseMatchingCost(reader);
        return LuceneUtil.getSumTotalTermFreq(reader, getRealField()) - freq;
    }

    @Override
    public int forwardMatchingCost() {
        return clauses.get(0).forwardMatchingCost() + 1;
    }

    // no hashCode() and equals() because super class version is sufficient

}
