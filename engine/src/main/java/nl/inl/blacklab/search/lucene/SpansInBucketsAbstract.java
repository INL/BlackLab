package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.Spans;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Wrap a Spans to retrieve sequences of certain matches (in "buckets"), so we
 * can process the sequence efficiently.
 *
 * Examples of sequences of hits might be: * all hits in a document * all
 * consecutive hits in a document
 *
 * This way we can retrieve hits and perform some operation on them (like
 * sorting or retrieving some extra information).
 *
 * Note that with this class, "bucketing" is only possible with consecutive hits
 * from the Spans object. If you want other kinds of hit buckets (containing
 * non-consecutive spans), you should just implement the SpansInBuckets
 * interface, not extend SpansInBucketsAbstract.
 *
 * Also, SpansInBuckets assumes all hits in a bucket are from a single document.
 *
 */
abstract class SpansInBucketsAbstract extends SpansInBuckets {
    
    protected final BLSpans source;

    /** Starts and ends of hits in our bucket */
    protected final LongList startsEnds = new LongArrayList(LIST_INITIAL_CAPACITY);

    /**
     * For each hit we fetched, store the match info (e.g. captured groups, relations),
     * so we don't lose this information.
     */
    protected ObjectArrayList<MatchInfo[]> matchInfoPerHit = null;

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
    protected boolean clauseCapturesMatchInfo = true;

    /**
     * Assert that, if our clause is positioned at a doc, nextStartPosition() has also been called.
     *
     * Sanity check to be called from assertions at the start and end of methods that change the internal state.
     *
     * We require this because nextBucket() expects the clause to be positioned at a hit. This is because
     * for certain bucketing operations we can only decide we're done with a bucket if we're at the first hit
     * that doesn't belong in the bucket.
     *
     * @return true if positioned at a hit (or at a doc and nextStartPosition() has been called), false if not
     */
    private boolean positionedAtHitIfPositionedInDoc() {
        return source.docID() < 0 || source.docID() == NO_MORE_DOCS || source.startPosition() >= 0;
    }

    protected void addHitFromSource() {
        assert positionedAtHitIfPositionedInDoc();
        assert source.startPosition() >= 0 && source.startPosition() != Spans.NO_MORE_POSITIONS;
        assert source.endPosition() >= 0 && source.endPosition() != Spans.NO_MORE_POSITIONS;
        long span = ((long)source.startPosition() << 32) | source.endPosition();
        startsEnds.add(span);
        if (doMatchInfo) {
            // Store match information such as captured groups and active relation (if any)
            MatchInfo[] matchInfo = new MatchInfo[hitQueryContext.numberOfMatchInfos()];
            source.getMatchInfo(matchInfo);
            if (matchInfoPerHit == null)
                matchInfoPerHit = new ObjectArrayList<>(LIST_INITIAL_CAPACITY);
            matchInfoPerHit.add(matchInfo);
            if (activeRelationPerHit == null)
                activeRelationPerHit = new ObjectArrayList<>(LIST_INITIAL_CAPACITY);
            RelationInfo relationInfo = source.getRelationInfo();
            activeRelationPerHit.add(relationInfo == null ? null : relationInfo.copy());
        }
        assert positionedAtHitIfPositionedInDoc();
    }

    @Override
    public int bucketSize() {
        return startsEnds.size();
    }

    @Override
    public int startPosition(int indexInBucket) {
        //return bucketSlow.get(indexInBucket).start();
        return (int)(startsEnds.getLong(indexInBucket) >> 32);
    }

    @Override
    public int endPosition(int indexInBucket) {
        //return bucketSlow.get(indexInBucket).end();
        return (int) startsEnds.getLong(indexInBucket);
    }

    public SpansInBucketsAbstract(BLSpans source) {
        this.source = source;
    }

    @Override
    public int nextDoc() throws IOException {
        assert positionedAtHitIfPositionedInDoc();
        if (source.docID() != DocIdSetIterator.NO_MORE_DOCS) {
            if (source.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                source.nextStartPosition(); // start gathering at the first hit
            }
        }
        assert positionedAtHitIfPositionedInDoc();
        assert source.docID() == DocIdSetIterator.NO_MORE_DOCS || (source.startPosition() >= 0 && source.startPosition() != Spans.NO_MORE_POSITIONS);;
        return source.docID();
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        assert positionedAtHitIfPositionedInDoc();
        int doc = source.docID();
        if (doc != DocIdSetIterator.NO_MORE_DOCS) {
            if (doc >= target) {
                // Already at or beyond; go to the next doc
                // (though we shouldn't rely on this behavior)
                doc = nextDoc();
            } else {
                doc = source.advance(target);
                if (doc != DocIdSetIterator.NO_MORE_DOCS) {
                    int startPos = source.nextStartPosition(); // start gathering at the first hit
                    assert startPos >= 0 && source.startPosition() >= 0;
                }
            }
        }
        assert source.docID() == DocIdSetIterator.NO_MORE_DOCS || (source.startPosition() >= 0 && source.startPosition() != Spans.NO_MORE_POSITIONS);;
        assert doc >= 0 && doc >= target;
        assert positionedAtHitIfPositionedInDoc();
        return doc;
    }

    @Override
    public int nextBucket() throws IOException {
        assert positionedAtHitIfPositionedInDoc();
        assert source.docID() >= 0;
        if (source.docID() < 0) {
            // Not nexted yet, no bucket
            return -1;
        }
        if (source.docID() == DocIdSetIterator.NO_MORE_DOCS || source.startPosition() == Spans.NO_MORE_POSITIONS)
            return NO_MORE_BUCKETS;
        assert(source.startPosition() >= 0 && source.startPosition() != Spans.NO_MORE_POSITIONS);
        assert positionedAtHitIfPositionedInDoc();
        return gatherHitsInternal();
    }

    /**
     * Go to the next bucket at or beyond the specified start point.
     *
     * Always at least advances to the next bucket, even if we were already at or
     * beyond the specified target.
     * 
     * Note that this will only work correctly if the underlying Spans is startpoint-sorted.
     *
     * @param targetPos the target start point
     * @return docID if we're at a valid bucket, or NO_MORE_BUCKETS if we're done.
     */
    public int advanceBucket(int targetPos) throws IOException {
        assert positionedAtHitIfPositionedInDoc();
        if (source.startPosition() >= targetPos) {
            int i = nextBucket();
            assert positionedAtHitIfPositionedInDoc();
            return i;
        }
        if (source.advanceStartPosition(targetPos) == Spans.NO_MORE_POSITIONS) {
            assert positionedAtHitIfPositionedInDoc();
            return NO_MORE_BUCKETS;
        }
        assert(source.startPosition() >= 0 && source.startPosition() != Spans.NO_MORE_POSITIONS);
        assert positionedAtHitIfPositionedInDoc();
        int i = gatherHitsInternal();
        assert positionedAtHitIfPositionedInDoc();
        return i;
    }

    /**
     * Subclasses should override this to gather the hits they wish to put in the
     * next bucket.
     *
     * Upon entering this method, the source spans is at the last unused hit (or the
     * first hit in a new document). At the end, it should be at the first hit that
     * doesn't fit in the bucket (or beyond the last hit, i.e.
     * Spans.NO_MORE_POSITIONS).
     *
     */
    protected abstract void gatherHits() throws IOException;

    private int gatherHitsInternal() throws IOException {
        assert positionedAtHitIfPositionedInDoc();
        startsEnds.clear();
        if (doMatchInfo) {
            matchInfoPerHit.clear();
            activeRelationPerHit.clear();
        }
        doMatchInfo = clauseCapturesMatchInfo && hitQueryContext != null && hitQueryContext.numberOfMatchInfos() > 0;
        if (doMatchInfo && matchInfoPerHit == null) {
            matchInfoPerHit = new ObjectArrayList<>(LIST_INITIAL_CAPACITY);
            activeRelationPerHit = new ObjectArrayList<>(LIST_INITIAL_CAPACITY);
        }
        assert(source.startPosition() >= 0 && source.startPosition() != Spans.NO_MORE_POSITIONS);
        gatherHits();
        assert(source.startPosition() >= 0);
        assert positionedAtHitIfPositionedInDoc();
        return source.docID();
    }

    @Override
    public int docID() {
        return source.docID();
    }

    @Override
    public String toString() {
        return source.toString();
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
        MatchInfo[] previouslyCapturedMatchInfo = matchInfoPerHit.get(indexInBucket);
        if (previouslyCapturedMatchInfo != null) {
            for (int i = 0; i < matchInfo.length; i++) {
                if (previouslyCapturedMatchInfo.length > i &&  previouslyCapturedMatchInfo[i] != null)
                    matchInfo[i] = previouslyCapturedMatchInfo[i];
            }
        }
    }

    @Override
    public boolean hasMatchInfo() {
        return source.hasMatchInfo();
    }

    @Override
    public RelationInfo getRelationInfo(int indexInBucket) {
        if (!doMatchInfo)
            return null;
        return activeRelationPerHit.get(indexInBucket);
    }

    @Override
    public SpanGuarantees guarantees() {
        return source.guarantees();
    }

    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        assert positionedAtHitIfPositionedInDoc();
        TwoPhaseIterator twoPhaseIterator = getTwoPhaseIterator(source);
        assert positionedAtHitIfPositionedInDoc();
        return twoPhaseIterator;
    }

    public long cost() {
        return source.cost();
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
