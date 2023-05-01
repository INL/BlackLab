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

import org.apache.lucene.search.spans.FilterSpans;
import org.apache.lucene.search.spans.Spans;

/**
 * A {@link Spans} implementation wrapping another spans instance, allowing to filter spans matches
 * easily by implementing {@link #accept}
 * <p>
 * Adapted from Lucene.
 * <p>
 * NOTE: We've relaxed the visibility of some members to allow more reuse (see e.g. {@link SpansEdge}),
 * at the cost of child classes having to know more about the implementation. Specifically: match-level methods
 * are no longer final, and child classes can access atFirstInCurrentDoc and startPos, but should make sure
 * these are updated correctly when overriding methods)
 */
public abstract class BLFilterSpans<T extends Spans> extends BLFilterDocsSpans<T> {

    /**
     * Did we advance to the first hit already through the two-phase iterator?
     * If true, startPos will contain the start position of the first hit,
     * but we should still return -1 to indicate nextStartPosition() hasn't been called
     * yet.
     */
    protected boolean atFirstInCurrentDoc = false;

    /**
     * Start position of current hit, or (if atFirstInCurrentDoc is true) the first
     * hit to return when nextStartPosition() is called.
     */
    protected int startPos = -1;

    /**
     * Wrap the given {@link T}.
     */
    protected BLFilterSpans(T in) {
        super(in);
    }

    /**
     * Returns YES if the candidate should be an accepted match, NO if it should not, and
     * NO_MORE_IN_CURRENT_DOC if iteration should move on to the next document.
     */
    protected abstract FilterSpans.AcceptStatus accept(T candidate) throws IOException;

    @Override
    public int nextDoc() throws IOException {
        atFirstInCurrentDoc = false;
        startPos = -1;
        return super.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        atFirstInCurrentDoc = false;
        startPos = -1;
        return super.advance(target);
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            assert startPos != -1 && startPos != NO_MORE_POSITIONS;
            return startPos;
        }
        return goToNextMatch(true);
    }

    private int goToNextMatch(boolean nextImmediately) throws IOException {
        boolean next = nextImmediately;
        for (;;) {
            if (next)
                startPos = in.nextStartPosition();
            assert startPos >= 0;
            next = true;
            if (startPos == NO_MORE_POSITIONS) {
                return NO_MORE_POSITIONS;
            }
            switch(accept(in)) {
            case YES:
                return startPos;
            case NO:
                break;
            case NO_MORE_IN_CURRENT_DOC:
                return startPos = NO_MORE_POSITIONS;
            }
        }
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        if (atFirstInCurrentDoc && startPos >= target) {
            // Our cached hit is the one we want.
            return nextStartPosition();
        }
        if (in.startPosition() >= target) {
            // we always advance at least 1 hit, as per contract
            return nextStartPosition();
        }
        if (in instanceof BLSpans) {
            startPos = ((BLSpans) in).advanceStartPosition(target);
            return goToNextMatch(false);
        }
        return super.advanceStartPosition(target);
    }

    @Override
    public int startPosition() {
        return atFirstInCurrentDoc ? -1 : startPos;
    }

    @Override
    public int endPosition() {
        return atFirstInCurrentDoc ? -1
                : (startPos != NO_MORE_POSITIONS) ? in.endPosition() : NO_MORE_POSITIONS;
    }

    /**
     * Returns true if the current document matches.
     * <p>
     * This is called during two-phase processing.
     */
    @SuppressWarnings("fallthrough")
    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        atFirstInCurrentDoc = false;
        startPos = -1;
        assert docID() != -1 && docID() != NO_MORE_DOCS;
        assert startPosition() == -1;
        assert in.startPosition() == -1;
        startPos = in.nextStartPosition();
        assert startPos != NO_MORE_POSITIONS && startPos != -1;
        for (;;) {
            switch(accept(in)) {
            case YES:
                atFirstInCurrentDoc = true;
                return true;
            case NO:
                startPos = in.nextStartPosition();
                if (startPos != NO_MORE_POSITIONS) {
                    break;
                }
                // else fallthrough
            case NO_MORE_IN_CURRENT_DOC:
                startPos = -1;
                return false;
            }
        }
    }
}
