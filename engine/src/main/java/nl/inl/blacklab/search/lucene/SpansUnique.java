package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.FilterSpans;

/**
 * Remove consecutive duplicate hits from a source spans.
 */
class SpansUnique extends BLFilterSpans<BLSpans> {

    /** Document id for the previous match */
    int prevDoc = -1;

    /** Start point for the previous match */
    int prevStart = -1;

    /** End point for the previous match */
    int prevEnd = -1;

    /**
     * Construct SpansUnique.
     *
     * @param in (start- or endpoint-sorted) Spans to make unique
     */
    public SpansUnique(BLSpans in) {
        super(in);
        // Validate clause guarantees
        if (! (in.guarantees().hitsStartPointSorted() || in.guarantees().hitsEndPointSorted()))
            throw new IllegalArgumentException("Clause must be either start- or endpoint sorted");
        if (in.guarantees().hitsHaveUniqueStartEnd())
            throw new IllegalArgumentException("Clause already has unique hits");
    }

    @Override
    public String toString() {
        return "UNIQUE(" + in + ")";
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        if (candidate.docID() == prevDoc && candidate.startPosition() == prevStart && candidate.endPosition() == prevEnd) {
            // Not unique doc/start/end (match filter may be unique, we don't know yet - so careful with this class!)
            return FilterSpans.AcceptStatus.NO;
        }
        // Unique hit. Remember for next time.
        prevDoc = candidate.docID();
        prevStart = candidate.startPosition();
        prevEnd = candidate.endPosition();
        return FilterSpans.AcceptStatus.YES;
    }
}
