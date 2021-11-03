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

import org.apache.lucene.search.spans.SpanCollector;

import nl.inl.blacklab.search.Span;

/**
 * Combines spans, keeping only combinations of hits that occur one after the
 * other. The order is significant: a hit from the first span must be followed
 * by a hit from the second.
 *
 * There are different Spans classes that deal with variations of this problem;
 * this one deals with clauses with a "gap" in the middle, and doesn't sort the 
 * left clause by endpoint, making it potentially faster than other versions.
 *
 * It has to take the following problem into account, which might arise with
 * more complex sequences with overlapping hits ("1234" are token positions in
 * the document, A-C are hits in spans1, D-F are hits in spans2, "AD", "AE" and
 * "BF" are resulting sequence hits):
 *
 * <pre>
 *  spans1       1234
 *       A(1-2)  -
 *       B(1-3)  --
 *       C(2-4)   --
 *
 *  spans2       1234
 *       D(2-4)   --
 *       E(2-5)   ---
 *       F(3-4)    -
 *
 *  seq(1,2)     1234
 *       AD(1-4) ---
 *       AE(1-5) ----
 *       BF(1-4) ---
 * </pre>
 *
 * Note that the sequence of the two spans contains duplicates (AD and BF are
 * identical) and out-of-order endpoints (AE ends at 5 but BF ends at 4). Both
 * are undesirable; the user doesn't want to see duplicates, and out-of-order
 * endpoints may cause problems when combining this spans with other spans
 * (although that is not certain; should be checked).
 *
 * Therefore, objects of this class should be wrapped in a class that sort the
 * matches per document and eliminates duplicates.
 */
class SpansSequenceWithGap extends BLSpans {
    
    /** Allowable gap size between parts of a sequence. */
    public static class Gap {
        
        public static final Gap NONE = fixed(0);
        
        public static final Gap ANY = atLeast(0);
        
        public static Gap atLeast(int minSize) {
            return new Gap(minSize, MAX_UNLIMITED);
        }
        
        public static Gap atMost(int maxSize) {
            return new Gap(0, maxSize);
        }
        
        public static Gap fixed(int size) {
            return new Gap(size, size);
        }
        
        public static Gap variable(int minSize, int maxSize) {
            return new Gap(minSize, maxSize);
        }
        
        private int minSize;
        
        private int maxSize;
    
        public Gap(int minSize, int maxSize) {
            super();
            this.minSize = minSize;
            this.maxSize = maxSize;
        }
    
        public int minSize() {
            return minSize;
        }

        public int maxSize() {
            return maxSize;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + maxSize;
            result = prime * result + minSize;
            return result;
        }
    
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Gap other = (Gap) obj;
            if (maxSize != other.maxSize)
                return false;
            if (minSize != other.minSize)
                return false;
            return true;
        }
        
        @Override
        public String toString() {
            return minSize + "-" + maxSize;
        }
        
    }

    private BLSpans left;
    
    /** Gap between the two clauses. */
    private Gap gap;

    /** Right clause matches, collected for the whole document, sorted by startpoint. */
    private SpansInBucketsPerDocument right;

    /** 
     * First index in the right bucket that we could possibly match to a span with the current
     * start position.
     * 
     * This is based on the *start* position of the left clause, not the end position, because
     * the start position is guaranteed to always rise, but the end position could go down between
     * two spans (e.g. if a short span follows a long span, and the short span is actually contained
     * by the long span).
     * 
     * We will remember this index and iterate forward to see if multiple spans from the right
     * clause can be matched with a given left span. Then we will start from this index again
     * for the next left match, etc.
     */
    int indexInBucket = -2; // -2 == not started yet; -1 == just started a bucket

    /**
     * First index in the right bucket that we could match to the end of the current left span.
     */
    int indexInBucketLeftEnd = -2; // -2 == not started yet; -1 == just started a bucket
    
    int currentDoc = -1;

    int leftStart = -1;

    int rightEnd = -1;

    /**
     * Are we already a the first match in the document, even if
     * .nextStartPosition() hasn't been called? Required because we need to test for
     * matches in the document in .nextDoc()/.advance().
     */
    private boolean alreadyAtFirstMatch = false;

    private int leftStartFirst;

    private int rightStartFirst;

    private int rightStartLast;

    /**
     * Construct SpansSequenceWithGap.
     *
     * @param left (startpoint-sorted) left clause
     * @param gap allowable gap between the clauses
     * @param right (startpoint-sorted) right clause
     */
    public SpansSequenceWithGap(BLSpans left, Gap gap, BLSpans right) {
        this.left = left;
        this.gap = gap;
        this.right = new SpansInBucketsPerDocument(right);
    }

    @Override
    public int docID() {
        return currentDoc;
    }

    @Override
    public int endPosition() {
        if (alreadyAtFirstMatch)
            return -1; // .nextStartPosition() not called yet
        return rightEnd;
    }

    @Override
    public int nextDoc() throws IOException {
        alreadyAtFirstMatch = false;
        if (currentDoc != NO_MORE_DOCS) {
            currentDoc = left.nextDoc();
            leftStart = -1;
            if (currentDoc != NO_MORE_DOCS) {
                right.nextDoc();
                right.nextBucket();
                indexInBucket = -1;
                indexInBucketLeftEnd = -1;
                rightEnd = -1;
                realignDoc();
            }
        }
        return currentDoc;
    }

    @Override
    public int nextStartPosition() throws IOException {
        
        // Preconditions:
        // - left and right are in the same document
        // - left span is valid
        // - right bucket is available, indexInBucket and indexInBucketLeftEnd have been set
        
        // Did we already find the first match?
        if (alreadyAtFirstMatch) {
            alreadyAtFirstMatch = false;
            return leftStart;
        }

        // Are we done with this document?
        if (leftStart == NO_MORE_POSITIONS) {
            leftStart = rightEnd = NO_MORE_POSITIONS;
            return NO_MORE_POSITIONS;
        }
        
        /*
         * Go to the next match.
         * 
         * A match is the current left span combined with a span from the right bucket whose startpoint
         * matches the endpoint of the current left span.
         * 
         * Going to the next match therefore means:
         * - going to the next span in the right clause bucket (if that matches the endpoint of the current left span), OR
         * - going to the next left span and seeing if that has any matches in the right bucket
         */
        
        // See if there's another match with the same left span and a new right span.
        indexInBucketLeftEnd++;
        if (indexInBucketLeftEnd < right.bucketSize()) {
            // At next span in right clause bucket. Does this match?
            if (right.startPosition(indexInBucketLeftEnd) <= rightStartLast) {
                // Yes! Report the new match.
                rightEnd = right.endPosition(indexInBucketLeftEnd);
                return leftStart;
            }
        }
        // No more matches, end of bucket, or no bucket yet.
        // Find a new left span and corresponding right span(s).
        leftStart = left.nextStartPosition();
        realignPos();
        return leftStart;
    }

    /**
     * Puts both spans in the next doc (possibly the current one) that has a match
     * in it.
     *
     * @return docID if we're on a valid match, NO_MORE_DOCS if we're done.
     * @throws IOException
     */
    private int realignDoc() throws IOException {
        while (true) {
            // Put in same doc if necessary
            while (currentDoc != right.docID()) {
                while (currentDoc < right.docID()) {
                    currentDoc = left.advance(right.docID());
                    if (currentDoc == NO_MORE_DOCS) {
                        leftStart = rightEnd = NO_MORE_POSITIONS;
                        return NO_MORE_DOCS;
                    }
                    leftStart = -1;
                    rightEnd = -1;
                }
                while (right.docID() < currentDoc) {
                    int rightDoc = right.advance(currentDoc);
                    if (rightDoc == NO_MORE_DOCS) {
                        currentDoc = NO_MORE_DOCS;
                        leftStart = rightEnd = NO_MORE_POSITIONS;
                        return NO_MORE_DOCS;
                    }
                    right.nextBucket();
                    rightEnd = -1;
                    indexInBucket = 0;
                    indexInBucketLeftEnd = -1;
                }
            }

            // Does this doc have any matches?
            leftStart = left.nextStartPosition();
            realignPos();
            if (leftStart != NO_MORE_POSITIONS) {
                // Yes. Remember that we're already on the first match and return the doc id.
                alreadyAtFirstMatch = true;
                return currentDoc;
            }
            
            // No matches in this doc; on to the next
            currentDoc = left.nextDoc();
            if (currentDoc == NO_MORE_DOCS) {
                // We're out of docs.
                leftStart = rightEnd = NO_MORE_POSITIONS;
                return NO_MORE_DOCS;
            }
        }
    }

    /**
     * Restores the property that the current left match ends where the current
     * right matches begin.
     *
     * The spans are assumed to be already in the same doc. It is also assumed that 
     * the left clause has just been advanced to a new position.
     * 
     * We will start by seeing if the right bucket contains matches for this new left
     * clause span. If not, we will advance the left clause and repeat until we've
     * found a match, or are out of matches.
     * 
     * After this function, we're on the first valid match found, or we're out of
     * matches for this document.
     *
     * @throws IOException
     */
    private void realignPos() throws IOException {
        while (true) {
            if (leftStart == NO_MORE_POSITIONS) {
                rightEnd = NO_MORE_POSITIONS;
                return;
            }
            
            // Where should the right clause start?
            leftStartFirst = leftStart + gap.minSize();
            rightStartFirst = left.endPosition() + gap.minSize();
            rightStartLast = gap.maxSize() == MAX_UNLIMITED ? MAX_UNLIMITED : left.endPosition() + gap.maxSize();
            
            // Do we need to advance the starting point in the right bucket?
            // First, position indexInBucket according to leftStartFirst.
            // (because left.endPosition() is not guaranteed to always rise, but leftStart is)
            while (indexInBucket < right.bucketSize() && (indexInBucket < 0 || right.startPosition(indexInBucket) < leftStartFirst)) {
                indexInBucket++;
            }
            if (indexInBucket < right.bucketSize()) {
                // Found a valid position for indexInBucket.
                // Next, position indexInBucketLeftEnd according to rightStartFirst.
                // (This represents the actual first span in the bucket that can match our current left span)
                indexInBucketLeftEnd = indexInBucket;
                while (indexInBucketLeftEnd < right.bucketSize() && right.startPosition(indexInBucketLeftEnd) < rightStartFirst) {
                    indexInBucketLeftEnd++;
                }
                if (indexInBucketLeftEnd < right.bucketSize() && right.startPosition(indexInBucketLeftEnd) <= rightStartLast) {
                    // Found the first matching right span for this left span. Return the sequence span.
                    rightEnd = right.endPosition(indexInBucketLeftEnd);
                    return;
                }
            }
            
            // Advance the left clause.
            leftStart = left.nextStartPosition();
        }
    }

    @Override
    public int advance(int doc) throws IOException {
        alreadyAtFirstMatch = false;
        if (currentDoc != NO_MORE_DOCS) {
            currentDoc = left.advance(doc);
            if (currentDoc != NO_MORE_DOCS) {
                leftStart = -1;
                int rightDoc = right.advance(doc);
                if (rightDoc == NO_MORE_DOCS)
                    currentDoc = NO_MORE_DOCS;
                else {
                    right.nextBucket();
                    rightEnd = -1;
                    indexInBucket = 0;
                    indexInBucketLeftEnd = -1;
                    realignDoc();
                }
            }
        }
        return currentDoc;
    }

    /**
     * @return start of the current hit
     */
    @Override
    public int startPosition() {
        if (alreadyAtFirstMatch)
            return -1; // .nextStartPosition() not called yet
        return leftStart;
    }

    @Override
    public String toString() {
        return "SeqGap(" + left + ", " + gap + ", " + right + ")";
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        left.setHitQueryContext(context);
        right.setHitQueryContext(context);
    }

    @Override
    public void getCapturedGroups(Span[] capturedGroups) {
        if (!childClausesCaptureGroups)
            return;
        left.getCapturedGroups(capturedGroups);
        right.getCapturedGroups(indexInBucketLeftEnd, capturedGroups);
    }

    @Override
    public int width() {
        return left.width(); // should be + right.width(); but not implemented for now and we don't use .width()
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        left.collect(collector);
        //right.collect(collector); should probably be called as well, but not implemented, and not necessary for now
        // (we only use payloads in SpansTags)
    }

    @Override
    public float positionsCost() {
        return left.positionsCost();
    }

}
