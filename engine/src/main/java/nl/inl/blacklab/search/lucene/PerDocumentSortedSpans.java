package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.SpanCollector;

import nl.inl.blacklab.search.Span;

/**
 * Sort the given Spans per document, according to the given comparator.
 */
final class PerDocumentSortedSpans extends BLSpans {
    
    public static PerDocumentSortedSpans startPointEliminateDuplicates(BLSpans src) {
        return new PerDocumentSortedSpans(src, true, true);
    }

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

    private int curDoc = -1;

    private int curStart = -1;

    private int curEnd = -1;

    private final SpansInBuckets bucketedSpans;

    private final boolean eliminateDuplicates;

    private final boolean sortByStartPoint;

    private int indexInBucket = -2; // -2 == no bucket yet; -1 == just started a bucket

    private PerDocumentSortedSpans(BLSpans src, boolean sortByStartPoint, boolean eliminateDuplicates) {
        // Wrap a HitsPerDocument and show it to the client as a normal, sequential Spans.
        bucketedSpans = new SpansInBucketsPerDocumentSorted(src, sortByStartPoint);

        this.eliminateDuplicates = eliminateDuplicates;
        this.sortByStartPoint = sortByStartPoint;
    }

    @Override
    public int docID() {
        return curDoc;
    }

    @Override
    public int startPosition() {
        if (indexInBucket < 0)
            return -1;
        if (indexInBucket >= bucketedSpans.bucketSize())
            return NO_MORE_POSITIONS;
        return curStart;
    }

    @Override
    public int endPosition() {
        if (indexInBucket < 0)
            return -1;
        if (indexInBucket >= bucketedSpans.bucketSize())
            return NO_MORE_POSITIONS;
        return curEnd;
    }

    @Override
    public int nextDoc() throws IOException {
        curDoc = bucketedSpans.nextDoc();
        indexInBucket = -2;
        curStart = -1;
        curEnd = -1;
        return curDoc;
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (!eliminateDuplicates) {
            // No need to eliminate duplicates
            if (indexInBucket == -2 || indexInBucket >= bucketedSpans.bucketSize() - 1) {
                // Bucket exhausted or no bucket yet; get one
                if (bucketedSpans.nextBucket() == SpansInBuckets.NO_MORE_BUCKETS) {
                    indexInBucket = SpansInBuckets.NO_MORE_BUCKETS;
                    return NO_MORE_POSITIONS;
                }
                indexInBucket = -1;
            }
            indexInBucket++;
            curStart = bucketedSpans.startPosition(indexInBucket);
            curEnd = bucketedSpans.endPosition(indexInBucket);
        } else {
            // Eliminate any duplicates
            int prevEnd;
            int prevStart;
            do {
                if (indexInBucket == -2 || indexInBucket >= bucketedSpans.bucketSize() - 1) {
                    // Bucket exhausted or no bucket yet; get one
                    if (bucketedSpans.nextBucket() == SpansInBuckets.NO_MORE_BUCKETS) {
                        indexInBucket = SpansInBuckets.NO_MORE_BUCKETS;
                        return NO_MORE_POSITIONS;
                    }
                    indexInBucket = -1;
                }
                if (indexInBucket >= 0) {
                    prevStart = bucketedSpans.startPosition(indexInBucket);
                    prevEnd = bucketedSpans.endPosition(indexInBucket);
                } else {
                    prevStart = prevEnd = -1;
                }
                indexInBucket++;
                curStart = bucketedSpans.startPosition(indexInBucket);
                curEnd = bucketedSpans.endPosition(indexInBucket);
            } while (prevStart == curStart && prevEnd == curEnd);
        }
        return curStart;
    }

    @Override
    public int advance(int target) throws IOException {
        curDoc = bucketedSpans.advance(target);
        indexInBucket = -2;
        curStart = -1;
        curEnd = -1;
        return curDoc;
    }

    @Override
    public String toString() {
        String name = "sort" + (sortByStartPoint ? "Start" : "End") + (eliminateDuplicates ? "Uniq" : "");
        return name + "(" + bucketedSpans + ")";
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        bucketedSpans.setHitQueryContext(context);
    }

    @Override
    public void getCapturedGroups(Span[] capturedGroups) {
        if (indexInBucket < 0 || indexInBucket >= bucketedSpans.bucketSize())
            return;
        bucketedSpans.getCapturedGroups(indexInBucket, capturedGroups);
    }

    @Override
    public int width() {
        return 0; // should maybe be bucketedSpans.width(indexInBucket) ? but we don't use .width()
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        // BucketedSpans should collect payload as well, but for now, we don't use
        // payload beyond a "simple" SpanQuery like SpanQueryTags.
        // bucketedSpans.collect(collector);
    }

    @Override
    public float positionsCost() {
        return 0; // should maybe be bucketedSpans.positionsCost()
    }
}
