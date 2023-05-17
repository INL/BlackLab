package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.Spans;

/**
 * Interface to retrieve whole sequences of certain matches (in "buckets")
 * instead of individual matches like with Spans.
 *
 * This is useful for efficiently processing sequences of related matches (i.e.
 * fetch some content for all matches in one document).
 *
 * N.B. Note that in these classes, we avoid the term 'group' and 'grouping'
 * because we already use these terms for the generic way of grouping spans
 * (nl.inl.blacklab.search.grouping), while this is more focused on speed and
 * efficiency of certain specific operations.
 *
 * Specifically, SpansInBuckets is designed to have random access to the
 * contents of a bucket, but for efficiency's sake, only has sequential access
 * to the buckets themselves. Also, SpansInBuckets uses subclassing instead of
 * GroupIdentity objects to determine what goes in a bucket. This makes it
 * easier to optimize.
 *
 * Note that SpansInBuckets assumes all hits in a bucket are from a single
 * document.
 */
public abstract class SpansInBuckets extends DocIdSetIterator implements SpanGuaranteeGiver {
    
    /** What initial capacity to reserve for lists to avoid too much reallocation */
    public static final int LIST_INITIAL_CAPACITY = 1000;

    public static final int NO_MORE_BUCKETS = Spans.NO_MORE_POSITIONS;

    /**
     * Document id of current bucket
     *
     * @return Document id of current bucket
     */
    public abstract int docID();

    public abstract int bucketSize();

    public abstract int startPosition(int index);

    public abstract int endPosition(int index);

    /**
     * Go to the next document.
     *
     * You still have to call nextBucket() to get to the first valid bucket.
     *
     * @return docID if we're at the next valid doc, NO_MORE_DOCS if we're done
     */
    public abstract int nextDoc() throws IOException;

    /**
     * Go to the next bucket in this doc.
     *
     * @return docID if we're at the next valid bucket, NO_MORE_BUCKETS if we're
     *         done
     */
    public abstract int nextBucket() throws IOException;

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
    public abstract int advanceBucket(int targetPos) throws IOException;

    /**
     * Skip to specified document id.
     *
     * If we're already at the target id, go to the next document (just like Spans).
     *
     * @param target document id to skip to
     * @return docID if we're at a valid document, NO_MORE_DOCS if we're done
     */
    public abstract int advance(int target) throws IOException;

    /**
     * Pass the hit query context to the underlying BLSpans.
     *
     * @param context the hit query context
     */
    public abstract void setHitQueryContext(HitQueryContext context);

    /**
     * Get the captured groups information for the current hit.
     *
     * @param indexInBucket what hit in the current bucket to get the information
     *            for
     * @param matchInfo where to add the captured group information
     */
    public abstract void getMatchInfo(int indexInBucket, MatchInfo[] matchInfo);

    /**
     * Does any of our descendants capture match info?
     *
     * This will recursively call this method on all subclauses.
     * Can be used before hit query context is set. After that, it's
     * more efficient to just remember whether any clauses added capture groups.
     *
     * @return true if any of our subclauses capture match info
     */
    public abstract boolean hasMatchInfo();

    public abstract TwoPhaseIterator asTwoPhaseIterator();

    public abstract long cost();

    protected static TwoPhaseIterator getTwoPhaseIterator(BLSpans source) {
        TwoPhaseIterator inner = source.asTwoPhaseIterator();
        if (inner != null) {
            return new TwoPhaseIterator(inner.approximation()) {
                @Override
                public boolean matches() throws IOException {
                    if (!inner.matches())
                        return false;
                    source.nextStartPosition(); // start gathering at the first hit
                    return true;
                }

                @Override
                public float matchCost() {
                    return inner.matchCost();
                }

                @Override
                public String toString() {
                    return "SpansInBucketsAbstract@asTwoPhaseIterator(source=" + source + ", iter=" + inner + ")";
                }
            };
        } else {
            return new TwoPhaseIterator(source) {
                @Override
                public boolean matches() throws IOException {
                    source.nextStartPosition(); // start gathering at the first hit
                    return true;
                }

                @Override
                public float matchCost() {
                    return source.positionsCost(); // overestimate
                }

                @Override
                public String toString() {
                    return "SpansInBucketsAbstract@asTwoPhaseIterator(source=" + source + ")";
                }
            };
        }
    }

    public abstract float positionsCost();

    public abstract int width();
}
