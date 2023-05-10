package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.Spans;

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
    protected int currentBucketStart = -1;

    /** Current end position */
    protected int currentBucketEnd = -1;

    /** Match infos for this start and end position */
    private final List<MatchInfo[]> matchInfos = new ArrayList<>(LIST_INITIAL_CAPACITY);

    private HitQueryContext hitQueryContext;

    /**
     * Construct SpansInBucketsPerStartPoint.
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
        currentBucketStart = -1; // no bucket yet
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
        matchInfos.clear();
        currentBucketStart = source.startPosition();
        currentBucketEnd = source.endPosition();
        int sourceStart = currentBucketStart;
        while (sourceStart != Spans.NO_MORE_POSITIONS && sourceStart == currentBucketStart &&
                source.endPosition() == currentBucketEnd) {
            MatchInfo[] matchInfo = new MatchInfo[hitQueryContext.numberOfMatchInfos()];
            source.getMatchInfo(matchInfo);
            matchInfos.add(matchInfo);
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
        currentBucketStart = -1; // no bucket yet
        return docId;
    }

    @Override
    public String toString() {
        return source.toString();
    }

    @Override
    public int bucketSize() {
        return matchInfos.size();
    }

    @Override
    public int startPosition(int indexInBucket) {
        return currentBucketStart;
    }

    @Override
    public int endPosition(int indexInBucket) {
        return currentBucketEnd;
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        this.hitQueryContext = context;
        source.setHitQueryContext(context);
    }

    @Override
    public void getMatchInfo(int indexInBucket, MatchInfo[] matchInfo) {
        if (matchInfos.isEmpty())
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
