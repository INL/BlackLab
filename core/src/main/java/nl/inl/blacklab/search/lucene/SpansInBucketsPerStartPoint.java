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
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.Spans;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import nl.inl.blacklab.search.Span;

/**
 * Gather hits from a Spans object in "buckets" by the start point of the hits.
 * Allow us to retrieve all hits that start at a certain point.
 *
 * The reason we don't use SpansInBucketsAbstract here is that it's more
 * efficient to just save the endpoints for the current start point (the source
 * spans is normally startpoint-sorted already).
 */
class SpansInBucketsPerStartPoint extends DocIdSetIterator implements SpansInBuckets {
    protected BLSpans source;

    protected int currentDoc = -1;

    protected int currentBucketStart = -1;

    protected int currentSpansStart = -1;

    private IntArrayList endPoints = new IntArrayList(LIST_INITIAL_CAPACITY);

    private List<Span[]> capturedGroupsPerEndpoint = new ArrayList<>(LIST_INITIAL_CAPACITY);

    private int bucketSize = 0;

    private HitQueryContext hitQueryContext;

    /** Do we have a hitQueryContext and does it contain captured groups? */
    private boolean doCapturedGroups = true;

    /**
     * Does our clause capture any groups? If not, we don't need to mess with those
     */
    protected boolean clauseCapturesGroups = true;

    /**
     * Construct SpansInBucketsPerStartPoint.
     *
     * @param source (startpoint-sorted) source spans
     */
    public SpansInBucketsPerStartPoint(BLSpans source) {
        this.source = source; //Sort
    }

    @Override
    public int docID() {
        return currentDoc;
    }

    @Override
    public int nextDoc() throws IOException {
        if (currentDoc != NO_MORE_DOCS) {
            currentDoc = source.nextDoc();
            if (currentDoc != NO_MORE_DOCS)
                currentSpansStart = source.nextStartPosition();
            currentBucketStart = -1; // no bucket yet
        }
        return currentDoc;
    }

    @Override
    public int nextBucket() throws IOException {
        if (currentDoc < 0) {
            // Not nexted yet, no bucket
            return -1;
        }
        if (currentSpansStart == Spans.NO_MORE_POSITIONS)
            return NO_MORE_BUCKETS;

        return gatherEndPointsAtStartPoint();
    }

    /**
     * Go to the next bucket at or beyond the specified start point.
     *
     * Always at least advances to the next bucket, even if we were already at or
     * beyond the specified target.
     *
     * @param targetPos the target start point
     * @return docID if we're at a valid bucket, or NO_MORE_BUCKETS if we're done.
     * @throws IOException
     */
    public int advanceBucket(int targetPos) throws IOException {
        if (currentSpansStart >= targetPos)
            return nextBucket();
        currentSpansStart = source.advanceStartPosition(targetPos);
        if (currentSpansStart == Spans.NO_MORE_POSITIONS)
            return NO_MORE_BUCKETS;
        return gatherEndPointsAtStartPoint();
    }

    @SuppressWarnings("unused")
    protected int gatherEndPointsAtStartPoint() throws IOException {
        if (!REALLOCATE_IF_TOO_LARGE || endPoints.size() < COLLECTION_REALLOC_THRESHOLD) {
            // Not a huge amount of memory, so don't reallocate
            endPoints.clear();
            capturedGroupsPerEndpoint.clear();
        } else {
            // Reallocate in this case to avoid holding on to a lot of memory
            endPoints = new IntArrayList(LIST_INITIAL_CAPACITY);
            capturedGroupsPerEndpoint = new ArrayList<>(LIST_INITIAL_CAPACITY);
        }

        doCapturedGroups = clauseCapturesGroups && source != null && hitQueryContext != null
                && hitQueryContext.numberOfCapturedGroups() > 0;

        bucketSize = 0;
        currentBucketStart = currentSpansStart;
        while (currentSpansStart != Spans.NO_MORE_POSITIONS && currentSpansStart == currentBucketStart) {
            endPoints.add(source.endPosition());
            if (doCapturedGroups) {
                Span[] capturedGroups = new Span[hitQueryContext.numberOfCapturedGroups()];
                source.getCapturedGroups(capturedGroups);
                capturedGroupsPerEndpoint.add(capturedGroups);
            }
            bucketSize++;
            currentSpansStart = source.nextStartPosition();
        }
        return currentDoc;
    }

    @Override
    public int advance(int target) throws IOException {
        if (currentDoc >= target) {
            return nextDoc();
        }

        if (currentDoc == NO_MORE_DOCS)
            return DocIdSetIterator.NO_MORE_DOCS;

        if (currentDoc < target) {
            currentDoc = source.advance(target);
            currentSpansStart = source.nextStartPosition();
            currentBucketStart = -1; // no bucket yet
        }

        return currentDoc;
    }

    @Override
    public String toString() {
        return source.toString();
    }

    @Override
    public int bucketSize() {
        return bucketSize;
    }

    @Override
    public int startPosition(int indexInBucket) {
        return currentBucketStart;
    }

    @Override
    public int endPosition(int indexInBucket) {
        return endPoints.get(indexInBucket);
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
        if (!doCapturedGroups || capturedGroupsPerEndpoint.isEmpty())
            return;
        Span[] previouslyCapturedGroups = capturedGroupsPerEndpoint.get(indexInBucket);
        if (previouslyCapturedGroups != null) {
            for (int i = 0; i < capturedGroups.length; i++) {
                if (previouslyCapturedGroups[i] != null)
                    capturedGroups[i] = previouslyCapturedGroups[i];
            }
        }
    }

    @Override
    public long cost() {
        return 300; // (arbitrary value. This is used for scoring, which we don't use yet)
    }

}
