/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.perdocument;

/**
 * Abstract base class for a property of a hit, like document title, hit text, right context, etc.
 */
public abstract class DocGroupProperty {
	public abstract String get(DocGroup result);

	public String getHumanReadable(DocGroup result) {
		return get(result);
	}

	public boolean defaultSortDescending() {
		return false;
	}
}
