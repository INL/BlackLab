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
package nl.inl.blacklab.search.sequences;

import java.io.IOException;

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.BLSpansWrapper;
import nl.inl.blacklab.search.lucene.HitQueryContext;

import org.apache.lucene.search.spans.Spans;

/**
 * Combines spans, keeping only combinations of hits that occur one after the other. The order is
 * significant: a hit from the first span must be followed by a hit from the second.
 *
 * This is a fairly involved process.
 *
 * The Spans for the left clause is sorted by hit end point instead of by hit start point, because
 * this is necessary for efficient sequential hit generation.
 *
 * The Spans for the right clause is wrapped in EndPointsPerStartPoint because we need to combine
 * all left hits with end point X with all right hits with start point X. Note that this Spans
 * should already be start point sorted, but this is the default in Lucene.
 *
 * It has to take the following problem into account, which might arise with more complex sequences
 * with overlapping hits ("1234" are token positions in the document, A-C are hits in spans1, D-F
 * are hits in spans2, "AD", "AE" and "BF" are resulting sequence hits):
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
 * Note that the sequence of the two spans contains duplicates (AD and BF are identical) and
 * out-of-order endpoints (AE ends at 5 but BF ends at 4). Both are undesirable; the user doesn't
 * want to see duplicates, and out-of-order endpoints may cause problems when combining this spans
 * with other spans (although that is not certain; should be checked).
 *
 * Therefore, objects of this class should be wrapped in a class that sort the matches per document
 * and eliminates duplicates.
 */
class SpansSequenceRaw extends BLSpans {
	private BLSpans left;

	private BLSpans origRight;

	private SpansInBuckets right;

	int indexInBucket = -2; // -2 == not started yet; -1 == just started a bucket

	int leftStart = -1;

	int rightEnd = -1;

	boolean more = true;

	int currentDoc = -1;

	public SpansSequenceRaw(Spans leftClause, Spans rightClause) {
		// Sort the left spans by (1) document (2) end point (3) start point
		left = new PerDocumentSortedSpans(leftClause, true, false);

		// From the right spans, let us extract all end points belonging with a start point.
		// Already start point sorted.
		origRight = BLSpansWrapper.optWrap(rightClause);
		right = new SpansInBucketsPerStartPoint(origRight);
	}

	/**
	 * @return the Lucene document id of the current hit
	 */
	@Override
	public int doc() {
		return currentDoc;
	}

	/**
	 * @return end position of current hit
	 */
	@Override
	public int end() {
		return rightEnd;
	}

	/**
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
	 *
	 * @return true if we're on a valid match, false if we're done.
	 * @throws IOException
	 */
	@Override
	public boolean next() throws IOException {
		if (!more)
			return false;

		if (indexInBucket == -2 || indexInBucket == right.bucketSize() - 1) {
			/*
			 * We're out of end points (right matches). Advance the left Spans and realign both
			 * spans to the mid point.
			 */
			more = left.next();
			if (!more)
				return false;
			return realign();
		}

		indexInBucket++;
		rightEnd = right.end(indexInBucket);
		return true;
	}

	/**
	 * Restores the property that the current left match ends where the current right matches begin.
	 *
	 * This is called whenever the left and right spans may be out of alignment (after left has been
	 * advanced in next(), or after skipTo() has been called)
	 *
	 * If they're already aligned, this function does nothing. If they're out of alignment (that is,
	 * left.end() != right.start()), advance the spans that is lagging. Repeat until they are
	 * aligned, or one of the spans run out.
	 *
	 * After this function, we're on the first valid match found.
	 *
	 * @return true if we're on a valid match, false if we're done.
	 * @throws IOException
	 */
	private boolean realign() throws IOException {
		do {
			// Put in same doc if necessary
			while (left.doc() != right.doc()) {
				while (left.doc() < right.doc()) {
					more = left.skipTo(right.doc());
					if (!more)
						return false;
				}
				while (right.doc() < left.doc()) {
					more = right.skipTo(left.doc());
					if (!more)
						return false;
				}
			}

			// Synchronize within doc
			int rightStart = right.start(0);
			while (left.doc() == right.doc() && left.end() != rightStart) {
				if (rightStart < left.end()) {
					// Advance right if necessary
					while (left.doc() == right.doc() && rightStart < left.end()) {
						more = right.next();
						rightStart = right.start(0);
						if (!more)
							return false;
					}
				} else {
					// Advance left if necessary
					while (left.doc() == right.doc() && left.end() < rightStart) {
						more = left.next();
						if (!more)
							return false;
					}
				}
			}
		} while (left.doc() != right.doc()); // Repeat until left and right align.

		// Save doc, start and end positions.
		// Reset the end point iterator (end points of right matches starting at this mid point)
		currentDoc = right.doc();
		leftStart = left.start();
		indexInBucket = 0;
		rightEnd = right.end(indexInBucket);
		return true;
	}

	/**
	 * Go to the specified document, if it has hits. If not, go to the next document containing
	 * hits.
	 *
	 * @param doc
	 *            the document number to skip to / over
	 * @return true if we're at a valid hit, false if not
	 * @throws IOException
	 */
	@Override
	public boolean skipTo(int doc) throws IOException {
		if (!more)
			return false;
		more = left.skipTo(doc);
		if (!more)
			return false;
		more = right.skipTo(doc);
		if (!more)
			return false;

		return realign();
	}

	/**
	 * @return start of the current hit
	 */
	@Override
	public int start() {
		return leftStart;
	}

	@Override
	public String toString() {
		return "SpansSequence(" + left + ", " + right + ")";
	}

	@Override
	public boolean hitsEndPointSorted() {
		return origRight.hitsAllSameLength();
	}

	@Override
	public boolean hitsStartPointSorted() {
		return left.hitsAllSameLength();
	}

	@Override
	public boolean hitsAllSameLength() {
		return left.hitsAllSameLength() && origRight.hitsAllSameLength();
	}

	@Override
	public int hitsLength() {
		return hitsAllSameLength() ? left.hitsLength() + origRight.hitsLength() : -1;
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return left.hitsHaveUniqueStart() && origRight.hitsHaveUniqueStart();
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return left.hitsHaveUniqueEnd() && origRight.hitsHaveUniqueEnd();
	}

	@Override
	public boolean hitsAreUnique() {
		return hitsHaveUniqueStart() || hitsHaveUniqueEnd();
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

}
