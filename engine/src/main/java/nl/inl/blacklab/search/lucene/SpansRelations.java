package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.payloads.PayloadSpanCollector;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.store.ByteArrayDataInput;

import nl.inl.blacklab.analysis.PayloadUtils;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.Span;

/**
 * Gets spans for relations matches.
 *
 * Relations are indexed in the _relation annotation, with information in the
 * payload to determine the source and target of the relation. The source and
 * target also define the span that is returned.
 */
class SpansRelations extends BLSpans {

    private final int END_NOT_YET_NEXTED = -1;

    private final int END_PAYLOAD_NOT_YET_READ = -2;

    /** Term query that found our relations */
    private final BLSpans relationsMatches;

    /** Span end position (or END_NOT_YET_NEXTED, or END_PAYLOAD_NOT_YET_READ) */
    private int end = END_NOT_YET_NEXTED;

    /** Source and target for this relation */
    private RelationInfo relationInfo = new RelationInfo();

    /** If true, we have to skip the primary value indicator in the payload (see PayloadUtils) */
    private boolean payloadIndicatesPrimaryValues;

    /** Filter to apply to the relations */
    private SpanQueryRelations.Filter filter;

    /**
     * Construct SpansRelations.
     *
     * NOTE: relation payloads contain the location of the other side of the relation. To work with these,
     * we also need to know if there's "is primary value" indicators in (some of) the payloads,
     * so we can skip these. See {@link PayloadUtils}.
     *
     * @param relationsMatches relation matches for us to decode
     * @param payloadIndicatesPrimaryValues whether or not there's "is primary value" indicators in the payloads
     */
    public SpansRelations(BLSpans relationsMatches, boolean payloadIndicatesPrimaryValues, SpanQueryRelations.Filter filter) {
        this.relationsMatches = relationsMatches;
        this.payloadIndicatesPrimaryValues = payloadIndicatesPrimaryValues;
        this.filter = filter;
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
        return relationsMatches.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        end = END_NOT_YET_NEXTED;
        return relationsMatches.advance(target);
    }

    @Override
    public int docID() {
        return relationsMatches.docID();
    }

    @Override
    public int nextStartPosition() throws IOException {
        end = END_PAYLOAD_NOT_YET_READ;
        int startPosition;
        do {
            startPosition = relationsMatches.nextStartPosition();
            if (startPosition == NO_MORE_POSITIONS)
                break;
        } while (!matchesFilter());
        return startPosition;
    }

    private boolean matchesFilter() throws IOException {
        switch (filter) {
        case ROOT:
            return getRelationInfo().isRoot();
        case FORWARD:
            return getRelationInfo().getSourceStart() <= getRelationInfo().getTargetStart();
        case BACKWARD:
            return getRelationInfo().getSourceStart() >= getRelationInfo().getTargetStart();
        case BOTH_DIRECTIONS:
            return true;
        default:
            throw new IllegalArgumentException("Unknown filter: " + filter);
        }
    }

    @Override
    public int startPosition() {
        return relationsMatches.startPosition();
    }

    private final PayloadSpanCollector collector = new PayloadSpanCollector();

    @Override
    public int endPosition() {
        if (relationsMatches.startPosition() == NO_MORE_POSITIONS)
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
        // NOTE: relationsMatches is from a BLSpanTermQuery, a leaf, so we know there can only be one payload
        //   each relation gets a payload, so there should always be one
        relationsMatches.collect(collector);
        byte[] payload = collector.getPayloads().iterator().next();
        ByteArrayDataInput dataInput = PayloadUtils.getDataInput(payload, payloadIndicatesPrimaryValues);
        relationInfo.deserialize(startPosition(), dataInput);
        end = relationInfo.getSpanEnd();
    }

    public RelationInfo getRelationInfo() throws IOException {
        if (end == END_PAYLOAD_NOT_YET_READ)
            decodePayload();
        return relationInfo;
    }

    @Override
    public int width() {
        return relationsMatches.width();
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        relationsMatches.collect(collector);
    }

    @Override
    public float positionsCost() {
        return relationsMatches.positionsCost();
    }

}
