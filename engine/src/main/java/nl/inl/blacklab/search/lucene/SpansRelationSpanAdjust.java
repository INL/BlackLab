package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.FilterSpans;

/**
 * Adjust relations spans to match source, target, or entire relation.
 */
class SpansRelationSpanAdjust extends BLFilterSpans<BLSpans> {

    /** how to adjust spans */
    private final MatchInfo.SpanMode mode;

    /**
     * Constructs a SpansRelationSpanAdjust.
     *
     * @param in spans to adjust
     * @param mode how to adjust spans
     */
    public SpansRelationSpanAdjust(BLSpans in, MatchInfo.SpanMode mode) {
        super(in);
        this.mode = mode;
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        if (mode == MatchInfo.SpanMode.SOURCE & in.getRelationInfo().isRoot()) {
            // Need source, but this has no source
            return FilterSpans.AcceptStatus.NO;
        }
        return FilterSpans.AcceptStatus.YES;
    }

    @Override
    public int startPosition() {
        if (atFirstInCurrentDoc || startPos < 0)
            return -1;
        if (startPos == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;
        return in.getRelationInfo().spanStart(mode);
    }

    @Override
    public int endPosition() {
        if (atFirstInCurrentDoc || startPos < 0)
            return -1;
        if (startPos == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;
        return in.getRelationInfo().spanEnd(mode);
    }

    @Override
    public int nextStartPosition() throws IOException {
        super.nextStartPosition();
        return startPosition();
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        if (atFirstInCurrentDoc && startPos >= target) {
            // Our cached hit is the one we want.
            return nextStartPosition();
        }
        if (mode != MatchInfo.SpanMode.FULL_SPAN) {
            // We can't skip because our spans are not sorted by start.
            // Call the naive implementation.
            if (BLSpans.naiveAdvanceStartPosition(this, target) == NO_MORE_POSITIONS) {
                startPos = NO_MORE_POSITIONS;
                return NO_MORE_POSITIONS;
            }
        } else {
            if (in.advanceStartPosition(target) == NO_MORE_POSITIONS) {
                startPos = NO_MORE_POSITIONS;
                return NO_MORE_POSITIONS;
            }
        }
        return startPosition();
    }

    @Override
    public String toString() {
        return "RSPAN(" + in + ", " + mode + ")";
    }

}
