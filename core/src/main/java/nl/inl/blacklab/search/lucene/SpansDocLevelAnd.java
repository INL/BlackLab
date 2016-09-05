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
import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.search.Span;

/**
 * "AND"-combination of two Spans objects.
 *
 * Behave as a boolean AND at the document level and as a boolean
 * OR within each document.
 *
 * (NOTE: we don't use SpanNearQuery with "infinite" slop because
 *  we need access to the sub-spans for capture group functionality)
 */
public class SpansDocLevelAnd extends BLSpans {

	/** Index of spans object that contains the current hit */
	private int currentSpansIndex;

	/** The spans objects we're producing hits from */
	private BLSpans[] spans;

	/** What doc is span in? */
	private int currentDoc[] = new int[2];

	/** What start pos is span at? */
	private int currentStart[] = new int[2];

	public SpansDocLevelAnd(Spans leftClause, Spans rightClause) {
		spans = new BLSpans[2];
		spans[0] = BLSpansWrapper.optWrapSort(leftClause);
		spans[1] = BLSpansWrapper.optWrapSort(rightClause);
		currentDoc[0] = currentDoc[1] = -1;
		currentStart[0] = currentStart[1] = -1;
		currentSpansIndex = 0;
	}

	@Override
	public int docID() {
		return currentDoc[0];
	}

	@Override
	public int endPosition() {
		return spans[currentSpansIndex].endPosition();
	}

	@Override
	public int nextDoc() throws IOException {
		// Both spans are in the same doc. Advance both now and re-synchronize.
		currentDoc[0] = spans[0].nextDoc();
		currentDoc[1] = spans[1].nextDoc();
		return synchronize();
	}

	@Override
	public int nextStartPosition() throws IOException {
		if (currentDoc[currentSpansIndex] == NO_MORE_DOCS)
			return NO_MORE_POSITIONS;

		// Advance the spans from which the last hit was produced,
		// so that both spans[0] and spans[1] point to a 'fresh' hit.
		// (Of course one or both might become depleted at some point;
		// we keep track of this in validspans[])

		currentStart[currentSpansIndex] = spans[currentSpansIndex].nextStartPosition();

		return determineCurrentHit();
	}

	@Override
	public int advanceStartPosition(int target) throws IOException {
		for (int i = 0; i < 2; i++) {
			currentStart[i] = spans[i].advanceStartPosition(target);
		}
		return determineCurrentHit();
	}

	private int determineCurrentHit() {
		// Are we done with the current doc?
		if (currentStart[0] == NO_MORE_POSITIONS && currentStart[1] == NO_MORE_POSITIONS) {
			return NO_MORE_POSITIONS;
		}

		if (currentStart[0] != NO_MORE_POSITIONS && currentStart[1] != NO_MORE_POSITIONS) {
			// Two spans to choose from; choose the hit occurring first in the document.
			// (if equal starts, choose the one that ends first)
			if (spans[0].startPosition() == spans[1].startPosition()) {
				currentSpansIndex = spans[0].endPosition() < spans[1].endPosition() ? 0 : 1;
			} else {
				currentSpansIndex = currentStart[0] < currentStart[1] ? 0 : 1;
			}
		} else if (currentStart[0] != NO_MORE_POSITIONS) {
			// Only spans[0] still has hits in the current document.
			currentSpansIndex = 0;
		} else if (currentStart[1] != NO_MORE_POSITIONS) {
			// Only spans[1] still has hits in the current document.
			currentSpansIndex = 1;
		} else {
			// not possible (checks at the top of the method make this impossible)
			throw new RuntimeException();
		}

		// Found new hit
		return currentStart[currentSpansIndex];
	}

	/**
	 * If spans[0] and spans[1] are at different document ids, this method will
	 * advance them until they're in the same document again.
	 *
	 * @return document id if a next document has been found, NO_MORE_DOCS if we're done
	 * @throws IOException
	 */
	private int synchronize() throws IOException {
		// Were we done already?
		if (currentDoc[0] == NO_MORE_DOCS || currentDoc[1] == NO_MORE_DOCS) {
			// Yes
			return NO_MORE_DOCS;
		}

		// Loop until we match up spans[0] and spans[1]
		while (currentDoc[0] != currentDoc[1]) {
			// Which of the two should we advance?
			if (currentDoc[0] < currentDoc[1]) {
				// spans[0] is behind spans[1]; skip spans[0] to spans[1]'s position
				currentDoc[0] = spans[0].advance(currentDoc[1]);
				if (currentDoc[0] == NO_MORE_DOCS) {
					// spans[0] is depleted; we're done
					return NO_MORE_DOCS;
				}
			} else {
				// spans[1] is behind spans[0]; skip spans[1] to spans[0]'s position
				currentDoc[1] = spans[1].advance(currentDoc[0]);
				if (currentDoc[1] == NO_MORE_DOCS) {
					// spans[1] is depleted; we're done
					return NO_MORE_DOCS;
				}
			}
		}
		return currentDoc[0];
	}

	/**
	 * Go to the specified document, if it contains hits. If not, go to the first document
	 * after that containing hits.
	 *
	 * @param doc
	 *            document id to skip to (or over)
	 * @return true if we're on a valid hit, false if we're done
	 * @throws IOException
	 */
	@Override
	public int advance(int doc) throws IOException {
		// Skip beiden tot aan doc
		currentDoc[0] = spans[0].advance(doc);
		currentDoc[1] = spans[1].advance(doc);
		return synchronize();
	}

	/**
	 * @return start of current hit
	 */
	@Override
	public int startPosition() {
		return currentStart[currentSpansIndex];
	}

	@Override
	public String toString() {
		return "SpansDocLevelAnd(" + spans[0] + ", " + spans[1] + ")";
	}

	@Override
	public boolean hitsEndPointSorted() {
		return hitsAllSameLength();
	}

	@Override
	public boolean hitsStartPointSorted() {
		return true;
	}

	@Override
	public boolean hitsAllSameLength() {
		return spans[0].hitsAllSameLength() && spans[1].hitsAllSameLength() && spans[0].hitsLength() == spans[1].hitsLength();
	}

	@Override
	public int hitsLength() {
		if (hitsAllSameLength())
			return spans[0].hitsLength();
		return -1;
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return false;
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return false;
	}

	@Override
	public boolean hitsAreUnique() {
		return false;
	}

	@Override
	public void passHitQueryContextToClauses(HitQueryContext context) {
		spans[0].setHitQueryContext(context);
		spans[1].setHitQueryContext(context);
	}

	@Override
	public void getCapturedGroups(Span[] capturedGroups) {
		if (!childClausesCaptureGroups)
			return;
		spans[currentSpansIndex].getCapturedGroups(capturedGroups);
	}

	@Override
	public int width() {
		return spans[currentSpansIndex].width();
	}

	@Override
	public void collect(SpanCollector collector) throws IOException {
		spans[currentSpansIndex].collect(collector);
	}

}
