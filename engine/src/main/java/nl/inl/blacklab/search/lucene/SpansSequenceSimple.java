package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.List;

/**
 * Simple version of sequence Spans. Assumes that:
 * 
 * <p>
 * <ul>
 * <li>right side is ordered by start point, and start points are unique
 * <li>left side is ordered by end point, and end points are unique
 * </ul>
 * 
 * The client should ensure these properties are true.
 */
class SpansSequenceSimple extends BLConjunctionSpans {

    public SpansSequenceSimple(BLSpans leftClause, BLSpans rightClause) {
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
        return atFirstInCurrentDoc ? -1 : subSpans[1].endPosition();
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            return subSpans[0].startPosition();
        }

        /*
         * Go to the next match.
         *
         * This is done around the 'mid point', the word position where the left match ends and the
         * right match begins.
         *
         * The left Spans are sorted by end point. The matches from this Spans are iterated through, and
         * for each match, the end point will be the 'mid point' of the resulting match. Note that there
         * may be multiple matches from the left with the same end point.
         *
         * The right Spans are sorted by start point (no sorting required, as this is Lucene's default).
         * For each 'mid point', all matches starting at that point are collected from the right spans.
         *
         * Each match from the left is then combined with all the collected matches from the right. The
         * collected matches from the right may be used for multiple matches from the left (if there are
         * multiple matches from the left with the same end point).
         */

        if (oneExhaustedInCurrentDoc)
            return NO_MORE_POSITIONS;

        if (subSpans[0].nextStartPosition() == NO_MORE_POSITIONS) {
            oneExhaustedInCurrentDoc = true;
            return NO_MORE_POSITIONS;
        }
        return realignPos();
    }

    private int realignPos() throws IOException {
        // Synchronize within doc
        int leftEnd = subSpans[0].endPosition();
        int rightStart = subSpans[1].startPosition();
        while (leftEnd != rightStart) {
            if (rightStart < leftEnd) {
                // Advance right if necessary
                while (rightStart < leftEnd) {
                    rightStart = subSpans[1].advanceStartPosition(leftEnd);
                    if (rightStart == NO_MORE_POSITIONS) {
                        oneExhaustedInCurrentDoc = true;
                        return NO_MORE_POSITIONS;
                    }
                }
            } else {
                // Advance left if necessary
                while (leftEnd < rightStart) {
                    if (subSpans[0].nextStartPosition() == NO_MORE_POSITIONS) {
                        oneExhaustedInCurrentDoc = true;
                        return NO_MORE_POSITIONS;
                    }
                    leftEnd = subSpans[0].endPosition();
                }
            }
        }
        return subSpans[0].startPosition();
    }

    @Override
    boolean twoPhaseCurrentDocMatches() throws IOException {
        if (oneExhaustedInCurrentDoc)
            return false;

        if (subSpans[0].nextStartPosition() == NO_MORE_POSITIONS) {
            oneExhaustedInCurrentDoc = true;
            return false;
        }
        int pos = realignPos();
        if (pos != NO_MORE_POSITIONS) {
            atFirstInCurrentDoc = true;
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "SEQSIMPLE(" + subSpans[0] + ", " + subSpans[1] + ")";
    }

    @Override
    public int width() {
        return subSpans[0].width() + subSpans[1].width();
    }

    @Override
    public float positionsCost() {
        return subSpans[0].positionsCost() + subSpans[1].positionsCost();
    }

}
