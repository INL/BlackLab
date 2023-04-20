package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.spans.FilterSpans;

/**
 * Apply a document Filter to a Spans.
 *
 * This allows us to only consider certain documents (say, only documents in a
 * certain domain) when executing our query.
 */
class SpansFiltered extends BLFilterSpans<BLSpans> {

    /** Keep hits from these documents. */
    private final DocIdSetIterator acceptedDocs;

    public SpansFiltered(BLSpans spans, Scorer filterDocs) throws IOException {
        this(spans, filterDocs == null ? null : filterDocs.iterator());
    }

    public SpansFiltered(BLSpans spans, DocIdSetIterator acceptedDocs) throws IOException {
        super(spans);
        this.acceptedDocs = acceptedDocs;
        if (acceptedDocs != null)
            acceptedDocs.nextDoc(); // position at first doc, if any
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        if (acceptedDocs == null || acceptedDocs.docID() == NO_MORE_DOCS)
            return FilterSpans.AcceptStatus.NO_MORE_IN_CURRENT_DOC;
        int docId = candidate.docID();
        while (docId > acceptedDocs.docID()) {
            if (acceptedDocs.advance(docId) == NO_MORE_DOCS) {
                return FilterSpans.AcceptStatus.NO_MORE_IN_CURRENT_DOC;
            }
        }
        return acceptedDocs.docID() == docId ? FilterSpans.AcceptStatus.YES : FilterSpans.AcceptStatus.NO;
    }

    @Override
    public String toString() {
        return "DOCFILTER(" + in + ", " + acceptedDocs + ")";
    }
}
