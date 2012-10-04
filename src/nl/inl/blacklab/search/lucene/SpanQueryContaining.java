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
 * Returns spans from the first set that contain one or more spans from the second set.
 *
 * For example,
 * <code>new SpanQueryContaining(new SpanQueryTags("ne"), new SpanTermQuery("John"))</code> will
 * find all named entities containing "John".
 *
 */
public class SpanQueryContaining extends SpanQueryBase {
	public SpanQueryContaining(SpanQuery containers, SpanQuery search) {
		super(containers, search);
	}

	@Override
	public Spans getSpans(IndexReader reader) throws IOException {
		return new SpansContaining(clauses[0].getSpans(reader), clauses[1].getSpans(reader));
	}

	@Override
	public String toString(String field) {
		return "SpanQueryContaining(" + clausesToString(field, " contains ") + ")";
	}
}
