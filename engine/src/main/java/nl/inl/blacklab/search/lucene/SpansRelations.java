package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.payloads.PayloadSpanCollector;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.store.ByteArrayDataInput;

import nl.inl.blacklab.analysis.PayloadUtils;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.lucene.SpanQueryRelations.Direction;

/**
 * Gets spans for relations matches.
 *
 * Relations are indexed in the _relation annotation, with information in the
 * payload to determine the source and target of the relation. The source and
 * target also define the span that is returned.
 */
class SpansRelations extends BLSpans {

    private final int NOT_YET_NEXTED = -1;

    /** Term query that found our relations */
    private final BLSpans relationsMatches;

    /** Span end position (or NOT_YET_NEXTED) */
    private int start = NOT_YET_NEXTED;

    /** Span end position (or NOT_YET_NEXTED) */
    private int end = NOT_YET_NEXTED;

    /** Source and target for this relation */
    private final RelationInfo relationInfo = new RelationInfo();

    /** If true, we have to skip the primary value indicator in the payload (see PayloadUtils) */
    private final boolean payloadIndicatesPrimaryValues;

    /** Filter to apply to the relations */
    private final Direction direction;

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
    public SpansRelations(BLSpans relationsMatches, boolean payloadIndicatesPrimaryValues, Direction direction) {
        this.relationsMatches = relationsMatches;
        this.payloadIndicatesPrimaryValues = payloadIndicatesPrimaryValues;
        this.direction = direction;
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
        start = end = NOT_YET_NEXTED;
        return relationsMatches.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        start = end = NOT_YET_NEXTED;
        return relationsMatches.advance(target);
    }

    @Override
    public int docID() {
        return relationsMatches.docID();
    }

    @Override
    public int nextStartPosition() throws IOException {
        do {
            start = relationsMatches.nextStartPosition();
            if (start == NO_MORE_POSITIONS) {
                end = NO_MORE_POSITIONS;
                break;
            }
            fetchRelationInfo();
            end = relationInfo.getFullSpanEnd();
        } while (!matchesFilter());
        return start;
    }

    private boolean matchesFilter() {
        switch (direction) {
        case ROOT:
            return getRelationInfo().isRoot();
        case FORWARD:
            return getRelationInfo().getSourceStart() <= getRelationInfo().getTargetStart();
        case BACKWARD:
            return getRelationInfo().getSourceStart() >= getRelationInfo().getTargetStart();
        case BOTH_DIRECTIONS:
            return true;
        default:
            throw new IllegalArgumentException("Unknown filter: " + direction);
        }
    }

    @Override
    public int startPosition() {
        return start;
    }

    private final PayloadSpanCollector collector = new PayloadSpanCollector();

    @Override
    public int endPosition() {
        if (start == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;
        return end;
    }

    private void fetchRelationInfo() {
        try {
            // Fetch the payload
            collector.reset();
            // NOTE: relationsMatches is from a BLSpanTermQuery, a leaf, so we know there can only be one payload
            //   each relation gets a payload, so there should always be one
            relationsMatches.collect(collector);
            byte[] payload = collector.getPayloads().iterator().next();
            ByteArrayDataInput dataInput = PayloadUtils.getDataInput(payload, payloadIndicatesPrimaryValues);
            relationInfo.deserialize(startPosition(), dataInput);
        } catch (IOException e) {
            throw new BlackLabRuntimeException("Error getting payload");
        }
    }

    public RelationInfo getRelationInfo() {
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
