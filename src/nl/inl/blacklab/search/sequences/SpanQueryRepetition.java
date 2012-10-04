/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.sequences;

import java.io.IOException;

import nl.inl.blacklab.search.lucene.SpanQueryBase;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;

/**
 * Finds repeated consecutive hits.
 */
public class SpanQueryRepetition extends SpanQueryBase {
	private int min;

	private int max;

	public SpanQueryRepetition(SpanQuery clause, int min, int max) {
		super(clause);
		this.min = min;
		this.max = max;
		if (max != -1 && min > max)
			throw new RuntimeException("min > max");
		if (min < 1)
			throw new RuntimeException("min < 1");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!super.equals(o))
			return false;

		final SpanQueryRepetition that = (SpanQueryRepetition) o;
		return min == that.min && max == that.max;
	}

	@Override
	public Spans getSpans(IndexReader reader) throws IOException {
		return new SpansRepetition(clauses[0].getSpans(reader), min, max);
	}

	@Override
	public int hashCode() {
		int h = clauses.hashCode();
		h ^= (h << 10) | (h >>> 23);
		h ^= min << 10;
		h ^= max << 5;
		h ^= Float.floatToRawIntBits(getBoost());
		return h;
	}

	@Override
	public String toString(String field) {
		return "SpanQueryRepetition(" + clauses[0] + ", " + min + ", " + max + ")";
	}
}
