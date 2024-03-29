package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.Spans;

/**
 * Wrap a Spans to retrieve matches per document, so we can process all matches
 * in a document efficiently.
 *
 * This way we can retrieve hits per document and perform some operation on them
 * (like sorting or retrieving some extra information). Afterwards we can use
 * HitsPerDocumentSpans to convert the per-document hits into a normal Spans
 * object again.
 */
class SpansInBucketsPerDocument extends SpansInBucketsAbstract {
    public SpansInBucketsPerDocument(BLSpans source) {
        super(source);
    }

    public static SpansInBucketsPerDocument sorted(BLSpans spansFilter) {
        if (spansFilter.guarantees().hitsStartPointSorted()) {
            // Already start point sorted; no need to sort buckets again
            return new SpansInBucketsPerDocument(spansFilter);
        }
        // Not sorted yet; sort buckets
        return new SpansInBucketsPerDocumentSorted(spansFilter, true);
    }

    @Override
    protected void gatherHits() throws IOException {
        assert(source.startPosition() >= 0 && source.startPosition() != Spans.NO_MORE_POSITIONS);
        do {
            addHitFromSource();
        } while (source.nextStartPosition() != Spans.NO_MORE_POSITIONS);
        assert source.startPosition() == Spans.NO_MORE_POSITIONS;
    }

}
