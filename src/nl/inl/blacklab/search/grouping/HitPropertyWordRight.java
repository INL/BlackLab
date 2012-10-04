/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.search.Hit;
import nl.inl.util.Utilities;

/**
 * A hit property for grouping on the context of the hit. Requires HitConcordances as input (so we
 * have the hit text available).
 */
public class HitPropertyWordRight extends HitProperty {
	boolean lowerCase;

	public HitPropertyWordRight(boolean lowerCase) {
		super();
		this.lowerCase = lowerCase;
	}

	public HitPropertyWordRight() {
		this(false);
	}

	@Override
	public String get(Hit result) {
		if (result.conc == null) {
			throw new RuntimeException("Can only sort/group on context if results are concordances");
		}
		String rightContext = result.conc[2].trim();
		int indexOf = rightContext.indexOf(' ');
		String word = rightContext;
		if (indexOf >= 0)
			word = rightContext.substring(0, indexOf);
		return Utilities.xmlToSortable(word, lowerCase);
	}

	@Override
	public boolean needsConcordances() {
		return true;
	}

	@Override
	public String getName() {
		return "word right";
	}

}
