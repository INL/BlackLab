/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.sequences;

import java.io.IOException;

import nl.inl.blacklab.search.Hit;

import org.apache.lucene.search.spans.Spans;

/**
 * Wrap a Spans to retrieve consecutive matches.
 *
 * This is used for repetition regex operators such as * and +.
 */
class SpansInBucketsConsecutive extends SpansInBucketsAbstract {
	public SpansInBucketsConsecutive(Spans source) {
		super(source);
	}

	@Override
	protected void gatherHits() throws IOException {
		int lastEnd = source.start();
		while (more && source.doc() == currentDoc && source.start() == lastEnd) {
			hits.add(new Hit(source.doc(), source.start(), source.end()));
			lastEnd = source.end();
			more = source.next();
		}
	}
}
