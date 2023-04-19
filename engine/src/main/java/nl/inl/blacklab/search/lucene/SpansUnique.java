package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.FilterSpans;
import org.apache.lucene.search.spans.Spans;

/**
 * Remove consecutive duplicate hits from a source spans.
 */
class SpansUnique extends BLFilterSpans {

    /** Document id for the previous match */
    int prevDoc = -1;

    /** Start point for the previous match */
    int prevStart = -1;

    /** End point for the previous match */
    int prevEnd = -1;

    /**
     * Construct SpansUnique.
     *
     * @param in (startpoint-sorted) Spans to make unique
     */
    public SpansUnique(BLSpans in) {
        super(in); // NOTE: must be startpoint sorted! (caller's responsibility)
    }

    @Override
    public String toString() {
        return "UNIQUE(" + in.toString() + ")";
    }

    @Override
    protected FilterSpans.AcceptStatus accept(Spans candidate) throws IOException {
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
