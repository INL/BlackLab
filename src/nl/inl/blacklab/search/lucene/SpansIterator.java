/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Iterator;

import nl.inl.blacklab.search.Hit;

import org.apache.lucene.search.spans.Spans;

/**
 * Iterate over a Spans object, yielding Hit objects.
 */
class SpansIterator implements Iterator<Hit> {
	Hit lookAhead = null;

	boolean more;

	private Spans spans;

	public SpansIterator(Spans spans) {
		this.spans = spans;
		try {
			more = spans.next();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		if (more)
			lookAhead = Hit.getHit(spans);
	}

	@Override
	public boolean hasNext() {
		return more;
	}

	@Override
	public Hit next() {
		Hit rv = lookAhead;
		try {
			more = spans.next();
			if (more)
				lookAhead = Hit.getHit(spans);
			else
				lookAhead = null;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return rv;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

}