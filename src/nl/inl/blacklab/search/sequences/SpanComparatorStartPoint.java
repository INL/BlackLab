/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.sequences;

import java.util.Comparator;

import nl.inl.blacklab.search.Hit;

/**
 * Compare two hits by start point, then by end point.
 */
public class SpanComparatorStartPoint implements Comparator<Hit> {
	@Override
	public int compare(Hit o1, Hit o2) {
		if (o2.start != o1.start)
			return o1.start - o2.start;

		return o1.end - o2.end;
	}
}
