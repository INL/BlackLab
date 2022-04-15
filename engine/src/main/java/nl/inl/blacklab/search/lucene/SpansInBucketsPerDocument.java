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

    @Override
    protected void gatherHits() throws IOException {
        do {
            addHitFromSource();
        } while (source.nextStartPosition() != Spans.NO_MORE_POSITIONS);
    }

}
