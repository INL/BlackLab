/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Collection;

import org.apache.lucene.search.spans.Spans;

/**
 * Gets spans for a certain XML element.
 */
class SpansTags extends Spans {
	/** The two sets of hits to combine */
	private Spans[] spans = new Spans[2];

	/** Do the Spans objects still point to valid hits? */
	private boolean stillValidSpans[] = new boolean[2];

	public SpansTags(Spans startTags, Spans endTags) {
		spans[0] = startTags;
		spans[1] = endTags;
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
		return spans[1].end() - 1;
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

		if (!stillValidSpans[0] || !stillValidSpans[1])
			return false;

		if (spans[0].doc() != spans[1].doc())
			throw new RuntimeException(
					"Error, start and end tags not in synch (start and end Spans ended up inside different documents)");

		return stillValidSpans[0] && stillValidSpans[1];
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
		return stillValidSpans[0] && stillValidSpans[1];
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
		return "SpansTags(" + spans[0] + ", " + spans[1] + ")";
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
