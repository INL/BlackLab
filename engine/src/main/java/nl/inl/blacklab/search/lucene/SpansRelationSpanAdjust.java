package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.SpanCollector;

/**
 * Adjust relations spans to match source, target, or entire relation.
 */
class SpansRelationSpanAdjust extends BLSpans {

    /** query the query to determine edges from */
    private final BLSpans clause;

    /** how to adjust spans */
    private final MatchInfo.SpanMode mode;

    /**
     * Constructs a SpansEdge.
     *
     * @param clause the clause to get an edge from
     * @param mode how to adjust spans
     */
    public SpansRelationSpanAdjust(BLSpans clause, MatchInfo.SpanMode mode) {
        this.clause = clause;
        this.mode = mode;
    }

    @Override
    public int docID() {
        return clause.docID();
    }

    @Override
    public int startPosition() {
        if (clause.startPosition() == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;
        return clause.getRelationInfo().spanStart(mode);
    }

    @Override
    public int endPosition() {
        if (clause.endPosition() == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;
        return clause.getRelationInfo().spanEnd(mode);
    }

    @Override
    public int nextDoc() throws IOException {
        return clause.nextDoc();
    }

    @Override
    public int nextStartPosition() throws IOException {
        while (true) {
            if (clause.nextStartPosition() == NO_MORE_POSITIONS)
                return NO_MORE_POSITIONS;
            // If we're looking for sources, skip root relations because they have none
            if (!clause.getRelationInfo().isRoot() || mode != MatchInfo.SpanMode.SOURCE) {
                return startPosition();
            }
        }
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        if (mode != MatchInfo.SpanMode.FULL_SPAN) {
            // We can't skip because our spans are not sorted by start.
            // Call the naive implementation.
            if (super.advanceStartPosition(target) == NO_MORE_POSITIONS)
                return NO_MORE_POSITIONS;
        } else {
            if (clause.advanceStartPosition(target) == NO_MORE_POSITIONS)
                return NO_MORE_POSITIONS;
        }
        return startPosition();
    }

    @Override
    public int advance(int doc) throws IOException {
        return clause.advance(doc);
    }

    @Override
    public String toString() {
        return "rspan(" + clause + ", " + mode + ")";
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        clause.setHitQueryContext(context);
    }

    @Override
    public void getMatchInfo(MatchInfo[] relationInfo) {
        if (!childClausesCaptureGroups)
            return;
        clause.getMatchInfo(relationInfo);
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
