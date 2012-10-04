/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.grouping.HitProperty;

import org.apache.lucene.search.spans.Spans;

/**
 * Sort results using some comparator. Subclasses SpansCacher to retrieve all results, then sorts
 * the resulting list.
 */
public class SpansSorter extends SpansCacher {

	public SpansSorter(Searcher searcher, Spans source, HitProperty sortProp,
			String defaultConcField) {
		this(searcher, source, sortProp, false, defaultConcField);
	}

	public SpansSorter(Searcher searcher, Spans source, HitProperty sortProp,
			final boolean reverseSort, String defaultConcField) {
		super(searcher, source, defaultConcField);

		hits.sort(sortProp, reverseSort);

		reset();
	}
}
