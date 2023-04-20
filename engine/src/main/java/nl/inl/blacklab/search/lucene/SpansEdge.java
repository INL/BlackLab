package nl.inl.blacklab.search.lucene;

import static org.apache.lucene.search.spans.FilterSpans.AcceptStatus.YES;

import java.io.IOException;

import org.apache.lucene.search.spans.FilterSpans;

/**
 * Returns either the left edge or right edge of the specified query.
 * <p>
 * Note that the results of this query are zero-length spans.
 */
class SpansEdge extends BLFilterSpans<BLSpans> {

    /** if true, return the right edges; if false, the left */
    private final boolean rightEdge;

    /**
     * Constructs a SpansEdge.
     *
     * @param clause the clause to get an edge from
     * @param rightEdge whether or not to get the right edge
     */
    public SpansEdge(BLSpans clause, boolean rightEdge) {
        super(clause);
        this.rightEdge = rightEdge;
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        return YES;
    }

    @Override
    public int startPosition() {
        return atFirstInCurrentDoc ? -1 : (rightEdge ? in.endPosition() : startPos);
    }

    @Override
    public int endPosition() {
        return startPosition();
    }

    @Override
    public int nextStartPosition() throws IOException {
        super.nextStartPosition();
        return rightEdge ? in.endPosition() : startPos;
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        if (rightEdge) {
            // We can't skip because the spans we produce are not sorted by start.
            // Use the naive implementation.
            BLSpans.naiveAdvanceStartPosition(this, target);
            return startPos == NO_MORE_POSITIONS ? startPos : in.endPosition();
        }
        return super.advanceStartPosition(target);
    }

    @Override
    public String toString() {
        return "EDGE(" + in + ", " + (rightEdge ? "RIGHT" : "LEFT") + ")";
    }

}
