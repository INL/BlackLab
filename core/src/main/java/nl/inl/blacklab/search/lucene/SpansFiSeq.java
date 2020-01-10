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
import java.util.Iterator;
import java.util.NavigableSet;

import org.apache.lucene.search.spans.SpanCollector;

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor.ForwardIndexAccessorLeafReader;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.fimatch.NfaState;

/**
 * Finds hits using the forward index, by matching an NFA from anchor points.
 */
class SpansFiSeq extends BLSpans {

    /** The spans we're (possibly) looking for */
    private BLSpans anchor;

    /** What doc is the anchorSpans in? */
    private int anchorDoc = -1;

    /** Where to get forward index tokens for the current doc */
    private ForwardIndexDocument currentFiDoc;

    /** What start pos is the anchor at? */
    private int anchorStart = -1;

    /**
     * Are we already at the first match in a new document, before
     * nextStartPosition() has been called? Necessary because we have to make sure
     * nextDoc()/advance() actually puts us in a document with at least one match.
     */
    private boolean alreadyAtFirstMatch = false;

    /**
     * If true, match from the start of the anchor hit. Otherwise, match from the
     * end.
     */
    private boolean startOfAnchor;

    /** The NFA to use to find matches in the forward index. */
    private NfaState nfa;

    /** The direction to match in (-1 / DIR_TO_LEFT = backward, 1 / DIR_TO_RIGHT = forward). */
    private int direction;

    /** Maps from term strings to term indices for each annotation. */
    private ForwardIndexAccessorLeafReader fiAccessor;

    /** Iterator over NFA-matched endpoints */
    private Iterator<Integer> matchEndPointIt;

    /** Current NFA-matched endpoint */
    private int currentMatchEndPoint = -1;

    public SpansFiSeq(BLSpans anchorSpans, boolean startOfAnchor, NfaState nfa, int direction,
            ForwardIndexAccessorLeafReader fiAccessor) {
        this.anchor = anchorSpans;
        this.startOfAnchor = startOfAnchor;
        this.nfa = nfa;
        this.direction = direction;
        this.fiAccessor = fiAccessor;
    }

    @Override
    public int docID() {
        return anchorDoc;
    }

    @Override
    public int startPosition() {
        if (alreadyAtFirstMatch)
            return -1; // nextStartPosition() hasn't been called yet
        if (anchorStart == NO_MORE_POSITIONS || anchorStart < 0)
            return anchorStart;
        return direction < 0 ? Math.min(currentMatchEndPoint + 1, anchorStart) : anchorStart;
    }

    @Override
    public int endPosition() {
        if (alreadyAtFirstMatch)
            return -1; // nextStartPosition() hasn't been called yet
        int endPos = anchor.endPosition();
        if (endPos == NO_MORE_POSITIONS || endPos < 0)
            return endPos;
        return direction > 0 ? Math.max(currentMatchEndPoint, anchor.endPosition()) : anchor.endPosition();
    }

    @Override
    public int nextDoc() throws IOException {
        alreadyAtFirstMatch = false;

        // Are we done yet?
        if (anchorDoc == NO_MORE_DOCS)
            return NO_MORE_DOCS;

        // Advance container
        anchorDoc = anchor.nextDoc();
        anchorStart = -1;
        if (anchorDoc == NO_MORE_DOCS) {
            currentFiDoc = null;
            return NO_MORE_DOCS; // no more containers; we're done.
        }
        currentFiDoc = fiAccessor.getForwardIndexDoc(anchorDoc);

        // Find first matching anchor span from here
        return findDocWithMatch();
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (anchorDoc == NO_MORE_DOCS)
            return NO_MORE_POSITIONS;

        if (alreadyAtFirstMatch) {
            // We're already at the first match in the doc. Return it.
            alreadyAtFirstMatch = false;
            return anchorStart;
        }

        // Are we done yet?
        if (anchorStart == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;

        if (matchEndPointIt.hasNext()) {
            currentMatchEndPoint = matchEndPointIt.next();
            return startPosition();
        }

        // Find first matching anchor span from here
        anchorStart = anchor.nextStartPosition();
        return synchronizePos();
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        if (anchorDoc == NO_MORE_DOCS)
            return NO_MORE_POSITIONS;

        if (alreadyAtFirstMatch) {
            alreadyAtFirstMatch = false;
            if (anchorStart >= target)
                return anchorStart;
        }

        // Are we done yet?
        if (anchorStart == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;

        anchorStart = anchor.advanceStartPosition(target);

        // Find first matching anchor span from here
        return synchronizePos();
    }

    /**
     * Find a anchor span (not necessarily in this document) matching with NFA,
     * starting from the current anchor span.
     *
     * @return docID if found, NO_MORE_DOCS if no such anchor span exists (i.e.
     *         we're done)
     * @throws IOException
     */
    private int findDocWithMatch() throws IOException {
        // Find the next "valid" container, if there is one.
        while (anchorDoc != NO_MORE_DOCS) {

            // Are there search results in this document?
            if (anchorStart != NO_MORE_POSITIONS) {
                anchorStart = anchor.nextStartPosition();
            }
            anchorStart = synchronizePos();
            if (anchorStart != NO_MORE_POSITIONS) {
                alreadyAtFirstMatch = true;
                return anchorDoc;
            }

            // No search results found in the current container.
            // Advance to the next container.
            anchorDoc = anchor.nextDoc();
            if (anchorDoc != NO_MORE_DOCS) {
                currentFiDoc = fiAccessor.getForwardIndexDoc(anchorDoc);
            } else {
                currentFiDoc = null;
            }
            anchorStart = -1;
        }
        return anchorDoc;
    }

    /**
     * Find a anchor span that has an NFA match, starting from the current anchor
     * span.
     *
     * @return start position if found, NO_MORE_POSITIONS if no such anchor spans
     *         exists (i.e. we're done)
     * @throws IOException
     */
    private int synchronizePos() throws IOException {
        // Find the next "valid" anchor spans, if there is one.
        while (anchorStart != NO_MORE_POSITIONS) {

            // We're at the first unchecked anchor spans. Does our NFA match?
            int anchorPos = startOfAnchor ? anchorStart : anchor.endPosition();
            if (direction < 0)
                anchorPos--;
            NavigableSet<Integer> setMatchEndpoints = nfa.findMatches(currentFiDoc, anchorPos, direction);
            if (setMatchEndpoints.size() > 0) {
                if (direction == 1)
                    matchEndPointIt = setMatchEndpoints.iterator();
                else
                    matchEndPointIt = setMatchEndpoints.descendingSet().iterator();
                currentMatchEndPoint = matchEndPointIt.next();
                return startPosition();
            }

            // Didn't match filter; go to the next position.
            anchorStart = anchor.nextStartPosition();
            if (anchorStart == NO_MORE_POSITIONS)
                return NO_MORE_POSITIONS;

        }
        return anchorStart;
    }

    @Override
    public int advance(int doc) throws IOException {
        alreadyAtFirstMatch = false;

        // Skip both to doc
        anchorDoc = anchor.advance(doc);
        anchorStart = -1;
        if (anchorDoc == NO_MORE_DOCS) {
            currentFiDoc = null;
            return NO_MORE_DOCS;
        }
        currentFiDoc = fiAccessor.getForwardIndexDoc(anchorDoc);

        // Find first matching anchor span from here
        return findDocWithMatch();
    }

    @Override
    public String toString() {
        return "SpansFiSeq(" + anchor + ", " + (startOfAnchor ? "START" : "END") + ", " + nfa + ", "
                + (direction == 1 ? "FORWARD" : "BACKWARD") + ")";
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        anchor.setHitQueryContext(context);
        // what to do for NFA? (NFAs cannot be used right now if we're trying to capture groups)
    }

    @Override
    public void getCapturedGroups(Span[] capturedGroups) {
        if (!childClausesCaptureGroups)
            return;
        anchor.getCapturedGroups(capturedGroups);
        // what to do for NFA? (NFAs cannot be used right now if we're trying to capture groups)
    }

    @Override
    public int width() {
        return anchor.width();
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        anchor.collect(collector);
    }

    @Override
    public float positionsCost() {
        return anchor.positionsCost();
    }

}
