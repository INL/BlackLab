/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.perdocument;

public class DocGroupPropertySize extends DocGroupProperty {
	@Override
	public String get(DocGroup result) {
		return String.format("%09d", result.size());
	}

	@Override
	public boolean defaultSortDescending() {
		return true;
	}
}
