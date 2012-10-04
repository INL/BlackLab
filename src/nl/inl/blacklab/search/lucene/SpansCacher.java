/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import java.util.Iterator;

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Searcher;

import org.apache.lucene.search.spans.Spans;

/**
 * Retrieves all results from a source Results object and stores them in a List. This class is used
 * for sorting (see ResultsSorter) and for easier debugging (see Searcher.find()).
 */
public class SpansCacher extends SpansWithHit {
	protected Hits hits;

	Hit current;

	private Iterator<Hit> it;

	public SpansCacher(Searcher searcher, Spans source, String defaultConcField) {
		try {
			hits = new Hits(searcher, source, defaultConcField);
			reset();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void add(Hit r) {
		hits.add(r);
	}

	public int size() {
		return hits.size();
	}

	@Override
	public boolean next() {
		if (!it.hasNext())
			return false;
		current = it.next();
		return true;
	}

	public void reset() {
		it = hits.iterator();
		current = null;
	}

	@Override
	public int doc() {
		return current.doc;
	}

	@Override
	public int end() {
		return current.end;
	}

	@Override
	public int start() {
		return current.start;
	}

	@Override
	public Hit getHit() {
		return current;
	}

	public Hits getResults() {
		return hits;
	}
}
