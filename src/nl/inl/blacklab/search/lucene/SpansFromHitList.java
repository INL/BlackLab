/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
/**
 *
 */
package nl.inl.blacklab.search.lucene;

import java.util.Collection;
import java.util.List;

import nl.inl.blacklab.search.Hit;

import org.apache.lucene.search.spans.Spans;

public class SpansFromHitList extends Spans {
	private List<Hit> hits;

	private int current = -1;

	public SpansFromHitList(List<Hit> hits) {
		this.hits = hits;
	}

	@Override
	public int doc() {
		return hits.get(current).doc;
	}

	@Override
	public int end() {
		return hits.get(current).end;
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
		return current < hits.size();
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
		return hits.get(current).start;
	}
}