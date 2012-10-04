/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Collection;

import org.apache.lucene.search.spans.Spans;

/**
 * Combines two Spans using AND. Note that this means that only matches with the same document id,
 * the same start and the same end positions will be kept.
 */
class SpansAnd extends Spans {
	/** The two sets of hits to combine */
	private Spans[] spans = new Spans[2];

	/** Do the Spans objects still point to valid hits? */
	private boolean stillValidSpans[] = new boolean[2];

	public SpansAnd(Spans leftClause, Spans rightClause) {
		spans[0] = leftClause;
		spans[1] = rightClause;
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
		// Synchroniseer de spans
		boolean synched = false;
		while (!synched && stillValidSpans[0] && stillValidSpans[1]) {
			// Synch op documentnivo
			if (spans[0].doc() != spans[1].doc()) {
				if (spans[0].doc() < spans[1].doc())
					synchDoc(0);
				else
					synchDoc(1);
				continue;
			}

			// Synch op match start nivo
			if (spans[0].start() != spans[1].start()) {
				if (spans[0].start() < spans[1].start())
					synchMatchStart(0);
				else
					synchMatchStart(1);
				continue;
			}

			// Synch op match end nivo
			if (spans[0].end() != spans[1].end()) {
				if (spans[0].end() < spans[1].end())
					synchMatchEnd(0);
				else
					synchMatchEnd(1);
				continue;
			}

			synched = true;
		}

		// Zijn we nu klaar?
		if (!stillValidSpans[0] || !stillValidSpans[1]) {
			// Ja
			return false;
		}

		// Nee, match gevonden.
		return true;
	}

	private void synchDoc(int laggingSpans) throws IOException {
		stillValidSpans[laggingSpans] = spans[laggingSpans].skipTo(spans[1 - laggingSpans].doc());
	}

	private void synchMatchStart(int laggingSpans) throws IOException {
		int i = laggingSpans;
		int doc = spans[i].doc();
		int catchUpTo = spans[1 - i].start();
		while (stillValidSpans[i] && spans[i].start() < catchUpTo && spans[i].doc() == doc) {
			stillValidSpans[i] = spans[i].next();
		}
	}

	private void synchMatchEnd(int laggingSpans) throws IOException {
		int i = laggingSpans;
		int doc = spans[i].doc();
		int start = spans[i].start();
		int catchUpTo = spans[1 - i].end();
		while (stillValidSpans[i] && spans[i].end() < catchUpTo && spans[i].doc() == doc
				&& spans[i].start() == start) {
			stillValidSpans[i] = spans[i].next();
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
	public Collection<byte[]> getPayload() {
		return null;
	}

	@Override
	public boolean isPayloadAvailable() {
		return false;
	}

}
