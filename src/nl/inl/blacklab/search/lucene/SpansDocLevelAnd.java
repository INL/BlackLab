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
import java.util.Collection;

import org.apache.lucene.search.spans.Spans;

/**
 * "AND"-combination of two Spans objects.
 *
 * Behave as a boolean AND at the document level and as a boolean
 * OR within each document.
 */
public class SpansDocLevelAnd extends Spans {
	/** Current document id */
	private int currentDocId;

	/** Index of spans object that contains the current hit */
	private int currentSpansIndex;

	/** The spans objects we're producing hits from */
	private Spans[] spans;

	/** Did we go past the last hit? */
	private boolean stillValidSpans[];

	/** spans[1].next() should be called right away. If this is true, that has been done. */
	private boolean spans1nexted;

	public SpansDocLevelAnd(Spans leftClause, Spans rightClause) {
		spans = new Spans[2];
		spans[0] = leftClause;
		spans[1] = rightClause;
		currentDocId = -1; // no current document yet
		stillValidSpans = new boolean[2];
		spans1nexted = false; // spans[1] will be placed on the first hit right away
		stillValidSpans[1] = true;
		stillValidSpans[0] = true;
		currentSpansIndex = 0;
	}

	/**
	 * Returns document id of current hit
	 *
	 * @return current document id
	 */
	@Override
	public int doc() {
		return spans[currentSpansIndex].doc();
	}

	/**
	 * Checks if the specified spans still points to the current document, or if it points to a
	 * different document (or is depleted).
	 *
	 * @param spansNumber
	 *            index in the spans[] array
	 * @return true if this spans still points to current document, false if not
	 */
	private boolean doesSpansPointToCurrentDoc(int spansNumber) {
		return stillValidSpans[spansNumber] && spans[spansNumber].doc() == currentDocId;
	}

	/**
	 * @return end of current hit
	 */
	@Override
	public int end() {
		return spans[currentSpansIndex].end();
	}

	/**
	 * Go to next hit.
	 *
	 * @return true if we're on a valid hit, false if we're done.
	 * @throws IOException
	 */
	@Override
	public boolean next() throws IOException {
		// We must do this right away, but not in the constructor because of exceptions
		if (!spans1nexted) {
			spans1nexted = true;
			stillValidSpans[1] = spans[1].next();
		}

		// Advance the spans from which the last hit was produced,
		// so that both spans[0] and spans[1] point to a 'fresh' hit.
		// (Of course one or both might become depleted at some point;
		// we keep track of this in validspans[])

		stillValidSpans[currentSpansIndex] = spans[currentSpansIndex].next();

		// If we didn't have a current document yet, or we're done with the current doc:
		// advance to next document
		if (currentDocId == -1
				|| ((!stillValidSpans[0] || spans[0].doc() != currentDocId) && (!stillValidSpans[1] || spans[1]
						.doc() != currentDocId))) {
			boolean ok = synchronize();
			if (!ok)
				return false;
		}

		// When we arrive here, we know we have a valid document.
		// It's still possible that one of the spans is depleted while the other one still has
		// hits in it.

		boolean spansPointsToCurrentDoc[] = new boolean[2];
		spansPointsToCurrentDoc[0] = doesSpansPointToCurrentDoc(0);
		spansPointsToCurrentDoc[1] = doesSpansPointToCurrentDoc(1);
		if (spansPointsToCurrentDoc[0] && spansPointsToCurrentDoc[1]) {
			// Two spans to choose from; choose the hit occurring first in the document.
			if (spans[0].start() < spans[1].start())
				currentSpansIndex = 0;
			else
				currentSpansIndex = 1;
		} else if (spansPointsToCurrentDoc[0]) {
			// Only spans[0] still has hits in the current document.
			currentSpansIndex = 0;
		} else if (spansPointsToCurrentDoc[1]) {
			// Only spans[1] still has hits in the current document.
			currentSpansIndex = 1;
		} else {
			// not possible (checks at the top of the method make this impossible,
			// we would be in a new document or be all done)
			assert (false);
		}

		// Found new hit
		return true;
	}

	/**
	 * If spans[0] and spans[1] are at different document ids, this method will
	 * advance them until they're in the same document again.
	 *
	 * @return true if a next document has been found, false if we're done
	 */
	private boolean synchronize() throws IOException {
		// Were we done already?
		if (!stillValidSpans[0] || !stillValidSpans[1]) {
			// Yes
			return false;
		}

		// Loop until we match up spans[0] and spans[1]
		int doc1, doc2;
		doc1 = spans[0].doc();
		doc2 = spans[1].doc();
		while (doc1 != doc2) {
			// Which of the two should we advance?
			if (doc1 < doc2) {
				// spans[0] is behind spans[1]; skip spans[0] to spans[1]'s position
				stillValidSpans[0] = spans[0].skipTo(doc2);
				if (!stillValidSpans[0]) {
					// spans[0] is depleted; we're done
					return false;
				}
				doc1 = spans[0].doc();
			} else {
				// spans[1] is behind spans[0]; skip spans[1] to spans[0]'s position
				stillValidSpans[1] = spans[1].skipTo(doc1);
				if (!stillValidSpans[1]) {
					// spans[1] is depleted; we're done
					return false;
				}
				doc2 = spans[1].doc();
			}
		}

		// Found a match; this is new current doc.
		// spans[0] and spans[1] are at their first hits in the document.
		currentDocId = doc1;
		return true;
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
	public boolean skipTo(int doc) throws IOException {
		// Skip beiden tot aan doc
		stillValidSpans[0] = spans[0].skipTo(doc);
		stillValidSpans[1] = spans[1].skipTo(doc);
		return synchronize();
	}

	/**
	 * @return start of current hit
	 */
	@Override
	public int start() {
		return spans[currentSpansIndex].start();
	}

	@Override
	public String toString() {
		return "SpansDocLevelAnd(" + spans[0] + ", " + spans[1] + ")";
	}

	@Override
	public Collection<byte[]> getPayload() {
		return null;
	}

	@Override
	public boolean isPayloadAvailable() {
		return false;
	}

}
