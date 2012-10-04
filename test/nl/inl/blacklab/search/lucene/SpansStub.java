/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
/**
 *
 */
package nl.inl.blacklab.search.lucene;

import java.util.Collection;

import org.apache.lucene.search.spans.Spans;

public class SpansStub extends Spans {
	private int[] doc;

	private int[] start;

	private int[] end;

	private int current = -1;

	public SpansStub(int[] doc, int[] start, int[] end) {
		this.doc = doc;
		this.start = start;
		this.end = end;
	}

	@Override
	public int doc() {
		return doc[current];
	}

	@Override
	public int end() {
		return end[current];
	}

	@Override
	public Collection<byte[]> getPayload() {
		return null;
	}

	@Override
	public boolean isPayloadAvailable() {
		return false;
	}

	@Override
	public boolean next() {
		current++;
		return current < doc.length;
	}

	@Override
	public boolean skipTo(int target) {
		boolean more = true;
		while (more && (current < 0 || doc() < target))
			more = next();
		return more;
	}

	@Override
	public int start() {
		return start[current];
	}
}