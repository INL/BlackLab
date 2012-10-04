/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Collection;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;

/**
 * Combines SpanQueries using AND. Note that this means that only matches with the same document id,
 * the same start and the same end positions in all SpanQueries will be kept.
 */
public class SpanQueryAnd extends SpanQueryBase {
	public SpanQueryAnd(SpanQuery first, SpanQuery second) {
		super(first, second);
	}

	public SpanQueryAnd(Collection<SpanQuery> clauscol) {
		super(clauscol);
	}

	public SpanQueryAnd(SpanQuery[] _clauses) {
		super(_clauses);
	}

	@Override
	public Spans getSpans(IndexReader reader) throws IOException {
		Spans combi = clauses[0].getSpans(reader);
		for (int i = 1; i < clauses.length; i++) {
			Spans si = clauses[i].getSpans(reader);
			combi = new SpansAnd(combi, si);
		}
		return combi;
	}

	@Override
	public String toString(String field) {
		return "SpanQueryAnd(" + clausesToString(field, " & ") + ")";
	}
}
