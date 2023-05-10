package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.Spans;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

/**
 * Gather hits from a Spans object in "buckets" by the start point of the hits.
 * Allow us to retrieve all hits that start at a certain point.
 * <p>
 * The reason we don't use SpansInBucketsAbstract here is that it's more
 * efficient to just save the endpoints for the current start point (the source
 * spans is normally startpoint-sorted already).
 */
class SpansInBucketsPerStartPoint extends SpansInBuckets {
    /** Source spans we're collecting matches from */
    protected final BLSpans source;

    /** ID of the current document */
    protected int currentDoc = -1;

    /** start position for current bucket */
    protected int currentBucketStart = -1;

    /** source.startPosition() */
    protected int currentSpansStart = -1;

    /** end positions for current bucket */
    private final MutableIntList endPoints = new IntArrayList(LIST_INITIAL_CAPACITY);

    /** match infos for current bucket */
    private final List<MatchInfo[]> matchInfos = new ArrayList<>(LIST_INITIAL_CAPACITY);

    private HitQueryContext hitQueryContext;

    /** Do we have a hitQueryContext and does it have match info captures? */
    private boolean doMatchInfo = true;

    /** Does our clause capture any match info? If not, we don't need to mess with those */
    protected boolean clauseCapturesMatchInfo = true;

    /**
     * Construct SpansInBucketsPerStartPoint.
     *
     * @param source (startpoint-sorted) source spans
     */
    public SpansInBucketsPerStartPoint(BLSpans source) {
        this.source = Objects.requireNonNull(source);
    }

    @Override
    public int docID() {
        return currentDoc;
    }

    @Override
    public int nextDoc() throws IOException {
        if (currentDoc != NO_MORE_DOCS) {
            currentDoc = source.nextDoc();
            if (currentDoc != NO_MORE_DOCS)
                currentSpansStart = source.nextStartPosition();
            else
                currentSpansStart = Spans.NO_MORE_POSITIONS;
            currentBucketStart = -1; // no bucket yet
        }
        return currentDoc;
    }

    @Override
    public int nextBucket() throws IOException {
        if (currentDoc < 0) {
            // Not nexted yet, no bucket
            return -1;
        }
        if (currentSpansStart == Spans.NO_MORE_POSITIONS)
            return NO_MORE_BUCKETS;

        return gatherEndPointsAtStartPoint();
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
        if (currentSpansStart >= targetPos)
            return nextBucket();
        currentSpansStart = source.advanceStartPosition(targetPos);
        if (currentSpansStart == Spans.NO_MORE_POSITIONS)
            return NO_MORE_BUCKETS;
        return gatherEndPointsAtStartPoint();
    }

    protected int gatherEndPointsAtStartPoint() throws IOException {
        endPoints.clear();
        matchInfos.clear();
        doMatchInfo = clauseCapturesMatchInfo && hitQueryContext != null
                && hitQueryContext.numberOfMatchInfos() > 0;

        currentBucketStart = currentSpansStart;
        while (currentSpansStart != Spans.NO_MORE_POSITIONS && currentSpansStart == currentBucketStart) {
            endPoints.add(source.endPosition());
            if (doMatchInfo) {
                MatchInfo[] capturedGroups = new MatchInfo[hitQueryContext.numberOfMatchInfos()];
                source.getMatchInfo(capturedGroups);
                matchInfos.add(capturedGroups);
            }
            currentSpansStart = source.nextStartPosition();
        }
        return currentDoc;
    }

    @Override
    public int advance(int target) throws IOException {
        if (currentDoc >= target) {
            return nextDoc();
        }
        currentDoc = source.advance(target);
        if (currentDoc != NO_MORE_DOCS)
            currentSpansStart = source.nextStartPosition();
        else
            currentSpansStart = Spans.NO_MORE_POSITIONS;
        currentBucketStart = -1; // no bucket yet
        return currentDoc;
    }

    @Override
    public String toString() {
        return source.toString();
    }

    @Override
    public int bucketSize() {
        return endPoints.size();
    }

    @Override
    public int startPosition(int indexInBucket) {
        return currentBucketStart;
    }

    @Override
    public int endPosition(int indexInBucket) {
        return endPoints.get(indexInBucket);
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        this.hitQueryContext = context;
        int before = context.getMatchInfoRegisterNumber();
        source.setHitQueryContext(context);
        if (context.getMatchInfoRegisterNumber() == before) {
            // Our clause doesn't capture any groups; optimize
            clauseCapturesMatchInfo = false;
        }
    }

    @Override
    public void getMatchInfo(int indexInBucket, MatchInfo[] matchInfo) {
        if (!doMatchInfo || matchInfos.isEmpty())
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
    public long cost() {
        return source.cost();
    }



    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        return getTwoPhaseIterator(source);
    }

    @Override
    public float positionsCost() {
        return source.positionsCost();
    }

    @Override
    public int width() {
        return source.width();
    }
}
