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

import nl.inl.blacklab.search.Span;

import org.apache.lucene.search.spans.Spans;

/**
 * Combines two Spans using AND. Note that this means that only matches with the same document id,
 * the same start and the same end positions will be kept.
 */
class SpansAnd extends BLSpans {
	/** The two sets of hits to combine */
	private BLSpans[] spans = new BLSpans[2];

	/** Do the Spans objects still point to valid hits? */
	private boolean stillValidSpans[] = new boolean[2];

	public SpansAnd(Spans leftClause, Spans rightClause) {
		spans[0] = BLSpansWrapper.optWrapSort(leftClause);
		spans[1] = BLSpansWrapper.optWrapSort(rightClause);
		stillValidSpans[1] = true;
		stillValidSpans[0] = true;
	}

	/**
	 * @return the Lucene document id of the current hit
	 */
	@Override
	public int doc() {
		return spans[0].doc();
	}

	/**
	 * @return end position of current hit
	 */
	@Override
	public int end() {
		return spans[0].end();
	}

	/**
	 * Go to next span.
	 *
	 * @return true if we're at the next span, false if we're done
	 * @throws IOException
	 */
	@Override
	public boolean next() throws IOException {
		// Waren we al klaar?
		if (!stillValidSpans[0] || !stillValidSpans[1])
			return false;

		// Draai beide Spans door
		stillValidSpans[0] = spans[0].next();
		stillValidSpans[1] = spans[1].next();

		return synchronize();

	}

	private boolean synchronize() throws IOException {
		// Synchronise spans
		boolean synched = false;
		while (!synched && stillValidSpans[0] && stillValidSpans[1]) {
			// Synch at document level
			if (spans[0].doc() != spans[1].doc()) {
				if (spans[0].doc() < spans[1].doc())
					synchDoc(0);
				else
					synchDoc(1);
				continue;
			}

			// Synch at match start level
			if (spans[0].start() != spans[1].start()) {
				if (spans[0].start() < spans[1].start())
					synchMatchStart(0);
				else
					synchMatchStart(1);
				continue; // restart synching at doc level
			}

			// Synch at match end level
			if (spans[0].end() != spans[1].end()) {
				if (spans[0].end() < spans[1].end())
					synchMatchEnd(0);
				else
					synchMatchEnd(1);
				continue;
			}

			synched = true;
		}

		// Are we done?
		if (!stillValidSpans[0] || !stillValidSpans[1]) {
			// Yes, one of the Spans was exhausted
			return false;
		}

		// No, we are synched on a new hit
		return true;
	}

	private void synchDoc(int laggingSpans) throws IOException {
		stillValidSpans[laggingSpans] = spans[laggingSpans].skipTo(spans[1 - laggingSpans].doc());
	}

	private void synchMatchStart(int laggingSpans) throws IOException {
		int doc = spans[laggingSpans].doc();
		int catchUpTo = spans[1 - laggingSpans].start();
		while (stillValidSpans[laggingSpans] && spans[laggingSpans].start() < catchUpTo && spans[laggingSpans].doc() == doc) {
			stillValidSpans[laggingSpans] = spans[laggingSpans].next();
		}
	}

	private void synchMatchEnd(int laggingSpans) throws IOException {
		int doc = spans[laggingSpans].doc();
		int start = spans[laggingSpans].start();
		int catchUpTo = spans[1 - laggingSpans].end();
		while (stillValidSpans[laggingSpans] && spans[laggingSpans].end() < catchUpTo && spans[laggingSpans].doc() == doc
				&& spans[laggingSpans].start() == start) {
			stillValidSpans[laggingSpans] = spans[laggingSpans].next();
		}
	}

	/**
	 * Skip to the specified document (or the first document after it containing hits)
	 *
	 * @param doc
	 *            the doc number to skip to (or past)
	 * @return true if we're still pointing to a valid hit, false if we're done
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
	 * @return start of current span
	 */
	@Override
	public int start() {
		return spans[0].start();
	}

	@Override
	public String toString() {
		return "AndSpans(" + spans[0] + ", " + spans[1] + ")";
	}

	@Override
	public boolean hitsAllSameLength() {
		return spans[0].hitsAllSameLength() || spans[1].hitsAllSameLength();
	}

	@Override
	public int hitsLength() {
		if (spans[0].hitsAllSameLength())
			return spans[0].hitsLength();
		return spans[1].hitsLength();
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return spans[0].hitsHaveUniqueStart() || spans[1].hitsHaveUniqueStart();
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return spans[0].hitsHaveUniqueEnd() || spans[1].hitsHaveUniqueEnd();
	}

	@Override
	public boolean hitsAreUnique() {
		return spans[0].hitsAreUnique() || spans[1].hitsAreUnique();
	}

	@Override
	public void setHitQueryContext(HitQueryContext context) {
		spans[0].setHitQueryContext(context);
		spans[1].setHitQueryContext(context);
	}

	@Override
	public void getCapturedGroups(Span[] capturedGroups) {
		spans[0].getCapturedGroups(capturedGroups);
		spans[1].getCapturedGroups(capturedGroups);
	}

}
