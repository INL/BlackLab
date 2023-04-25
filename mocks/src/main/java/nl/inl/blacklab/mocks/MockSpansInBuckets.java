package nl.inl.blacklab.mocks;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.SpansInBuckets;

/**
 * Stub SpansInBuckets class for testing. Takes arrays and iterates through
 * 'hits' from these arrays.
 */
public class MockSpansInBuckets extends SpansInBuckets {

    private final int[] start;

    private final int[] end;

    private final int[] bucketStart;

    private final int[] bucketDoc;

    private int currentBucket = -1;

    private boolean alreadyAtFirstBucket = false;

    public MockSpansInBuckets(int[] bucketDoc, int[] bucketStart, int[] start, int[] end) {
        this.bucketDoc = bucketDoc;
        this.bucketStart = bucketStart;
        this.start = start;
        this.end = end;
    }

    @Override
    public int docID() {
        if (currentBucket >= bucketDoc.length)
            return DocIdSetIterator.NO_MORE_DOCS;
        return bucketDoc[currentBucket];
    }

    @Override
    public int endPosition(int i) {
        if (currentBucket >= bucketDoc.length)
            return Spans.NO_MORE_POSITIONS;
        if (alreadyAtFirstBucket)
            return -1;
        return end[bucketStart[currentBucket] + i];
    }

    @Override
    public int startPosition(int i) {
        if (currentBucket >= bucketDoc.length)
            return Spans.NO_MORE_POSITIONS;
        if (alreadyAtFirstBucket)
            return -1;
        return start[bucketStart[currentBucket] + i];
    }

    @Override
    public int nextBucket() {
        if (alreadyAtFirstBucket) {
            alreadyAtFirstBucket = false;
            return docID();
        }
        if (currentBucket >= bucketDoc.length - 1) {
            currentBucket = bucketDoc.length;
            return NO_MORE_BUCKETS; // completely done
        }
        if (bucketDoc[currentBucket + 1] != bucketDoc[currentBucket])
            return NO_MORE_BUCKETS; // no more in this doc
        currentBucket++;
        return docID();
    }

    @Override
    public int nextDoc() {
        if (currentBucket >= bucketDoc.length)
            return DocIdSetIterator.NO_MORE_DOCS;
        alreadyAtFirstBucket = false;
        int curDoc = currentBucket == -1 ? -1 : bucketDoc[currentBucket];
        while (currentBucket < bucketDoc.length && (currentBucket == -1 || bucketDoc[currentBucket] == curDoc)) {
            currentBucket++;
        }
        if (currentBucket == bucketDoc.length)
            return DocIdSetIterator.NO_MORE_DOCS;
        alreadyAtFirstBucket = true;
        return docID();
    }

    @Override
    public int advance(int target) {
        alreadyAtFirstBucket = false;
        int doc;
        do {
            doc = nextDoc();
        } while (doc != DocIdSetIterator.NO_MORE_DOCS && doc < target);
        if (doc != DocIdSetIterator.NO_MORE_DOCS)
            alreadyAtFirstBucket = true;
        return doc;
    }

    @Override
    public int bucketSize() {
        if (alreadyAtFirstBucket)
            return -1;
        if (currentBucket >= bucketDoc.length)
            return -1;
        if (currentBucket == bucketDoc.length - 1)
            return start.length - bucketStart[currentBucket];
        return bucketStart[currentBucket + 1] - bucketStart[currentBucket];
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        // NOP
    }

    @Override
    public void getMatchInfo(int indexInBucket, MatchInfo[] matchInfo) {
        // NOP
    }

    public static SpansInBuckets fromLists(int[] bDoc, int[] bStart,
            int[] hStart, int[] hEnd) {
        return new MockSpansInBuckets(bDoc, bStart, hStart, hEnd);
    }

    @Override
    public long cost() {
        return 0;
    }

    @Override
    public float positionsCost() {
        return start.length;
    }
}
