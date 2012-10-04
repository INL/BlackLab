/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.grouping;

/**
 * Abstract base class for a property of a hit, like document title, hit text, right context, etc.
 */
public class GroupPropertySize extends GroupProperty {
	@Override
	public String get(Group result) {
		if (result instanceof RandomAccessGroup)
			return String.format("%09d", ((RandomAccessGroup) result).size());
		throw new RuntimeException("Cannot get group size from non-random-access group");
	}

	@Override
	public boolean defaultSortDescending() {
		return true;
	}
}
