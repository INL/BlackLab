/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.grouping;

import nl.inl.util.Utilities;

/**
 * Abstract base class for a property of a hit, like document title, hit text, right context, etc.
 */
public class GroupPropertyIdentity extends GroupProperty {
	@Override
	public String get(Group result) {
		return Utilities.sanitizeForSorting(result.getIdentity().toString());
	}

	@Override
	public boolean defaultSortDescending() {
		return false;
	}
}
