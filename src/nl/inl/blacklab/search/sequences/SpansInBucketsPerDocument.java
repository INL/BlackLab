/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.sequences;

import java.io.IOException;

import nl.inl.blacklab.search.Hit;

import org.apache.lucene.search.spans.Spans;

/**
 * Wrap a Spans to retrieve matches per document, so we can process all matches in a document
 * efficiently.
 *
 * This way we can retrieve hits per document and perform some operation on them (like sorting or
 * retrieving some extra information). Afterwards we can use HitsPerDocumentSpans to convert the
 * per-document hits into a normal Spans object again.
 */
public class SpansInBucketsPerDocument extends SpansInBucketsAbstract {
	public SpansInBucketsPerDocument(Spans source) {
		super(source);
	}

	@Override
	protected void gatherHits() throws IOException {
		while (more && source.doc() == currentDoc) {
			hits.add(new Hit(source.doc(), source.start(), source.end()));
			more = source.next();
		}
	}
}
