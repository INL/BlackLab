package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.FilterSpans;

/**
 * Captures its clause as a captured group.
 *
 * Registers itself with the HitQueryContext so others can access its start()
 * and end() when they want to.
 */
class SpansCaptureGroup extends BLFilterSpans<BLSpans> {

    /** group name */
    private final String name;

    /**
     * group index (where in the Spans[] to place our start/end position in
     * getCapturedGroups())
     */
    private int groupIndex;

    /**
     * How to adjust the left edge of the captured hits while matching. (necessary
     * because we try to internalize constant-length neighbouring clauses into our
     * clause to speed up matching)
     */
    final int leftAdjust;

    /**
     * How to adjust the right edge of the captured hits while matching. (necessary
     * because we try to internalize constant-length neighbouring clauses into our
     * clause to speed up matching)
     */
    private final int rightAdjust;

    /**
     * Constructs a SpansCaptureGroup.
     *
     * @param clause the clause to capture
     * @param name group name
     */
    public SpansCaptureGroup(BLSpans clause, String name, int leftAdjust, int rightAdjust) {
        super(clause);
        this.name = name;
        this.leftAdjust = leftAdjust;
        this.rightAdjust = rightAdjust;
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        return FilterSpans.AcceptStatus.YES;
    }

    @Override
    public String toString() {
        String adj = (leftAdjust != 0 || rightAdjust != 0 ? ", " + leftAdjust + ", " + rightAdjust : "");
        return "CAPTURE(" + in + ", " + name + adj + ")";
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        super.passHitQueryContextToClauses(context);
        this.groupIndex = context.registerMatchInfo(name);
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        super.getMatchInfo(matchInfo);
        // Place our start and end position at the correct index in the array
        int start = startPosition() + leftAdjust;
        int end = endPosition() + rightAdjust;
        matchInfo[groupIndex] = SpanInfo.create(start, end, getOverriddenField());
    }

    @Override
    public boolean hasMatchInfo() {
        return true;
    }
}
