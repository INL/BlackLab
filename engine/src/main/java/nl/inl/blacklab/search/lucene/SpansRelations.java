package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.payloads.PayloadSpanCollector;
import org.apache.lucene.search.spans.FilterSpans;
import org.apache.lucene.store.ByteArrayDataInput;

import nl.inl.blacklab.analysis.PayloadUtils;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.lucene.SpanQueryRelations.Direction;

/**
 * Gets spans for relations matches.
 *
 * Relations are indexed in the _relation annotation, with information in the
 * payload to determine the source and target of the relation. The source and
 * target also define the span that is returned.
 */
class SpansRelations extends BLFilterSpans<BLSpans> {

    private final int NOT_YET_NEXTED = -1;

    /** Span end position (or NOT_YET_NEXTED) */
    private int endPos = NOT_YET_NEXTED;

    /** Source and target for this relation */
    private final RelationInfo relationInfo = new RelationInfo();

    /** If true, we have to skip the primary value indicator in the payload (see PayloadUtils) */
    private final boolean payloadIndicatesPrimaryValues;

    /** Filter to apply to the relations */
    private final Direction direction;

    /** What span to return for the relations found */
    private final RelationInfo.SpanMode spanMode;

    /** Group number where we'll capture our relation info */
    private int groupIndex;

    /** Relation type we're looking for */
    private final String relationType;

    /** Unique id for this SpansRelations (to avoid match info name collision) */
    private final int uniqueId;

    /** Name to capture the relation info as */
    private final String captureAs;

    /**
     * Construct SpansRelations.
     *
     * NOTE: relation payloads contain the location of the other side of the relation. To work with these,
     * we also need to know if there's "is primary value" indicators in (some of) the payloads,
     * so we can skip these. See {@link PayloadUtils}.
     *
     * @param relationType type of relation we're looking for
     * @param relationsMatches relation matches for us to decode
     * @param payloadIndicatesPrimaryValues whether or not there's "is primary value" indicators in the payloads
     * @param direction direction of the relation
     * @param spanMode what span to return for the relations found
     * @param uniqueId unique id for this SpansRelations (to avoid match info name collision)
     * @param captureAs name to capture the relation info as
     */
    public SpansRelations(String relationType, BLSpans relationsMatches,
            boolean payloadIndicatesPrimaryValues, Direction direction, RelationInfo.SpanMode spanMode,
            int uniqueId, String captureAs) {
        super(relationsMatches, SpanQueryRelations.createGuarantees(relationsMatches.guarantees(), direction, spanMode));
        this.relationType = relationType;
        this.payloadIndicatesPrimaryValues = payloadIndicatesPrimaryValues;
        this.direction = direction;
        this.spanMode = spanMode;
        this.uniqueId = uniqueId;
        this.captureAs = captureAs;
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        // Discard relation class (if specified), and only keep Unicode letters from relationType
        // Note that this can result in collisions if your query contains the same relation type multiple times;
        // if you run into this problem, explicitly capture the relation by a unique name.
        String groupName = captureAs.isEmpty() ?
                relationType.replaceAll("^.+::", "").replaceAll("[^\\p{L}]", "") :
                captureAs;
        if (groupName.isEmpty())
            groupName = "rel";

        // Register our group
        this.groupIndex = context.registerMatchInfo(groupName);
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        matchInfo[groupIndex] = this.relationInfo.copy();
    }

    @Override
    public boolean hasMatchInfo() {
        return true;
    }

    /** Return current relation info.
     *
     * @return current relation info object; don't store or modify this, use .copy() first!
     */
    public RelationInfo getRelationInfo() {
        return relationInfo;
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        startPos = endPos = NOT_YET_NEXTED;
        return super.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        startPos = endPos = NOT_YET_NEXTED;
        return super.advance(target);
    }

    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            return startPos;
        }
        while (true) {
            if (in.nextStartPosition() == NO_MORE_POSITIONS) {
                startPos = NO_MORE_POSITIONS;
                endPos = NO_MORE_POSITIONS;
                return startPos;
            }
            switch (accept(this)) {
            case YES:
                return startPos;
            case NO:
                continue;
            case NO_MORE_IN_CURRENT_DOC:
                startPos = endPos = NO_MORE_POSITIONS;
                return startPos;
            }
        }
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        assert target > startPosition();
        if (atFirstInCurrentDoc && startPos >= target) {
            // Our cached hit is the one we want.
            return nextStartPosition();
        }
        if (direction == Direction.FORWARD && spanMode == RelationInfo.SpanMode.FULL_SPAN || spanMode == RelationInfo.SpanMode.SOURCE) {
            // We know our spans will be in order, so we can use the more efficient advanceStartPosition()
            super.advanceStartPosition(target);
            if (startPos == NO_MORE_POSITIONS) {
                endPos = NO_MORE_POSITIONS;
                return NO_MORE_POSITIONS;
            }
            switch (accept(this)) {
            case YES:
                break;
            case NO:
                nextStartPosition();
            case NO_MORE_IN_CURRENT_DOC:
                startPos = endPos = NO_MORE_POSITIONS;
                return startPos;
            }
            return startPos;
        }
        // Our spans may not be in order; use the slower implementation
        return BLSpans.naiveAdvanceStartPosition(this, target);
    }

    @Override
    public int endPosition() {
        if (atFirstInCurrentDoc)
            return -1;
        return startPos == NO_MORE_POSITIONS ? NO_MORE_POSITIONS : endPos;
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        fetchRelationInfo(); // decode the payload
        if (relationInfo.isRoot() && spanMode == RelationInfo.SpanMode.SOURCE) {
            // Need source, but this has no source
            return FilterSpans.AcceptStatus.NO;
        }

        // See if it's the correct direction
        boolean acc;
        switch (direction) {
        case ROOT:
            acc = relationInfo.isRoot();
            break;
        case FORWARD:
            acc = relationInfo.getSourceStart() <= relationInfo.getTargetStart();
            break;
        case BACKWARD:
            acc = relationInfo.getSourceStart() >= relationInfo.getTargetStart();
            break;
        case BOTH_DIRECTIONS:
            acc = true;
            break;
        default:
            throw new IllegalArgumentException("Unknown filter: " + direction);
        }
        if (acc) {
            startPos = relationInfo.spanStart(spanMode);
            endPos = relationInfo.spanEnd(spanMode);
        }
        return acc ? FilterSpans.AcceptStatus.YES : FilterSpans.AcceptStatus.NO;
    }

    private void fetchRelationInfo() {
        try {
            // Fetch the payload
            collector.reset();
            // NOTE: relationsMatches is from a BLSpanTermQuery, a leaf, so we know there can only be one payload
            //   each relation gets a payload, so there should always be one
            in.collect(collector);
            byte[] payload = collector.getPayloads().iterator().next();
            ByteArrayDataInput dataInput = PayloadUtils.getDataInput(payload, payloadIndicatesPrimaryValues);
            relationInfo.deserialize(in.startPosition(), dataInput);
            if (collector.term != null) // can happen during testing...
                relationInfo.setIndexedTerm(collector.term.text());
        } catch (IOException e) {
            throw new BlackLabRuntimeException("Error getting payload");
        }
    }

    /** SpanCollector that collects both the the payload and term for the current match. */
    private static class PayloadAndTermCollector extends PayloadSpanCollector {
        public Term term;

        @Override
        public void collectLeaf(PostingsEnum postings, int position, Term term) throws IOException {
            this.term = term;
            super.collectLeaf(postings, position, term);
        }
    }

    private final PayloadAndTermCollector collector = new PayloadAndTermCollector();

    @Override
    public String toString() {
        return "REL(" + relationType + ", " + spanMode + (direction != Direction.BOTH_DIRECTIONS ? ", " + direction : "") + ")";
    }
}
