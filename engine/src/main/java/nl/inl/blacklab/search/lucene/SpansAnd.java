package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.List;

/**
 * Combines two Spans using AND. Note that this means that only matches with the
 * same document id, the same start and the same end positions will be kept.
 */
class SpansAnd extends BLConjunctionSpans {

    /**
     * Construct SpansAnd.
     * <p>
     * Clauses must be start-point sorted.
     *
     * @param leftClause left clause
     * @param rightClause right clause
     */
    public SpansAnd(BLSpans leftClause, BLSpans rightClause) {
        super(List.of(leftClause, rightClause));
    }

    @Override
    public int startPosition() {
        if (oneExhaustedInCurrentDoc)
            return NO_MORE_POSITIONS;
        return atFirstInCurrentDoc ? -1 : subSpans[0].startPosition();
    }

    @Override
    public int endPosition() {
        if (oneExhaustedInCurrentDoc)
            return NO_MORE_POSITIONS;
        return atFirstInCurrentDoc ? -1 : subSpans[0].endPosition();
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (oneExhaustedInCurrentDoc)
            return NO_MORE_POSITIONS;
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            return subSpans[0].startPosition();
        }
        subSpans[0].nextStartPosition();
        subSpans[1].nextStartPosition();
        return synchronizePosition();
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        if (oneExhaustedInCurrentDoc)
            return NO_MORE_POSITIONS;
        int startPos = startPosition();
        if (startPos >= target)
            return nextStartPosition(); // already at or beyond target. per contract, return next match
        for (BLSpans subSpan: subSpans) {
            subSpan.advanceStartPosition(target);
        }
        return synchronizePosition();
    }

    private int synchronizePosition() throws IOException {
        while (true) {
            int leftStart = subSpans[0].startPosition();
            int rightStart = subSpans[1].startPosition();
            if (oneExhaustedInCurrentDoc)
                return NO_MORE_POSITIONS;

            // Synch at match start level
            if ((leftStart == -1 && rightStart == -1) ||
                    leftStart != rightStart) {
                // Starts don't match
                int laggingSpans = leftStart < rightStart ? 0 : 1;
                catchUpMatchStart(laggingSpans);
            } else if (subSpans[0].endPosition() != subSpans[1].endPosition()) {
                // Starts match but ends don't
                int laggingSpans = subSpans[0].endPosition() < subSpans[1].endPosition() ? 0 : 1;
                catchUpMatchEnd(laggingSpans);
            } else {
                // Both match
                leftStart = subSpans[0].startPosition();
                if (leftStart == NO_MORE_POSITIONS || subSpans[1].startPosition() == NO_MORE_POSITIONS) {
                    oneExhaustedInCurrentDoc = true;
                    return NO_MORE_POSITIONS;
                }
                return leftStart;
            }
        }
    }

    /** See if we can get starts to line up. */
    private void catchUpMatchStart(int laggingSpans) throws IOException {
        int catchUpTo = subSpans[1 - laggingSpans].startPosition();
        int catchUpFrom = subSpans[laggingSpans].startPosition();
        if (catchUpFrom < catchUpTo || catchUpFrom == -1) { // also covers catchUpFrom != NO_MORE_POSITIONS
            subSpans[laggingSpans].advanceStartPosition(catchUpTo);
        }
    }

    /** Try to get ends to line up without moving starts. */
    private void catchUpMatchEnd(int laggingSpans) throws IOException {
        int catchUpFromStart = subSpans[laggingSpans].startPosition();
        int catchUpToEnd = subSpans[1 - laggingSpans].endPosition();
        while ((subSpans[laggingSpans].startPosition() == catchUpFromStart &&
                subSpans[laggingSpans].endPosition() < catchUpToEnd) || subSpans[laggingSpans].startPosition() == -1) {
            subSpans[laggingSpans].nextStartPosition();
        }
    }

    @Override
    boolean twoPhaseCurrentDocMatches() throws IOException {
        // Note that we DON't use our nextStartPosition() here because atFirstInCurrentDoc
        // is not properly set yet at this point in time (we do that below).
        subSpans[0].nextStartPosition();
        subSpans[1].nextStartPosition();
        int start = synchronizePosition();
        if (start == NO_MORE_DOCS)
            return false;
        atFirstInCurrentDoc = true;
        return true;
    }

    @Override
    public String toString() {
        return "AND(" + subSpans[0] + ", " + subSpans[1] + ")";
    }

    @Override
    public int width() {
        return subSpans[0].width() + subSpans[1].width();
    }

}
