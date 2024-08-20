package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.search.spans.FilterSpans;

/**
 * Gets the hits in other fields (from the matchInfo).
 *
 * For e.g. parallel corpora, there is a source field and
 * one or more target fields. Normally, the hits returned are in
 * the source field, and the corresponding target field(s) hits
 * are returned for each source hit (if there is one).
 *
 * This class changes the hits to only be for one of the target
 * fields. This is useful for e.g. highlighting.
 */
class SpansOtherFieldHits extends BLFilterSpans<BLSpans> {

    private String targetField;

    /** Adjusted start position of current hit */
    private int startAdjusted = -1;

    /** Adjusted end position of current hit */
    private int endAdjusted = -1;

    private HitQueryContext context;

    /** Our clause's match info */
    private MatchInfo[] matchInfo;

    /** What field is our clause in? */
    private final String clauseField;

    /**
     * Constructs a SpansRelationSpanAdjust.
     *
     * @param in spans to adjust
     * @param targetField other field to get hits for
     *
     */
    public SpansOtherFieldHits(BLSpans in, String targetField, String clauseField) {
        super(in, SpanQueryOtherFieldHits.createGuarantees());
        this.targetField = targetField;
        this.clauseField = clauseField;
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        // Check if this source hit has a corresponding hit in our target field.
        if (startPos == NO_MORE_POSITIONS) {
            startAdjusted = endAdjusted = NO_MORE_POSITIONS;
            return FilterSpans.AcceptStatus.NO_MORE_IN_CURRENT_DOC;
        } else if (atFirstInCurrentDoc || startPos < 0) {
            startAdjusted = endAdjusted = -1;
        } else {
            // We need all match info because we want to expand the current span to include all matched relations
            if (matchInfo == null)
                matchInfo = new MatchInfo[context.numberOfMatchInfos()];
            else
                Arrays.fill(matchInfo, null);
            in.getMatchInfo(matchInfo);
            startAdjusted = Integer.MAX_VALUE;
            endAdjusted = Integer.MIN_VALUE;
            for (int i = 0; i < matchInfo.length; i++) {
                MatchInfo info = matchInfo[i];
                if (info != null) {
                    if (targetField.equals(info.getField())) {
                        // This match info's (source) span is in our target field
                        startAdjusted = Math.min(startAdjusted, info.getSpanStart());
                        endAdjusted = Math.max(endAdjusted, info.getSpanEnd());
                    }
                    if (info.getType() == MatchInfo.Type.RELATION) {
                        RelationInfo rel = (RelationInfo) info;
                        if (rel.isCrossFieldRelation() && rel.getTargetField().equals(targetField)) {
                            // Target of cross-field relation to our targetField.
                            startAdjusted = Math.min(startAdjusted, rel.getTargetStart());
                            endAdjusted = Math.max(endAdjusted, rel.getTargetEnd());
                        }
                    }
                }
            }
        }
        if (startAdjusted == Integer.MAX_VALUE) {
            // No hit in our target field.
            startAdjusted = endAdjusted = -1;
        }
        return startAdjusted >= 0 ? FilterSpans.AcceptStatus.YES : FilterSpans.AcceptStatus.NO;
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        startAdjusted = endAdjusted = -1;
        return super.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        startAdjusted = endAdjusted = -1;
        return super.advance(target);
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (super.nextStartPosition() == NO_MORE_POSITIONS) {
            startAdjusted = endAdjusted = NO_MORE_POSITIONS;
            return NO_MORE_POSITIONS;
        }
        return startAdjusted;
    }

    @Override
    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        assert positionedInDoc();
        startAdjusted = endAdjusted = -1;
        return super.twoPhaseCurrentDocMatches();
    }

    @Override
    public int startPosition() {
        return atFirstInCurrentDoc ? -1 : startAdjusted;
    }

    @Override
    public int endPosition() {
        return atFirstInCurrentDoc ? -1 : endAdjusted;
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        this.context = context;
        super.passHitQueryContextToClauses(context.withField(clauseField));
    }

    @Override
    public String getOverriddenField() {
        return targetField;
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        for (int i = 0; i < matchInfo.length; i++) {
            // Only pass on the match info for our target field
            if (this.matchInfo[i] != null && this.matchInfo[i].getField().equals(targetField))
                matchInfo[i] = this.matchInfo[i];
        }
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        assert target > startPosition();
        // We can't skip because the spans we produce are not guaranteed to be sorted by start position.
        // Call the naive implementation.
        if (BLSpans.naiveAdvanceStartPosition(this, target) == NO_MORE_POSITIONS) {
            startPos = startAdjusted = endAdjusted = NO_MORE_POSITIONS;
        }
        return startAdjusted;
    }

    @Override
    public String toString() {
        return "RFIELD(" + in + ", " + targetField + ")";
    }

}
