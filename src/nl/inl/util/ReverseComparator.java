/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.util;

import java.util.Comparator;

/** Comparator that reverses the sort order of another comparator */
public class ReverseComparator<T> implements Comparator<T> {
	private Comparator<T> comparator;

	public ReverseComparator(Comparator<T> comparator) {
		this.comparator = comparator;
	}

	@Override
	public int compare(T o1, T o2) {
		return -comparator.compare(o1, o2);
	}

}
