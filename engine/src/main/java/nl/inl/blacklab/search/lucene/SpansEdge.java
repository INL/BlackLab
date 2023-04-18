package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.SpanCollector;

/**
 * Returns either the left edge or right edge of the specified query.
 *
 * Note that the results of this query are zero-length spans.
 */
class SpansEdge extends BLSpans {

    /** query the query to determine edges from */
    private final BLSpans clause;

    /** if true, return the right edges; if false, the left */
    private final boolean rightEdge;

    /**
     * Constructs a SpansEdge.
     * 
     * @param clause the clause to get an edge from
     * @param rightEdge whether or not to get the right edge
     */
    public SpansEdge(BLSpans clause, boolean rightEdge) {
        this.clause = clause;
        this.rightEdge = rightEdge;
    }

    @Override
    public int docID() {
        return clause.docID();
    }

    @Override
    public int startPosition() {
        return rightEdge ? clause.endPosition() : clause.startPosition();
    }

    @Override
    public int endPosition() {
        return rightEdge ? clause.endPosition() : clause.startPosition();
    }

    @Override
    public int nextDoc() throws IOException {
        return clause.nextDoc();
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (rightEdge) {
            if (clause.nextStartPosition() == NO_MORE_POSITIONS)
                return NO_MORE_POSITIONS;
            return clause.endPosition();
        }
        return clause.nextStartPosition();
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        if (rightEdge) {
            // We can't skip because the spans we produce are not sorted by start.
            // Call the naive implementation.
            // (note that this method should not be called on Spans that are not sorted by start,
            //  as it only makes sense in terms of sorted spans)
            if (super.advanceStartPosition(target) == NO_MORE_POSITIONS)
                return NO_MORE_POSITIONS;
            return clause.endPosition();
        }
        return clause.advanceStartPosition(target);
    }

    @Override
    public int advance(int doc) throws IOException {
        return clause.advance(doc);
    }

    @Override
    public String toString() {
        return "SpansEdge(" + clause + ", " + (rightEdge ? "RIGHT" : "LEFT") + ")";
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        clause.setHitQueryContext(context);
    }

    @Override
    public void getMatchInfo(MatchInfo[] relationInfo) {
        if (!childClausesCaptureMatchInfo)
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
