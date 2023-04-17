package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.SpanCollector;

/**
 * Captures its clause as a captured group.
 *
 * Registers itself with the HitQueryContext so others can access its start()
 * and end() when they want to.
 */
class SpansCaptureGroup extends BLSpans {

    /** clause to capture as a group */
    private final BLSpans clause;

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
        this.clause = clause;
        this.name = name;
        this.leftAdjust = leftAdjust;
        this.rightAdjust = rightAdjust;
    }

    /**
     * @return the Lucene document id of the current hit
     */
    @Override
    public int docID() {
        return clause.docID();
    }

    /**
     * @return start position of current hit
     */
    @Override
    public int startPosition() {
        return clause.startPosition();
    }

    /**
     * @return end position of current hit
     */
    @Override
    public int endPosition() {
        return clause.endPosition();
    }

    @Override
    public int nextDoc() throws IOException {
        return clause.nextDoc();
    }

    @Override
    public int nextStartPosition() throws IOException {
        return clause.nextStartPosition();
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        return clause.advanceStartPosition(target);
    }

    /**
     * Skip to the specified document (or the first document after it containing
     * hits).
     *
     * @param doc the doc number to skip to (or past)
     * @return true if we're still pointing to a valid hit, false if we're done
     */
    @Override
    public int advance(int doc) throws IOException {
        return clause.advance(doc);
    }

    @Override
    public String toString() {
        String adj = (leftAdjust != 0 || rightAdjust != 0 ? ", " + leftAdjust + ", " + rightAdjust : "");
        return "CAPTURE(" + clause + ", " + name + adj + ")";
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        super.setHitQueryContext(context);
        this.groupIndex = context.registerMatchInfo(name);
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        clause.setHitQueryContext(context);
    }

    @Override
    public void getMatchInfo(MatchInfo[] relationInfo) {
        if (childClausesCaptureGroups)
            clause.getMatchInfo(relationInfo);

        // Place our start and end position at the correct index in the array
        relationInfo[groupIndex] = MatchInfo.captureGroupSpan(startPosition() + leftAdjust, endPosition() + rightAdjust);
    }

    @Override
    public int width() {
        return clause.width();
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        clause.collect(collector);
    }

    @Override
    public float positionsCost() {
        return clause.positionsCost();
    }

}
