/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Objects;

import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.search.spans.Spans;

/**
 * A {@link Spans} implementation wrapping another spans instance, providing a framework for quickly skipping
 * non-matching docs.
 * <p>
 * Based on Lucene's FilterSpans.
 */
public abstract class BLFilterDocsSpans<T extends Spans> extends BLSpans {

    /**
     * The Spans object we're wrapping
     */
    protected final T in;

    /**
     * Wrap the given {@link T}.
     */
    protected BLFilterDocsSpans(T in) {
        this.in = Objects.requireNonNull(in);
    }

    @Override
    public int nextDoc() throws IOException {
        while (true) {
            int doc = in.nextDoc();
            if (doc == NO_MORE_DOCS) {
                return NO_MORE_DOCS;
            } else if (twoPhaseCurrentDocMatches()) {
                return doc;
            }
        }
    }

    @Override
    public int advance(int target) throws IOException {
        int doc = in.advance(target);
        while (doc != NO_MORE_DOCS) {
            if (twoPhaseCurrentDocMatches()) {
                break;
            }
            doc = in.nextDoc();
        }

        return doc;
    }

    @Override
    public final int docID() {
        return in.docID();
    }

    @Override
    public int width() {
        return in.width();
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        in.collect(collector);
    }

    @Override
    public final long cost() {
        return in.cost();
    }

    @Override
    public abstract String toString();

    @Override
    public final TwoPhaseIterator asTwoPhaseIterator() {
        TwoPhaseIterator inner = in.asTwoPhaseIterator();
        if (inner != null) {
            // wrapped instance has an approximation
            return new TwoPhaseIterator(inner.approximation()) {
                @Override
                public boolean matches() throws IOException {
                    return twoPhaseCurrentDocMatches();
                }

                @Override
                public float matchCost() {
                    return inner.matchCost(); // underestimate
                }

                @Override
                public String toString() {
                    return "BLFilterSpans@asTwoPhaseIterator(inner=" + inner + ", in=" + in + ")";
                }
            };
        } else {
            // wrapped instance has no approximation, but 
            // we can still defer matching until absolutely needed.
            return new TwoPhaseIterator(in) {
                @Override
                public boolean matches() throws IOException {
                    return twoPhaseCurrentDocMatches();
                }

                @Override
                public float matchCost() {
                    return in.positionsCost(); // overestimate
                }

                @Override
                public String toString() {
                    return "BLFilterSpans@asTwoPhaseIterator(in=" + in + ")";
                }
            };
        }
    }

    @Override
    public float positionsCost() {
        //throw new UnsupportedOperationException(); // asTwoPhaseIterator never returns null
        return in.positionsCost();
    }

    /**
     * Returns true if the current document matches.
     * <p>
     * This is called during two-phase processing.
     */
    protected abstract boolean twoPhaseCurrentDocMatches() throws IOException;

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        if (in instanceof BLSpans)
            ((BLSpans) in).setHitQueryContext(context);
    }

    @Override
    public void getMatchInfo(MatchInfo[] relationInfo) {
        if (childClausesCaptureMatchInfo && in instanceof BLSpans)
            ((BLSpans) in).getMatchInfo(relationInfo);
    }

}
