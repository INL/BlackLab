/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;

/**
 * Makes sure the resulting hits do not contain consecutive duplicate hits. These may arise when
 * e.g. combining multiple SpanFuzzyQueries with OR.
 */
public class SpanQueryUnique extends SpanQuery {
	private SpanQuery src;

	public SpanQueryUnique(SpanQuery src) {
		this.src = src;
	}

	@Override
	public Spans getSpans(IndexReader reader) throws IOException {
		Spans srcSpans = src.getSpans(reader);
		return new SpansUnique(srcSpans);
	}

	@Override
	public String toString(String field) {
		return "SpanQueryUnique(" + src + ")";
	}

	@Override
	public String getField() {
		return src.getField();
	}
}
