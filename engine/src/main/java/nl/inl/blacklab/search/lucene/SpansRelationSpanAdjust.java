package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.search.spans.FilterSpans;

/**
 * Adjust relations spans to match source, target, or entire relation.
 */
class SpansRelationSpanAdjust extends BLFilterSpans<BLSpans> {

    /** how to adjust spans */
    private final RelationInfo.SpanMode mode;
    
    /** Adjusted start position of current hit */
    private int startAdjusted = -1;

    /** Adjusted end position of current hit */
    private int endAdjusted = -1;

    private HitQueryContext context;

    private MatchInfo[] matchInfo;

    /**
     * Constructs a SpansRelationSpanAdjust.
     *
     * @param in spans to adjust
     * @param mode how to adjust spans
     */
    public SpansRelationSpanAdjust(BLSpans in, RelationInfo.SpanMode mode) {
        super(in, SpanQueryRelationSpanAdjust.createGuarantees(in.guarantees(), mode));
        this.mode = mode;
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        if (mode == RelationInfo.SpanMode.SOURCE & in.getRelationInfo().isRoot()) {
            // Need source, but this has no source
            return FilterSpans.AcceptStatus.NO;
        }
        setAdjustedStartEnd();
        return FilterSpans.AcceptStatus.YES;
    }

    @Override
    public int nextDoc() throws IOException {
        startAdjusted = endAdjusted = -1;
        return super.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        startAdjusted = endAdjusted = -1;
        return super.advance(target);
    }

    @Override
    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        startAdjusted = endAdjusted = -1;
        return super.twoPhaseCurrentDocMatches();
    }

    @Override
    public int startPosition() {
        return startAdjusted;
    }

    @Override
    public int endPosition() {
        return endAdjusted;
    }

    @Override
    public int nextStartPosition() throws IOException {
        super.nextStartPosition();
        setAdjustedStartEnd();
        return startAdjusted;
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        super.passHitQueryContextToClauses(context);
        this.context = context;
    }

    private void setAdjustedStartEnd() {
        if (startPos == NO_MORE_POSITIONS) {
            startAdjusted = endAdjusted = NO_MORE_POSITIONS;
        } else if (atFirstInCurrentDoc || startPos < 0) {
            startAdjusted = endAdjusted = -1;
        } else if (mode == RelationInfo.SpanMode.ALL_SPANS) {
            // We need all match info because we want the full span including all matched relations
            if (matchInfo == null)
                matchInfo = new MatchInfo[context.numberOfMatchInfos()];
            else
                Arrays.fill(matchInfo, null);
            in.getMatchInfo(matchInfo);
            startAdjusted = Integer.MAX_VALUE;
            endAdjusted = Integer.MIN_VALUE;
            for (int i = 0; i < matchInfo.length; i++) {
                MatchInfo info = matchInfo[i];
                if (info != null && !info.isTag() && !info.isSpan()) {
                    // This is a relations match. Take this into account for the full span.
                    if (info.getFullSpanStart() < startAdjusted)
                        startAdjusted = info.getFullSpanStart();
                    if (info.getFullSpanEnd() > endAdjusted)
                        endAdjusted = info.getFullSpanEnd();
                }
            }
            if (startAdjusted == Integer.MAX_VALUE) {
                // Weird, no relations matched; use the original span (this should never happen though)
                startAdjusted = in.getRelationInfo().spanStart(RelationInfo.SpanMode.FULL_SPAN);
                endAdjusted = in.getRelationInfo().spanEnd(RelationInfo.SpanMode.FULL_SPAN);
            }
        } else {
            startAdjusted = in.getRelationInfo().spanStart(mode);
            endAdjusted = in.getRelationInfo().spanEnd(mode);
        }
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        if (this.matchInfo != null) {
            // We've already retrieved our clause's match info. Use that.
            for (int i = 0; i < matchInfo.length; i++) {
                if (matchInfo[i] == null)
                    matchInfo[i] = this.matchInfo[i];
            }
        } else {
            super.getMatchInfo(matchInfo);
        }
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        if (atFirstInCurrentDoc && startPos >= target) {
            // Our cached hit is the one we want.
            return nextStartPosition();
        }
        if (mode != RelationInfo.SpanMode.FULL_SPAN) {
            // We can't skip because the spans we produce are not guaranteed to be sorted by start position.
            // Call the naive implementation.
            if (BLSpans.naiveAdvanceStartPosition(this, target) == NO_MORE_POSITIONS) {
                startPos = startAdjusted = endAdjusted = NO_MORE_POSITIONS;
            }
        } else {
            // We know our spans will be in order, so we can use the more efficient advanceStartPosition()
            if (in.advanceStartPosition(target) == NO_MORE_POSITIONS) {
                startPos = startAdjusted = endAdjusted = NO_MORE_POSITIONS;
            } else {
                setAdjustedStartEnd();
            }
        }
        return startAdjusted;
    }

    @Override
    public String toString() {
        return "RSPAN(" + in + ", " + mode + ")";
    }

}
