package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.List;

/**
 * Simple version of sequence Spans. Requires that:
 *
 * <ul>
 * <li>1st clause is ordered by end point, and end points are unique</li>
 * <li>2nd clause is ordered by start point, and start points are unique</li>
 * </ul>
 *
 * <p>The client should ensure these properties are true.</p>
 */
class SpansSequenceSimple extends BLConjunctionSpans {

    public SpansSequenceSimple(BLSpans firstClause, BLSpans secondClause) {
        super(List.of(firstClause, secondClause), SpanQuerySequence.createGuarantees(List.of(firstClause.guarantees(), secondClause.guarantees())));

        // Validate clause guarantees
        if (!firstClause.guarantees().hitsEndPointSorted())
            throw new IllegalArgumentException("First clause is not end point sorted!");
        if (!firstClause.guarantees().hitsHaveUniqueEnd())
            throw new IllegalArgumentException("First clause does not have unique ends!");
        if (!secondClause.guarantees().hitsStartPointSorted())
            throw new IllegalArgumentException("Second clause is not start point sorted!");
        if (!secondClause.guarantees().hitsHaveUniqueStart())
            throw new IllegalArgumentException("Second clause does have unique starts!");

        atFirstInCurrentDoc = false;
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
        assert startPosition() != NO_MORE_POSITIONS;
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            assert subSpans[0].startPosition() >= 0 && subSpans[0].startPosition() != NO_MORE_POSITIONS;
            assert subSpans[1].startPosition() >= 0 && subSpans[1].startPosition() != NO_MORE_POSITIONS;
            return subSpans[0].startPosition();
        }

        /*
         * Go to the next match.
         *
         * This is done around the 'mid point', the word position where the first match ends and the
         * second match begins.
         *
         * The first Spans are sorted by end point. The matches from this Spans are iterated through, and
         * for each match, the end point will be the 'mid point' of the resulting match. Note that there
         * may be multiple matches from the first with the same end point.
         *
         * The second Spans are sorted by start point (no sorting required, as this is Lucene's default).
         * For each 'mid point', all matches starting at that point are collected from the second spans.
         *
         * Each match from the first is then combined with all the collected matches from the second. The
         * collected matches from the second may be used for multiple matches from the first (if there are
         * multiple matches from the first with the same end point).
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
        int firstEnd = subSpans[0].endPosition();
        int secondStart = subSpans[1].startPosition();
        while (firstEnd != secondStart) {
            if (secondStart < firstEnd) {
                // Advance 2nd if necessary
                while (secondStart < firstEnd) {
                    secondStart = subSpans[1].advanceStartPosition(firstEnd);
                    if (secondStart == NO_MORE_POSITIONS) {
                        oneExhaustedInCurrentDoc = true;
                        return NO_MORE_POSITIONS;
                    }
                }
            } else {
                // Advance 1st if necessary
                while (firstEnd < secondStart) {
                    if (subSpans[0].nextStartPosition() == NO_MORE_POSITIONS) {
                        oneExhaustedInCurrentDoc = true;
                        return NO_MORE_POSITIONS;
                    }
                    firstEnd = subSpans[0].endPosition();
                }
            }
        }
        assert subSpans[0].startPosition() != -1 && subSpans[0].startPosition() != NO_MORE_POSITIONS;
        return subSpans[0].startPosition();
    }

    @Override
    boolean twoPhaseCurrentDocMatches() throws IOException {
        assert docID() >= 0 && docID() != NO_MORE_DOCS;
        atFirstInCurrentDoc = false;
        oneExhaustedInCurrentDoc = false;

        if (subSpans[0].nextStartPosition() == NO_MORE_POSITIONS) {
            oneExhaustedInCurrentDoc = true;
            return false;
        }
        int pos = realignPos();
        if (pos != NO_MORE_POSITIONS) {
            atFirstInCurrentDoc = true;
            assert pos != -1;
            assert subSpans[0].startPosition() >= 0 && subSpans[0].startPosition() != NO_MORE_POSITIONS;
            assert subSpans[1].startPosition() >= 0 && subSpans[1].startPosition() != NO_MORE_POSITIONS;
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

}
