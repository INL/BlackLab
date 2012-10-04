/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.search.Hit;

/**
 * A hit property for grouping per document.
 */
public class HitPropertyDocumentId extends HitProperty {
	@Override
	public String get(Hit result) {
		return String.format("%09d", result.doc);
	}

	@Override
	public String getHumanReadable(Hit result) {
		return Integer.toString(result.doc);
	}

	@Override
	public String getName() {
		return "document id";
	}

}
