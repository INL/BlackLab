package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.SpanCollector;

/**
 * Sort the given Spans per document, according to the given comparator.
 */
final class PerDocumentSortedSpans extends BLFilterDocsSpans<SpansInBuckets> {
    
    public static PerDocumentSortedSpans startPoint(BLSpans src) {
        return new PerDocumentSortedSpans(src, true, false);
    }

    public static PerDocumentSortedSpans startPoint(BLSpans src, boolean removeDuplicates) {
        return new PerDocumentSortedSpans(src, true, removeDuplicates);
    }

    public static PerDocumentSortedSpans endPoint(BLSpans src) {
        return new PerDocumentSortedSpans(src, false, false);
    }

    public static PerDocumentSortedSpans get(BLSpans src, boolean sortByStartPoint, boolean removeDuplicates) {
        return new PerDocumentSortedSpans(src, sortByStartPoint, removeDuplicates);
    }

    private int curStart = -1;

    private int curEnd = -1;

    private final boolean eliminateDuplicates;

    private final boolean sortByStartPoint;

    private int indexInBucket = -2; // -2 == no bucket yet; -1 == just started a bucket

    private PerDocumentSortedSpans(BLSpans src, boolean sortByStartPoint, boolean eliminateDuplicates) {
        // Wrap a HitsPerDocument and show it to the client as a normal, sequential Spans.
        super(new SpansInBucketsPerDocumentSorted(src, sortByStartPoint), new SpanGuaranteesAdapter(src.guarantees()) {
            @Override
            public boolean hitsStartPointSorted() {
                return sortByStartPoint || super.hitsStartPointSorted();
            }

            @Override
            public boolean hitsAreUnique() {
                return eliminateDuplicates || super.hitsAreUnique();
            }
        });
        this.eliminateDuplicates = eliminateDuplicates;
        this.sortByStartPoint = sortByStartPoint;

        SpanGuarantees g = src.guarantees();
        if (eliminateDuplicates && g.hitsAreUnique())
            throw new IllegalArgumentException("Uniqueness requested but hits are already unique");
        if (sortByStartPoint && g.hitsStartPointSorted()) {
            throw new IllegalArgumentException("Hits are already startpoint sorted, use SpansUnique instead");
        }
    }

    @Override
    public int startPosition() {
        if (indexInBucket < 0)
            return -1;
        if (indexInBucket >= in.bucketSize())
            return NO_MORE_POSITIONS;
        return curStart;
    }

    @Override
    public int endPosition() {
        if (indexInBucket < 0)
            return -1;
        if (indexInBucket >= in.bucketSize())
            return NO_MORE_POSITIONS;
        return curEnd;
    }

    @Override
    public int nextDoc() throws IOException {
        indexInBucket = -2;
        curStart = -1;
        curEnd = -1;
        return super.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        indexInBucket = -2;
        curStart = -1;
        curEnd = -1;
        return super.advance(target);
    }

    @Override
    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        // If our clause matches, we match as well; we just reorder the matches.
        return true;
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (!eliminateDuplicates) {
            // No need to eliminate duplicates
            if (indexInBucket == -2 || indexInBucket >= in.bucketSize() - 1) {
                // Bucket exhausted or no bucket yet; get one
                if (in.nextBucket() == SpansInBuckets.NO_MORE_BUCKETS) {
                    indexInBucket = SpansInBuckets.NO_MORE_BUCKETS;
                    return NO_MORE_POSITIONS;
                }
                indexInBucket = -1;
            }
            indexInBucket++;
            curStart = in.startPosition(indexInBucket);
            curEnd = in.endPosition(indexInBucket);
        } else {
            // Eliminate any duplicates
            // FIXME: this doesn't take match info into account, which means we might get rid of
            //        match info we're interested in
            int prevEnd;
            int prevStart;
            do {
                if (indexInBucket == -2 || indexInBucket >= in.bucketSize() - 1) {
                    // Bucket exhausted or no bucket yet; get one
                    if (in.nextBucket() == SpansInBuckets.NO_MORE_BUCKETS) {
                        indexInBucket = SpansInBuckets.NO_MORE_BUCKETS;
                        return NO_MORE_POSITIONS;
                    }
                    indexInBucket = -1;
                }
                if (indexInBucket >= 0) {
                    prevStart = in.startPosition(indexInBucket);
                    prevEnd = in.endPosition(indexInBucket);
                } else {
                    prevStart = prevEnd = -1;
                }
                indexInBucket++;
                curStart = in.startPosition(indexInBucket);
                curEnd = in.endPosition(indexInBucket);
            } while (prevStart == curStart && prevEnd == curEnd);
        }
        return curStart;
    }

    @Override
    public String toString() {
        String name = "sort" + (sortByStartPoint ? "Start" : "End") + (eliminateDuplicates ? "Uniq" : "");
        return name + "(" + in + ")";
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        in.setHitQueryContext(context);
    }

    @Override
    public void getMatchInfo(MatchInfo[] relationInfo) {
        if (indexInBucket < 0 || indexInBucket >= in.bucketSize())
            return;
        in.getMatchInfo(indexInBucket, relationInfo);
    }

    @Override
    public void collect(SpanCollector collector) {
        // BucketedSpans doesn't collect payload. We could update it to do so for queries that need it,
        // but for now, we don't use payload beyond a "simple" Spans like SpansRelations.
    }

    @Override
    public int width() {
        return in.width();
    }

    @Override
    public float positionsCost() {
        return in.positionsCost();
    }


}
