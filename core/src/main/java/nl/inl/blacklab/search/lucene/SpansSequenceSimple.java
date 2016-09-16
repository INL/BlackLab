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
 * Simple version of sequence Spans. Assumes that:
 * - right side is ordered by start point (this is true by default in Lucene)
 * - left side is ordered by end point (true if start point sorted and all
 *   hits are the same length)
 * - left side end points and right side start points are unique (true
 *   if all hits are the same length and there are no duplicate hits (which
 *   should be true in Lucene by default, tho not 100% sure))
 */
class SpansSequenceSimple extends BLSpans {
	private BLSpans left;

	private BLSpans right;

	boolean more = true;

	int currentDoc = -1;

	private boolean alreadyAtFirstMatch = false;

	private int leftStart = NO_MORE_POSITIONS;

	public SpansSequenceSimple(BLSpans leftClause, BLSpans rightClause) {
		left = leftClause;
		right = rightClause;
//		if (!right.hitsStartPointSorted())
//			throw new IllegalArgumentException("Right hits not start point sorted!");
//		if (!right.hitsHaveUniqueStart())
//			throw new IllegalArgumentException("Right hits don't have unique start points!");
//		if (!left.hitsEndPointSorted())
//			throw new IllegalArgumentException("Left hits not end point sorted!");
//		if (!left.hitsHaveUniqueEnd())
//			throw new IllegalArgumentException("Left hits don't have unique end points!");
	}

	@Override
	public int docID() {
		return currentDoc;
	}

	@Override
	public int endPosition() {
		if (alreadyAtFirstMatch)
			return -1; // .nextStartPosition() not called yet
		if (leftStart == NO_MORE_POSITIONS)
			return NO_MORE_POSITIONS;
		return right.endPosition();
	}

	@Override
	public int nextDoc() throws IOException {
		alreadyAtFirstMatch = false;
		if (currentDoc != NO_MORE_DOCS) {
			currentDoc = left.nextDoc();
			if (currentDoc != NO_MORE_DOCS) {
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

		if (currentDoc == NO_MORE_DOCS || leftStart == NO_MORE_POSITIONS)
			return NO_MORE_POSITIONS;

		leftStart = left.nextStartPosition();
		if (leftStart != NO_MORE_POSITIONS)
			realignPos();
		return leftStart;
	}

	/**
	 * Puts both spans in the next doc (possibly the current one)
	 * that has a match in it.
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
				}
			}

			// See if this doc has any matches
			leftStart = left.nextStartPosition();
			if (leftStart != NO_MORE_POSITIONS)
				leftStart = realignPos();
			if (leftStart != NO_MORE_POSITIONS) {
				// Reset the end point iterator (end points of right matches starting at this mid point)
				// and save current end position.
				alreadyAtFirstMatch = true;
				return leftStart;
			}

			// No matches in this doc; on to the next
			currentDoc = left.nextDoc();
			if (currentDoc == NO_MORE_DOCS)
				return NO_MORE_DOCS;
		}
	}

	private int realignPos() throws IOException {
		// Synchronize within doc
		int leftEnd = left.endPosition();
		int rightStart = right.startPosition();
		while (leftEnd != rightStart) {
			if (rightStart < leftEnd) {
				// Advance right if necessary
				while (rightStart < leftEnd) {
					rightStart = right.advanceStartPosition(leftEnd);
					if (rightStart == NO_MORE_POSITIONS) {
						leftStart = NO_MORE_POSITIONS;
						break;
					}
				}
			} else {
				// Advance left if necessary
				while (leftEnd < rightStart) {
					leftStart = left.nextStartPosition();
					leftEnd = left.endPosition();
					if (leftStart == NO_MORE_POSITIONS)
						break;
					leftEnd = left.endPosition();
				}
			}
		}
		return leftStart;
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
				else
					realignDoc();
			}
		}
		return currentDoc;
	}

	@Override
	public int startPosition() {
		if (alreadyAtFirstMatch)
			return -1; // .nextStartPosition() not called yet
		return leftStart;
	}

	@Override
	public String toString() {
		return "SpansSequenceSimple(" + left + ", " + right + ")";
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
		right.getCapturedGroups(capturedGroups);
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
