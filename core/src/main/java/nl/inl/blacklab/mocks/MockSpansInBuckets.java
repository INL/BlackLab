/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
/**
 *
 */
package nl.inl.blacklab.mocks;

import java.io.IOException;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.SpansInBuckets;

/**
 * Stub SpansInBuckets class for testing. Takes arrays and iterates through
 * 'hits' from these arrays.
 */
public class MockSpansInBuckets implements SpansInBuckets {

    private int[] start;

    private int[] end;

    private int[] bucketStart;

    private int[] bucketDoc;

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
    public int nextBucket() throws IOException {
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
    public int advance(int target) throws IOException {
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
    public void getCapturedGroups(int indexInBucket, Span[] capturedGroups) {
        // NOP
    }

    public static SpansInBuckets fromLists(int[] bDoc, int[] bStart,
            int[] hStart, int[] hEnd) {
        return new MockSpansInBuckets(bDoc, bStart, hStart, hEnd);
    }

}
