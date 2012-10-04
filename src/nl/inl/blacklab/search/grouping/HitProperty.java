/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.search.Hit;

/**
 * Abstract base class for a property of a hit, like document title, hit text, right context, etc.
 */
public abstract class HitProperty {
	public abstract String get(Hit result);

	public String getHumanReadable(Hit result) {
		return get(result);
	}

	public boolean needsConcordances() {
		return false;
	}

	public abstract String getName();
}
