package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.FilterSpans;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.search.spans.TermSpans;

/**
 * Wrap a "simple" Spans object in a BLSpans object.
 */
public final class BLSpansWrapper extends BLSpansFilter {

    public BLSpansWrapper(Spans source) {
        super(source);
        if (source instanceof BLSpans) {
            throw new IllegalArgumentException("No need to wrap spans, already a BLSpans");
        }

        if (!(source instanceof TermSpans)) {
            // For anything but the very basic TermSpans,
            // this wrapper shouldn't be used anymore because everything is already BLSpans.
            // (which is needed for captures and relations)
            throw new IllegalArgumentException("Don't use BLSpansWrapper for anything but TermSpans");
        }
    }

    @Override
    public String toString() {
        return "WRAP(" + in + ")";
    }

    /**
     * Returns YES if the candidate should be an accepted match, NO if it should not, and
     * NO_MORE_IN_CURRENT_DOC if iteration should move on to the next document.
     */
    protected FilterSpans.AcceptStatus accept(Spans candidate) throws IOException {
        return FilterSpans.AcceptStatus.YES;
    }

}
