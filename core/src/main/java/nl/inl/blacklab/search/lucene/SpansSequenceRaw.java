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
 * This is a fairly involved process.
 *
 * The Spans for the left clause is sorted by hit end point instead of by hit
 * start point, because this is necessary for efficient sequential hit
 * generation.
 *
 * The Spans for the right clause is wrapped in EndPointsPerStartPoint because
 * we need to combine all left hits with end point X with all right hits with
 * start point X. Note that this Spans should already be start point sorted, but
 * this is the default in Lucene.
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
class SpansSequenceRaw extends BLSpans {
    private BLSpans left;

    private BLSpans origRight;

    private SpansInBucketsPerStartPoint right;

    int indexInBucket = -2; // -2 == not started yet; -1 == just started a bucket

    int currentDoc = -1;

    int leftStart = -1;

    int rightEnd = -1;

    /**
     * Are we already a the first match in the document, even if
     * .nextStartPosition() hasn't been called? Required because we need to test for
     * matches in the document in .nextDoc()/.advance().
     */
    private boolean alreadyAtFirstMatch = false;

    /**
     * Construct SpansSequenceRaw.
     *
     * @param leftClause (endpoint-sorted) left clause
     * @param rightClause (startpoint-sorted) right clause
     */
    public SpansSequenceRaw(BLSpans leftClause, BLSpans rightClause) {
        // Sort the left spans by (1) document (2) end point (3) start point
        left = leftClause; //new PerDocumentSortedSpans(leftClause, PerDocumentSortedSpans.cmpEndPoint, false);

        // From the right spans, let us extract all end points belonging with a start point.
        // Already start point sorted.
        origRight = rightClause; //Sort
        right = new SpansInBucketsPerStartPoint(origRight);
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
            if (currentDoc != NO_MORE_DOCS) {
                right.nextDoc();
                rightEnd = -1;
                indexInBucket = -2;
                realignDoc();
            }
        }
        return currentDoc;
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (alreadyAtFirstMatch) {
            alreadyAtFirstMatch = false;
            return leftStart;
        }

        /*
         * Go to the next match.
         *
         * This is done around the 'mid point', the word position where the left match ends and the
         * right match begins.
         *
         * The left Spans are sorted by end point. The matches from this Spans are iterated through, and
         * for each match, the end point will be the 'mid point' of the resulting match. Note that there
         * may be multiple matches from the left with the same end point.
         *
         * The right Spans are sorted by start point (no sorting required, as this is Lucene's default).
         * For each 'mid point', all matches starting at that point are collected from the right spans.
         *
         * Each match from the left is then combined with all the collected matches from the right. The
         * collected matches from the right may be used for multiple matches from the left (if there are
         * multiple matches from the left with the same end point).
         */

        if (currentDoc == NO_MORE_DOCS || leftStart == NO_MORE_POSITIONS) {
            leftStart = rightEnd = NO_MORE_POSITIONS;
            return NO_MORE_POSITIONS;
        }

        if (indexInBucket == -2 || indexInBucket == right.bucketSize() - 1) {
            // We're out of end points (right matches). Advance the left Spans and realign both
            // spans to the mid point.
            leftStart = left.nextStartPosition();
            if (leftStart == NO_MORE_POSITIONS) {
                rightEnd = NO_MORE_POSITIONS;
            } else {
                if (right.bucketSize() > 0 && left.endPosition() > right.startPosition(0)) {
                    // We have a new left end position, so need a new bucket
                    if (right.advanceBucket(left.endPosition()) == SpansInBuckets.NO_MORE_BUCKETS)
                        leftStart = rightEnd = NO_MORE_POSITIONS;
                } else {
                    // Same left end position, so reuse this bucket
                    // (nothing to do)
                }
                if (leftStart != NO_MORE_POSITIONS) {
                    // Reset the bucket and realign.
                    rightEnd = -1;
                    indexInBucket = -1;
                    realignPos();
                }
            }
        } else {
            // Go to the next end point
            indexInBucket++;
            rightEnd = right.endPosition(indexInBucket);
        }
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
                    if (currentDoc == NO_MORE_DOCS)
                        return NO_MORE_DOCS;
                }
                while (right.docID() < currentDoc) {
                    int rightDoc = right.advance(currentDoc);
                    if (rightDoc == NO_MORE_DOCS) {
                        currentDoc = NO_MORE_DOCS;
                        return NO_MORE_DOCS;
                    }
                    rightEnd = -1;
                    indexInBucket = -2;
                }
            }

            // See if this doc has any matches
            leftStart = left.nextStartPosition();
            if (leftStart != NO_MORE_POSITIONS) {
                if (right.nextBucket() == SpansInBuckets.NO_MORE_BUCKETS)
                    leftStart = rightEnd = NO_MORE_POSITIONS;
                else {
                    rightEnd = -1;
                    indexInBucket = -1;
                    realignPos();
                }
            }
            if (leftStart == NO_MORE_POSITIONS) {
                rightEnd = NO_MORE_POSITIONS;
            } else {
                // Reset the end point iterator (end points of right matches starting at this mid point)
                // and save current end position.
                alreadyAtFirstMatch = true;
                return currentDoc;
            }

            // No matches in this doc; on to the next
            currentDoc = left.nextDoc();
            if (currentDoc == NO_MORE_DOCS) {
                leftStart = rightEnd = NO_MORE_POSITIONS;
                return NO_MORE_DOCS;
            }
        }
    }

    /**
     * Restores the property that the current left match ends where the current
     * right matches begin.
     *
     * The spans are assumed to be already in the same doc.
     *
     * If they're already aligned, this function does nothing. If they're out of
     * alignment (that is, left.end() != right.start()), advance the spans that is
     * lagging. Repeat until they are aligned, or one of the spans run out.
     *
     * After this function, we're on the first valid match found, or we're out of
     * matches for this document.
     *
     * @throws IOException
     */
    private void realignPos() throws IOException {
        // Synchronize within doc
        int leftEnd = left.endPosition();
        int rightStart = right.startPosition(0);
        rightEnd = right.endPosition(0);
        indexInBucket = 0;
        while (leftEnd != rightStart) {
            if (rightStart < leftEnd) {
                // Advance right if necessary
                while (rightStart < leftEnd) {
                    int rightDoc = right.advanceBucket(leftEnd);
                    if (rightDoc == SpansInBuckets.NO_MORE_BUCKETS) {
                        leftStart = rightEnd = NO_MORE_POSITIONS;
                        return;
                    }
                    rightStart = right.startPosition(0);
                    rightEnd = right.endPosition(0);
                    indexInBucket = 0;
                }
            } else {
                // Advance left if necessary
                while (leftEnd < rightStart) {
                    leftStart = left.nextStartPosition();
                    leftEnd = left.endPosition();
                    if (leftStart == NO_MORE_POSITIONS) {
                        rightEnd = NO_MORE_POSITIONS;
                        return;
                    }
                }
            }
        }
    }

    @Override
    public int advance(int doc) throws IOException {
        alreadyAtFirstMatch = false;
        if (currentDoc != NO_MORE_DOCS) {
            currentDoc = left.advance(doc);
            if (currentDoc != NO_MORE_DOCS) {
                int rightDoc = right.advance(doc);
                if (rightDoc == NO_MORE_DOCS)
                    currentDoc = NO_MORE_DOCS;
                else {
                    rightEnd = -1;
                    indexInBucket = -2;
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
        return "Seq(" + left + ", " + right + ")";
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
        right.getCapturedGroups(indexInBucket, capturedGroups);
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
