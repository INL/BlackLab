package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.Spans;

/**
 * Wrap a Spans to retrieve consecutive matches.
 *
 * This is used for repetition regex operators such as * and +.
 */
class SpansInBucketsConsecutive extends SpansInBucketsAbstract {
    public SpansInBucketsConsecutive(BLSpans source) {
        super(source);
    }

    @Override
    protected void gatherHits() throws IOException {
        int lastEnd = source.startPosition();
        while (source.startPosition() == lastEnd) {
            addHitFromSource();
            lastEnd = source.endPosition();
            if (source.nextStartPosition() == Spans.NO_MORE_POSITIONS)
                break;
        }
    }
}
