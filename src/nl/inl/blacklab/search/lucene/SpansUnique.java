/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Collection;

import nl.inl.blacklab.search.Hit;

import org.apache.lucene.search.spans.Spans;

/**
 * Remove consecutive duplicate hits from a source spans.
 */
public class SpansUnique extends Spans {
	private Hit previousHit = null;

	private Spans src;

	private boolean more = true;

	private boolean nexted = false;

	public SpansUnique(Spans src) {
		this.src = src;
	}

	@Override
	public int doc() {
		return src.doc();
	}

	@Override
	public int start() {
		return src.start();
	}

	@Override
	public int end() {
		return src.end();
	}

	@Override
	public boolean next() throws IOException {
		if (!more)
			return false;
		do {
			if (nexted) {
				// Save previous hit
				previousHit = Hit.getHit(src);
			}
			more = src.next();
			nexted = true;
			if (!more)
				return false;
		} while (previousHit != null && previousHit.doc == src.doc()
				&& previousHit.start == src.start() && previousHit.end == src.end());
		return true;
	}

	@Override
	public boolean skipTo(int target) throws IOException {
		if (!more)
			return false;

		if (previousHit != null && target == src.doc()) {
			// We're already in the target doc. Just go to the next hit.
			return next();
		}

		// Just skip to the target doc
		more = src.skipTo(target);
		nexted = true;
		return more;
	}

	@Override
	public Collection<byte[]> getPayload() {
		// not used
		return null;
	}

	@Override
	public boolean isPayloadAvailable() {
		// not used
		return false;
	}

	@Override
	public String toString() {
		return "UniqueSpans(" + src.toString() + ")";
	}

}
