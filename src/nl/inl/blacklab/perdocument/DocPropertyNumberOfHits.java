/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.perdocument;

/**
 * For grouping DocResult objects on the number of hits. This would put documents with 1 hit in a
 * group, documents with 2 hits in another group, etc.
 */
public class DocPropertyNumberOfHits extends DocProperty {
	@Override
	public String get(DocResult result) {
		return String.format("%5d", result.getHits().size());
	}

	@Override
	public boolean defaultSortDescending() {
		return true;
	}

	@Override
	public String getName() {
		return "number of hits";
	}
}
