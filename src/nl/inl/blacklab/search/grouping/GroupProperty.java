/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.grouping;

/**
 * Abstract base class for a property of a hit, like document title, hit text, right context, etc.
 */
public abstract class GroupProperty {
	public abstract String get(Group result);

	public boolean defaultSortDescending() {
		return false;
	}
}
