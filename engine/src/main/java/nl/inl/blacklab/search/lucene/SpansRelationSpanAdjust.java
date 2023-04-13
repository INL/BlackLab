package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.SpanCollector;

import nl.inl.blacklab.search.Span;

/**
 * Adjust relations spans to match source, target, or entire relation.
 */
class SpansRelationSpanAdjust extends BLSpans {

    /** query the query to determine edges from */
    private final BLSpans clause;

    /** how to adjust spans */
    private final SpanQueryRelationSpanAdjust.Mode mode;

    /**
     * Constructs a SpansEdge.
     *
     * @param clause the clause to get an edge from
     * @param mode how to adjust spans
     */
    public SpansRelationSpanAdjust(BLSpans clause, SpanQueryRelationSpanAdjust.Mode mode) {
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
        switch (mode) {
        case SOURCE:
            return clause.getRelationInfo().getSourceStart();
        case TARGET:
            return clause.getRelationInfo().getTargetStart();
        case FULL_SPAN:
            return clause.getRelationInfo().getFullSpanStart();
        default:
            throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    @Override
    public int endPosition() {
        if (clause.endPosition() == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;
        switch (mode) {
        case SOURCE:
            return clause.getRelationInfo().getSourceEnd();
        case TARGET:
            return clause.getRelationInfo().getTargetEnd();
        case FULL_SPAN:
            return clause.getRelationInfo().getFullSpanEnd();
        default:
            throw new IllegalArgumentException("Unknown mode: " + mode);
        }
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
            if (!clause.getRelationInfo().isRoot() || mode != SpanQueryRelationSpanAdjust.Mode.SOURCE) {
                return startPosition();
            }
        }
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        if (mode != SpanQueryRelationSpanAdjust.Mode.FULL_SPAN) {
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
    public void getCapturedGroups(Span[] capturedGroups) {
        if (!childClausesCaptureGroups)
            return;
        clause.getCapturedGroups(capturedGroups);
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
