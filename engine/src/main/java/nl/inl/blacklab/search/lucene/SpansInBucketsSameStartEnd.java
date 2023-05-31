package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Objects;

import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.Spans;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Gather buckets where all hits have the same start and end position.
 * <p>
 * Only makes sense if there's match info that may be different between the
 * otherwise identical matches; if there's no match info, you should just make
 * sure you eliminate any duplicate matches.
 */
class SpansInBucketsSameStartEnd extends SpansInBuckets {
    protected final BLSpans source;

    /** Current start position */
    protected int currentStartPosition = -1;

    /** Current end position */
    protected int currentEndPosition = -1;

    /** How many hits with this start and end? */
    protected int currentBucketSize = -1;

    /**
     * For each hit we fetched, store the match info (e.g. captured groups, relations),
     * so we don't lose this information.
     */
    private ObjectArrayList<MatchInfo[]> matchInfos = null;

    /**
     * For each hit we fetched, store the active relation info, if any.
     */
    protected ObjectArrayList<RelationInfo> activeRelationPerHit = null;

    private HitQueryContext hitQueryContext;

    /** Is there match info (e.g. captured groups) for each hit that we need to store? */
    private boolean doMatchInfo;

    /**
     * Does our clause capture any match info? If not, we don't need to mess with those
     */
    private boolean clauseCapturesMatchInfo = true;

    /**
     * Construct SpansInBucketsSameStartEnd.
     *
     * @param source (startpoint-sorted) source spans
     */
    public SpansInBucketsSameStartEnd(BLSpans source) {
        this.source = Objects.requireNonNull(source);
    }

    @Override
    public int docID() {
        return source.docID();
    }

    @Override
    public int nextDoc() throws IOException {
        int docId = source.nextDoc();
        if (docId != NO_MORE_DOCS)
            source.nextStartPosition();
        currentStartPosition = -1; // no bucket yet
        return docId;
    }

    @Override
    public int nextBucket() throws IOException {
        assert source.docID() >= 0;
        if (source.startPosition() == Spans.NO_MORE_POSITIONS)
            return NO_MORE_BUCKETS;
        return gatherHitsWithSameStartEnd();
    }

    /**
     * Go to the next bucket at or beyond the specified start point.
     * <p>
     * Always at least advances to the next bucket, even if we were already at or
     * beyond the specified target.
     *
     * @param targetPos the target start point
     * @return docID if we're at a valid bucket, or NO_MORE_BUCKETS if we're done.
     */
    public int advanceBucket(int targetPos) throws IOException {
        assert source.docID() >= 0;
        if (source.startPosition() >= targetPos)
            return nextBucket();
        if (source.advanceStartPosition(targetPos) == Spans.NO_MORE_POSITIONS)
            return NO_MORE_BUCKETS;
        return gatherHitsWithSameStartEnd();
    }

    /**
     * Gather matchinfos for all hits with the same start and end position as the current one.
     *
     * @return current doc id
     */
    protected int gatherHitsWithSameStartEnd() throws IOException {
        if (doMatchInfo) {
            matchInfos.clear();
            activeRelationPerHit.clear();
        }
        doMatchInfo = clauseCapturesMatchInfo && hitQueryContext != null && hitQueryContext.numberOfMatchInfos() > 0;
        if (doMatchInfo && matchInfos == null) {
            matchInfos = new ObjectArrayList<>(LIST_INITIAL_CAPACITY);
            activeRelationPerHit = new ObjectArrayList<>(LIST_INITIAL_CAPACITY);
        }
        currentStartPosition = source.startPosition();
        currentEndPosition = source.endPosition();
        currentBucketSize = 0;
        int sourceStart = currentStartPosition;
        while (sourceStart != Spans.NO_MORE_POSITIONS && sourceStart == currentStartPosition &&
                source.endPosition() == currentEndPosition) {
            if (doMatchInfo) {
                int n = hitQueryContext == null ? 0 : hitQueryContext.numberOfMatchInfos();
                MatchInfo[] matchInfo = new MatchInfo[n];
                source.getMatchInfo(matchInfo);
                matchInfos.add(matchInfo);
                RelationInfo relationInfo = source.getRelationInfo();
                activeRelationPerHit.add(relationInfo == null ? null  : relationInfo.clone());
            }
            currentBucketSize++;
            sourceStart = source.nextStartPosition();
        }
        return source.docID();
    }

    @Override
    public int advance(int target) throws IOException {
        if (source.docID() >= target) {
            return nextDoc();
        }
        int docId = source.advance(target);
        if (docId != NO_MORE_DOCS)
            source.nextStartPosition();
        currentStartPosition = -1; // no bucket yet
        return docId;
    }

    @Override
    public String toString() {
        return source.toString();
    }

    @Override
    public int bucketSize() {
        return currentBucketSize;
    }

    @Override
    public int startPosition(int indexInBucket) {
        return currentStartPosition;
    }

    @Override
    public int endPosition(int indexInBucket) {
        return currentEndPosition;
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        clauseCapturesMatchInfo = hasMatchInfo();
        this.hitQueryContext = context;
        source.setHitQueryContext(context);
    }

    @Override
    public void getMatchInfo(int indexInBucket, MatchInfo[] matchInfo) {
        if (!doMatchInfo)
            return;
        MatchInfo[] thisMatchInfo = matchInfos.get(indexInBucket);
        if (thisMatchInfo != null) {
            for (int i = 0; i < matchInfo.length; i++) {
                if (thisMatchInfo[i] != null) // don't overwrite other clause's captures!
                    matchInfo[i] = thisMatchInfo[i];
            }
        }
    }

    @Override
    public boolean hasMatchInfo() {
        return source.hasMatchInfo();
    }

    @Override
    public RelationInfo getRelationInfo(int indexInBucket) {
        return doMatchInfo ? activeRelationPerHit.get(indexInBucket) : null;
    }

    @Override
    public SpanGuarantees guarantees() {
        return source.guarantees();
    }

    @Override
    public long cost() {
        return source.cost();
    }

    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        return getTwoPhaseIterator(source);
    }

    @Override
    public float positionsCost() {
        throw new UnsupportedOperationException(); // asTwoPhaseIterator never returns null here.
    }

    @Override
    public int width() {
        return source.width();
    }
}
