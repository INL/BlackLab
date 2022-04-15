package nl.inl.blacklab.search.lucene;

import java.io.IOException;

/**
 * Wrap a Spans to retrieve hits per document, so we can process all matches in
 * a document efficiently.
 *
 * Hits are sorted by the given comparator.
 */
class SpansInBucketsPerDocumentSorted extends SpansInBucketsPerDocument {
    private boolean sortByStartPoint;

    public SpansInBucketsPerDocumentSorted(BLSpans source, boolean sortByStartPoint) {
        super(source);
        this.sortByStartPoint = sortByStartPoint;
    }

    @Override
    protected void gatherHits() throws IOException {
        super.gatherHits();
        sortHits(sortByStartPoint);
    }

}
