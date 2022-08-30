package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.lucene.payloads.PayloadSpanCollector;
import org.apache.lucene.search.spans.SpanCollector;

import nl.inl.blacklab.analysis.PayloadUtils;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.Span;

/**
 * Gets spans for a certain XML element.
 */
class SpansTags extends BLSpans {

    private final BLSpans tags;

    private int end = -1; // -1: not nexted yet. -2: payload not read yet.

    /** If true, we have to skip the primary value indicator in the payload (see PayloadUtils) */
    private boolean payloadIndicatesPrimaryValues;

    public SpansTags(BLSpans startTags) {
        this.tags = startTags;
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        this.payloadIndicatesPrimaryValues = context.index().needsPrimaryTokenPayloads();
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        // NOP
    }

    @Override
    public void getCapturedGroups(Span[] capturedGroups) {
        // NOP
    }

    @Override
    public int nextDoc() throws IOException {
        end = -1; // not nexted yet
        return tags.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        end = -1; // not nexted yet
        return tags.advance(target);
    }

    @Override
    public int docID() {
        return tags.docID();
    }

    @Override
    public int nextStartPosition() throws IOException {
        end = -2; // payload not read yet
        return tags.nextStartPosition();
    }

    @Override
    public int startPosition() {
        return tags.startPosition();
    }

    private final PayloadSpanCollector collector = new PayloadSpanCollector();

    @Override
    public int endPosition() {
        if (tags.startPosition() == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;
        try {
            if (end == -2) {
                collector.reset();

                // NOTE: tags is a BLSpanTermQuery, so we know there can only be one payload
                //   each start tag gets a payload, so there should always be one
                tags.collect(collector);
                byte[] payload = collector.getPayloads().iterator().next();
                ByteBuffer bb = ByteBuffer.wrap(payload);
                bb.position(PayloadUtils.getPrimaryValueIndicatorLength(payload)); // skip indicator
                end = bb.getInt();
            }
            return end;
        } catch (IOException e) {
            throw new BlackLabRuntimeException("Error getting payload");
        }

    }

    @Override
    public int width() {
        return tags.width();
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        tags.collect(collector);
    }

    @Override
    public float positionsCost() {
        return tags.positionsCost();
    }

}
