package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.Spans;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

/**
 * Gather hits from a Spans object in "buckets" by the start point of the hits.
 * Allow us to retrieve all hits that start at a certain point.
 *
 * The reason we don't use SpansInBucketsAbstract here is that it's more
 * efficient to just save the endpoints for the current start point (the source
 * spans is normally startpoint-sorted already).
 */
class SpansInBucketsPerStartPoint extends DocIdSetIterator implements SpansInBuckets {
    protected final BLSpans source;

    protected int currentDoc = -1;

    protected int currentBucketStart = -1;

    protected int currentSpansStart = -1;

    private IntArrayList endPoints = new IntArrayList(LIST_INITIAL_CAPACITY);

    private List<MatchInfo[]> capturedGroupsPerEndpoint = new ArrayList<>(LIST_INITIAL_CAPACITY);

    private int bucketSize = 0;

    private HitQueryContext hitQueryContext;

    /** Do we have a hitQueryContext and does it contain captured groups? */
    private boolean doCapturedGroups = true;

    /**
     * Does our clause capture any groups? If not, we don't need to mess with those
     */
    protected boolean clauseCapturesGroups = true;

    /**
     * Construct SpansInBucketsPerStartPoint.
     *
     * @param source (startpoint-sorted) source spans
     */
    public SpansInBucketsPerStartPoint(BLSpans source) {
        this.source = source; //Sort
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
     *
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

    @SuppressWarnings("unused")
    protected int gatherEndPointsAtStartPoint() throws IOException {
        if (!REALLOCATE_IF_TOO_LARGE || endPoints.size() < COLLECTION_REALLOC_THRESHOLD) {
            // Not a huge amount of memory, so don't reallocate
            endPoints.clear();
            capturedGroupsPerEndpoint.clear();
        } else {
            // Reallocate in this case to avoid holding on to a lot of memory
            endPoints = new IntArrayList(LIST_INITIAL_CAPACITY);
            capturedGroupsPerEndpoint = new ArrayList<>(LIST_INITIAL_CAPACITY);
        }

        doCapturedGroups = clauseCapturesGroups && source != null && hitQueryContext != null
                && hitQueryContext.numberOfMatchInfos() > 0;

        bucketSize = 0;
        currentBucketStart = currentSpansStart;
        while (currentSpansStart != Spans.NO_MORE_POSITIONS && currentSpansStart == currentBucketStart) {
            endPoints.add(source.endPosition());
            if (doCapturedGroups) {
                MatchInfo[] capturedGroups = new MatchInfo[hitQueryContext.numberOfMatchInfos()];
                source.getMatchInfo(capturedGroups);
                capturedGroupsPerEndpoint.add(capturedGroups);
            }
            bucketSize++;
            currentSpansStart = source.nextStartPosition();
        }
        return currentDoc;
    }

    @Override
    public int advance(int target) throws IOException {
        if (currentDoc >= target) {
            return nextDoc();
        }

        if (currentDoc == NO_MORE_DOCS)
            return DocIdSetIterator.NO_MORE_DOCS;

        if (currentDoc < target) {
            currentDoc = source.advance(target);
            currentSpansStart = source.nextStartPosition();
            currentBucketStart = -1; // no bucket yet
        }

        return currentDoc;
    }

    @Override
    public String toString() {
        return source.toString();
    }

    @Override
    public int bucketSize() {
        return bucketSize;
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
            clauseCapturesGroups = false;
        }
    }

    @Override
    public void getMatchInfo(int indexInBucket, MatchInfo[] matchInfo) {
        if (!doCapturedGroups || capturedGroupsPerEndpoint.isEmpty())
            return;
        MatchInfo[] previouslyCapturedGroups = capturedGroupsPerEndpoint.get(indexInBucket);
        if (previouslyCapturedGroups != null) {
            for (int i = 0; i < matchInfo.length; i++) {
                if (previouslyCapturedGroups[i] != null)
                    matchInfo[i] = previouslyCapturedGroups[i];
            }
        }
    }

    @Override
    public long cost() {
        return source.cost();
    }

    @Override
    public float positionsCost() {
        return source.positionsCost();
    }
}
