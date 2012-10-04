/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.sequences;

import java.util.Comparator;

import nl.inl.blacklab.search.Hit;

/**
 * Compare two hits by end point, then by start point.
 *
 * So the following hits:
 *
 * <pre>
 * (1, 2)
 * (1, 3)
 * (2, 2)
 * (2, 4)
 * (3, 3)
 * </pre>
 *
 * Would be sorted as follows:
 *
 * <pre>
 * (1, 2)
 * (2, 2)
 * (1, 3)
 * (3, 3)
 * (2, 4)
 * </pre>
 */
public class SpanComparatorEndPoint implements Comparator<Hit> {
	@Override
	public int compare(Hit o1, Hit o2) {
		if (o2.end != o1.end)
			return o1.end - o2.end;

		return o1.start - o2.start;
	}
}
