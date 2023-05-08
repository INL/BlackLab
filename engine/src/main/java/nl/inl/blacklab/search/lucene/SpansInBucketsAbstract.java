package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.Spans;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongComparator;
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
    protected final LongArrayList startsEnds = new LongArrayList(LIST_INITIAL_CAPACITY);

    /**
     * For each hit we fetched, store the match info (e.g. captured groups, relations),
     * so we don't lose this information.
     */
    protected ObjectArrayList<MatchInfo[]> matchInfoPerHit = null;

    private HitQueryContext hitQueryContext;

    /** Is there match info (e.g. captured groups) for each hit that we need to store? */
    private boolean doMatchInfo;

    /**
     * Does our clause capture any match info? If not, we don't need to mess with those
     */
    protected boolean clauseCapturesMatchInfo = true;

    protected void addHitFromSource() {
        long span = ((long)source.startPosition() << 32) | source.endPosition();
        startsEnds.add(span);
        if (doMatchInfo) {
            // Store match information such as captured groups
            MatchInfo[] matchInfo = new MatchInfo[hitQueryContext.numberOfMatchInfos()];
            source.getMatchInfo(matchInfo);
            if (matchInfoPerHit == null)
                matchInfoPerHit = new ObjectArrayList<>(LIST_INITIAL_CAPACITY);
            matchInfoPerHit.add(matchInfo);
        }
    }
    
    static final LongComparator longCmpEndPoint = (k1, k2) -> {
        int a = (int)k1;
        int b = (int)k2;
        if (a == b)
            return (int)(k1 >> 32) - (int)(k2 >> 32); // compare start points
        else
            return a - b; // compare endpoints
    };
    
    protected void sortHits(boolean sortByStartPoint) {
        // FIXME: match info is not sorted!
        if (sortByStartPoint) { 
            LongArrays.quickSort(startsEnds.elements(), 0, startsEnds.size()); // natural order is startpoint order
        } else {
            LongArrays.quickSort(startsEnds.elements(), 0, startsEnds.size(), longCmpEndPoint);
        }
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
        if (source.docID() != DocIdSetIterator.NO_MORE_DOCS) {
            if (source.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                source.nextStartPosition(); // start gathering at the first hit
            }
        }
        return source.docID();
    }

    @Override
    public int nextBucket() throws IOException {
        if (source.docID() < 0) {
            // Not nexted yet, no bucket
            return -1;
        }
        if (source.docID() == DocIdSetIterator.NO_MORE_DOCS || source.startPosition() == Spans.NO_MORE_POSITIONS)
            return NO_MORE_BUCKETS;
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
        if (source.startPosition() >= targetPos)
            return nextBucket();
        if (source.advanceStartPosition(targetPos) == Spans.NO_MORE_POSITIONS)
            return NO_MORE_BUCKETS;
        return gatherHitsInternal();
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

    @Override
    public int advance(int target) throws IOException {
        if (source.docID() != DocIdSetIterator.NO_MORE_DOCS) {
            if (source.docID() >= target) {
                // Already at or beyond; go to the next doc
                // (though we shouldn't rely on this behavior)
                nextDoc();
            } else {
                if (source.advance(target) != DocIdSetIterator.NO_MORE_DOCS) {
                    source.nextStartPosition(); // start gathering at the first hit
                }
            }
        }
        return source.docID();
    }

    private int gatherHitsInternal() throws IOException {
        startsEnds.clear();
        if (doMatchInfo)
            matchInfoPerHit.clear();
        doMatchInfo = clauseCapturesMatchInfo && hitQueryContext != null
                && hitQueryContext.numberOfMatchInfos() > 0;
        gatherHits();
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
        this.hitQueryContext = context;
        int before = context.getMatchInfoRegisterNumber();
        source.setHitQueryContext(context);
        if (context.getMatchInfoRegisterNumber() == before) {
            // Our clause doesn't capture any match info; optimize
            clauseCapturesMatchInfo = false;
        }
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
    public TwoPhaseIterator asTwoPhaseIterator() {
        return getTwoPhaseIterator(source);
    }

    public long cost() {
        return source.cost();
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
