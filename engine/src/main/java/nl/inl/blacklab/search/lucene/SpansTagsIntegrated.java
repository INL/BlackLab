package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.payloads.PayloadSpanCollector;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.store.ByteArrayDataInput;

import nl.inl.blacklab.analysis.PayloadUtils;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.Span;

/**
 * Gets spans for a certain XML element.
 */
class SpansTagsIntegrated extends BLSpans {

    private final int END_NOT_YET_NEXTED = -1;

    private final int END_PAYLOAD_NOT_YET_READ = -2;

    /** Term query that found our relations */
    private final BLSpans tags;

    /** Span end position (or END_NOT_YET_NEXTED, or END_PAYLOAD_NOT_YET_READ) */
    private int end = END_NOT_YET_NEXTED;

    /** Source and target for this relation */
    private RelationInfo relationInfo = new RelationInfo();

    /** If true, we have to skip the primary value indicator in the payload (see PayloadUtils) */
    private boolean payloadIndicatesPrimaryValues;

    /**
     * Construct SpansTags.
     *
     * NOTE: start tag payloads contain the location of the end tag. To work with these,
     * we also need to know if there's "is primary value" indicators in (some of) the payloads,
     * so we can skip these. See {@link PayloadUtils}.
     *
     * @param startTags the positions of our start tags
     * @param payloadIndicatesPrimaryValues whether or not there's "is primary value" indicators in the payloads
     */
    public SpansTagsIntegrated(BLSpans startTags, boolean payloadIndicatesPrimaryValues) {
        this.tags = startTags;
        this.payloadIndicatesPrimaryValues = payloadIndicatesPrimaryValues;
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
        end = END_NOT_YET_NEXTED;
        return tags.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        end = END_NOT_YET_NEXTED;
        return tags.advance(target);
    }

    @Override
    public int docID() {
        return tags.docID();
    }

    @Override
    public int nextStartPosition() throws IOException {
        end = END_PAYLOAD_NOT_YET_READ;
        return tags.nextStartPosition();
    }

    @Override
    public int startPosition() {
        return tags.startPosition();
    }

    private final PayloadSpanCollector collector = new PayloadSpanCollector();

    /** Was the relationship indexed at the target instead of the source? */
    public static final byte REL_FLAG_INDEXED_AT_TARGET = 0x01;

    /** Is it a root relationship, that only has a target, no source? */
    public static final byte REL_FLAG_ONLY_HAS_TARGET = 0x02;

    @Override
    public int endPosition() {
        if (tags.startPosition() == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;
        try {
            if (end == END_PAYLOAD_NOT_YET_READ) {
                decodePayload();
            }
            return end;
        } catch (IOException e) {
            throw new BlackLabRuntimeException("Error getting payload");
        }
    }

    private void decodePayload() throws IOException {
        // Fetch the payload
        collector.reset();
        // NOTE: tags is from a BLSpanTermQuery, a leaf, so we know there can only be one payload
        //   each start tag gets a payload, so there should always be one
        tags.collect(collector);
        byte[] payload = collector.getPayloads().iterator().next();
        ByteArrayDataInput dataInput = PayloadUtils.getDataInput(payload, payloadIndicatesPrimaryValues);
        relationInfo.deserialize(startPosition(), dataInput);

        // Determine the end of the relation's span
        end = Math.max(relationInfo.getSourceEnd(), relationInfo.getTargetEnd());
    }

    public RelationInfo getRelationInfo() throws IOException {
        if (end == END_PAYLOAD_NOT_YET_READ)
            decodePayload();
        return relationInfo;
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
