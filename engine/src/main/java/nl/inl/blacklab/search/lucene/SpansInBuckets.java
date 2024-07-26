package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.queries.spans.Spans;

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
    /** If true, we need to call nextStartPosition() once before gathering hits in current bucket.
     *  (this happens at the start of the document; in any other case, we're already at the first
     *   hit in the next bucket, because we determine if we're done with a bucket by iterating over
     *   hits until we find one that doesn't belong in the current bucket)
     *  (we used to just advance to the first hit in the document whenever we started a new one,
     *   but this gets messy in combination with two-phase iterators, which advance through documents
     *   without looking at hits)
     */
    protected boolean beforeFirstBucketHit = false;

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

    /**
     * Get the "active" relation info.
     * <p>
     * A query that finds and combines several relations always has one
     * active relation. This relation is used when we call rspan(),
     * or if we combine the query with another relation query, e.g. using the
     * &amp; operator.
     *
     * @return the relation info, or null if active relation available
     */
    public RelationInfo getRelationInfo(int indexInBucket) {
        return null;
    }

    public abstract TwoPhaseIterator asTwoPhaseIterator();

    public abstract long cost();

    /**
     * Assert that, if our clause is positioned at a doc, nextStartPosition() has also been called.
     *
     * Sanity check to be called from assertions at the start and end of methods that change the internal state.
     *
     * We require this because nextBucket() expects the clause to be positioned at a hit. This is because
     * for certain bucketing operations we can only decide we're done with a bucket if we're at the first hit
     * that doesn't belong in the bucket.
     *
     * If {@link #beforeFirstBucketHit} is set and we're not at a hit, that's fine too: in that case, we've just
     * started a document and haven't nexted yet (we defer that because of two-phase iterators).
     *
     * @return true if positioned at a hit (or at a doc and nextStartPosition() has been called), false if not
     */
    protected boolean positionedAtHitIfPositionedInDoc(BLSpans source) {
        return source.docID() < 0 || source.docID() == NO_MORE_DOCS ||  // not in doc?
                (beforeFirstBucketHit && source.startPosition() < 0) ||     // just started a doc?
                source.startPosition() >= 0;                                // positioned at hit in doc
    }

    protected void prepareForFirstBucketInDocument(BLSpans source) {
        // Mark that we've just started a new document, and we need to call source.nextStartPosition()
        // first before gathering hits in the current bucket.
        // (from the second bucket in a document onwards, we always know we're already at the first hit
        //  in the bucket)
        beforeFirstBucketHit = true;
        assert positionedAtHitIfPositionedInDoc(source);
    }

    protected void ensureAtFirstHit(BLSpans source) throws IOException {
        if (beforeFirstBucketHit) {
            // We've just started a new document, and haven't called nextStartPosition() yet. Do so now.
            source.nextStartPosition();
            beforeFirstBucketHit = false;
        }
    }

    protected TwoPhaseIterator getTwoPhaseIterator(BLSpans source) {
        TwoPhaseIterator inner = source.asTwoPhaseIterator();
        if (inner != null) {
            return new TwoPhaseIterator(inner.approximation()) {
                @Override
                public boolean matches() throws IOException {
                    if (!inner.matches())
                        return false;
                    assert source.docID() >= 0 && source.docID() != NO_MORE_DOCS;
                    prepareForFirstBucketInDocument(source);
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
                    assert source.docID() >= 0 && source.docID() != NO_MORE_DOCS;
                    prepareForFirstBucketInDocument(source);
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
