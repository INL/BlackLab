package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.search.spans.TermSpans;

/**
 * Wrap a "simple" Spans object in a BLSpans object. It will give the guarantees
 * appropriate for single-term Spans like that of SpanTermQuery, SpanRegexQuery,
 * etc.
 */
public class BLSpansWrapper extends BLSpans {

    private final Spans source;

    public BLSpansWrapper(Spans source) {
        if (source == null)
            throw new IllegalArgumentException("Cannot wrap null Spans!");

        if (source instanceof BLSpans) {
            throw new IllegalArgumentException("No need to wrap spans, already a BLSpans");
        }

        if (!(source instanceof TermSpans)) {
            // For anything but the very basic TermSpans,
            // this wrapper shouldn't be used anymore because everything is already BLSpans.
            // (which is needed for token tagging)
            // Just to make sure, print an error for now
            // TODO upgrade to throwing an exception in the future)
            System.err.println("### BLSpansWrapper: " + source + " ###");
        }

        this.source = source;
    }

    @Override
    public boolean equals(Object obj) {
        return source.equals(obj);
    }

    @Override
    public int hashCode() {
        return source.hashCode();
    }

    @Override
    public String toString() {
        return source.toString();
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        if (source instanceof BLSpans) // shouldn't happen, but ok
            ((BLSpans) source).setHitQueryContext(context);
    }

    @Override
    public void getMatchInfo(MatchInfo[] relationInfo) {
        if (!childClausesCaptureGroups)
            return;
        if (source instanceof BLSpans) // shouldn't happen, but ok
            ((BLSpans) source).getMatchInfo(relationInfo);
    }

    @Override
    public int nextDoc() throws IOException {
        return source.nextDoc();
    }

    @Override
    public int docID() {
        return source.docID();
    }

    @Override
    public int nextStartPosition() throws IOException {
        return source.nextStartPosition();
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        if (source instanceof BLSpans) {
            return ((BLSpans) source).advanceStartPosition(target);
        }
        // Naive implementations; subclasses may provide a faster version.
        int pos;
        do {
            pos = source.nextStartPosition();
        } while (pos < target && pos != NO_MORE_POSITIONS);
        return pos;
    }

    @Override
    public int startPosition() {
        return source.startPosition();
    }

    @Override
    public int endPosition() {
        return source.endPosition();
    }

    @Override
    public int advance(int target) throws IOException {
        return source.advance(target);
    }

    @Override
    public int width() {
        return source.width();
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        source.collect(collector);
    }

    @Override
    public float positionsCost() {
        return source.positionsCost();
    }

}
