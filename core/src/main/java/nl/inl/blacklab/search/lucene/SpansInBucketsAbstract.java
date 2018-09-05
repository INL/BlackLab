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
package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.Spans;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongComparator;
import nl.inl.blacklab.search.Span;

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
abstract class SpansInBucketsAbstract implements SpansInBuckets {
    
    protected BLSpans source;

    protected int currentDoc = -1;

    /** Starts of hits in our bucket */
    private LongArrayList bucket = new LongArrayList(LIST_INITIAL_CAPACITY);

    /**
     * For each hit we fetched, store the captured groups, so we don't lose this
     * information.
     */
    private Long2ObjectMap<Span[]> capturedGroupsPerHit = null;

    /**
     * Size of the current bucket, or -1 if we're not at a valid bucket.
     */
    private int bucketSize = -1;

    private HitQueryContext hitQueryContext;

    /** Is there captured group information for each hit that we need to store? */
    private boolean doCapturedGroups;

    /**
     * Does our clause capture any groups? If not, we don't need to mess with those
     */
    protected boolean clauseCapturesGroups = true;

    protected void addHitFromSource() {
        long span = ((long)source.startPosition() << 32) | source.endPosition();
        bucket.add(span);
        if (doCapturedGroups) {
            // Store captured group information
            Span[] capturedGroups = new Span[hitQueryContext.numberOfCapturedGroups()];
            source.getCapturedGroups(capturedGroups);
            if (capturedGroupsPerHit == null)
                capturedGroupsPerHit = new Long2ObjectOpenHashMap<>(HASHMAP_INITIAL_CAPACITY);
            capturedGroupsPerHit.put(span, capturedGroups);
        }
        bucketSize++;
    }
    
    static final LongComparator longCmpEndPoint = new LongComparator() {
        @Override
        public int compare(long k1, long k2) {
            int a = (int)k1;
            int b = (int)k2;
            if (a == b)
                return (int)(k1 >> 32) - (int)(k2 >> 32); // compare start points
            else
                return a - b; // compare endpoints
        }
    };
    
    protected void sortHits(boolean sortByStartPoint) {
        if (sortByStartPoint) { 
            LongArrays.quickSort(bucket.elements(), 0, bucket.size()); // natural order is startpoint order
        } else {
            LongArrays.quickSort(bucket.elements(), 0, bucket.size(), longCmpEndPoint);
        }
    }

    @Override
    public int bucketSize() {
        return bucketSize;
    }

    @Override
    public int startPosition(int indexInBucket) {
        //return bucketSlow.get(indexInBucket).start();
        return (int)(bucket.getLong(indexInBucket) >> 32);
    }

    @Override
    public int endPosition(int indexInBucket) {
        //return bucketSlow.get(indexInBucket).end();
        return (int)bucket.getLong(indexInBucket);
    }

    public SpansInBucketsAbstract(BLSpans source) {
        this.source = source;
    }

    @Override
    public int nextDoc() throws IOException {
        bucketSize = -1; // not at a valid bucket anymore
        if (currentDoc != DocIdSetIterator.NO_MORE_DOCS) {
            currentDoc = source.nextDoc();
            if (currentDoc != DocIdSetIterator.NO_MORE_DOCS) {
                source.nextStartPosition(); // start gathering at the first hit
                //gatherHitsInternal();
            }
        }
        return currentDoc;
    }

    @Override
    public int nextBucket() throws IOException {
        if (currentDoc < 0) {
            // Not nexted yet, no bucket
            return -1;
        }
        if (currentDoc == DocIdSetIterator.NO_MORE_DOCS || source.startPosition() == Spans.NO_MORE_POSITIONS)
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
     * @throws IOException
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
     * @throws IOException
     */
    protected abstract void gatherHits() throws IOException;

    @Override
    public int advance(int target) throws IOException {
        bucketSize = -1; // not at a valid bucket anymore
        if (currentDoc != DocIdSetIterator.NO_MORE_DOCS) {
            if (currentDoc >= target)
                nextDoc();
            else {
                currentDoc = source.advance(target);
                if (currentDoc != DocIdSetIterator.NO_MORE_DOCS) {
                    source.nextStartPosition(); // start gathering at the first hit
                    //gatherHitsInternal();
                }
            }
        }
        return currentDoc;
    }

    @SuppressWarnings("unused")
    private int gatherHitsInternal() throws IOException {
        // NOTE: we could call .clear() here, but we don't want to hold on to
        // a lot of memory indefinitely after encountering one huge bucket.
        if (!REALLOCATE_IF_TOO_LARGE || bucketSize < COLLECTION_REALLOC_THRESHOLD) {
            // Not a huge amount of memory, so don't reallocate
            bucket.clear();
            if (doCapturedGroups)
                capturedGroupsPerHit.clear();
        } else {
            // Reallocate in this case to avoid holding on to a lot of memory
            bucket.trim(COLLECTION_REALLOC_THRESHOLD / 2);
            if (doCapturedGroups)
                capturedGroupsPerHit = new Long2ObjectOpenHashMap<>();
        }

        bucketSize = 0;
        doCapturedGroups = clauseCapturesGroups && hitQueryContext != null
                && hitQueryContext.numberOfCapturedGroups() > 0;
        gatherHits();
        return currentDoc;
    }

    @Override
    public int docID() {
        return currentDoc;
    }

    @Override
    public String toString() {
        return source.toString();
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        this.hitQueryContext = context;
        int before = context.getCaptureRegisterNumber();
        source.setHitQueryContext(context);
        if (context.getCaptureRegisterNumber() == before) {
            // Our clause doesn't capture any groups; optimize
            clauseCapturesGroups = false;
        }
    }

    @Override
    public void getCapturedGroups(int indexInBucket, Span[] capturedGroups) {
        if (!doCapturedGroups)
            return;
        Span[] previouslyCapturedGroups = capturedGroupsPerHit.get(bucket.getLong(indexInBucket));
        if (previouslyCapturedGroups != null) {
            for (int i = 0; i < capturedGroups.length; i++) {
                if (previouslyCapturedGroups[i] != null)
                    capturedGroups[i] = previouslyCapturedGroups[i];
            }
        }
    }

}
