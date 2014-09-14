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
import nl.inl.blacklab.search.lucene.HitQueryContext;

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

	public SpansSequenceSimple(BLSpans leftClause, BLSpans rightClause) {
		left = leftClause;
		right = rightClause;
		if (!right.hitsStartPointSorted())
			throw new RuntimeException("Right hits not start point sorted!");
		if (!right.hitsHaveUniqueStart())
			throw new RuntimeException("Right hits don't have unique start points!");
		if (!left.hitsEndPointSorted())
			throw new RuntimeException("Left hits not end point sorted!");
		if (!left.hitsHaveUniqueEnd())
			throw new RuntimeException("Left hits don't have unique end points!");
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
		return right.end();
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

		more = left.next();
		if (more)
			more = right.next();
		if (!more)
			return false;
		return realign();
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
			while (left.doc() == right.doc() && left.end() != right.start()) {
				if (right.start() < left.end()) {
					// Advance right if necessary
					while (left.doc() == right.doc() && right.start() < left.end()) {
						more = right.next();
						if (!more)
							return false;
					}
				} else {
					// Advance left if necessary
					while (left.doc() == right.doc() && left.end() < right.start()) {
						more = left.next();
						if (!more)
							return false;
					}
				}
			}
		} while (left.doc() != right.doc()); // Repeat until left and right align.

		// Found a sequence hit
		currentDoc = right.doc();
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
		return left.start();
	}

	@Override
	public String toString() {
		return "SpansSequenceSimple(" + left + ", " + right + ")";
	}

	@Override
	public boolean hitsEndPointSorted() {
		return right.hitsEndPointSorted();
	}

	@Override
	public boolean hitsStartPointSorted() {
		return left.hitsStartPointSorted();
	}

	@Override
	public boolean hitsAllSameLength() {
		return left.hitsAllSameLength() && right.hitsAllSameLength();
	}

	@Override
	public int hitsLength() {
		return hitsAllSameLength() ? left.hitsLength() + right.hitsLength() : -1;
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return left.hitsHaveUniqueStart();
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return right.hitsHaveUniqueEnd();
	}

	@Override
	public boolean hitsAreUnique() {
		return hitsHaveUniqueStart() || hitsHaveUniqueEnd();
	}

	@Override
	public void setHitQueryContext(HitQueryContext context) {
		left.setHitQueryContext(context);
		right.setHitQueryContext(context);
	}

	@Override
	public void getCapturedGroups(Span[] capturedGroups) {
		left.getCapturedGroups(capturedGroups);
		right.getCapturedGroups(capturedGroups);
	}

}
