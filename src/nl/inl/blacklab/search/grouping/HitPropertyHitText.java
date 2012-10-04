/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.search.Hit;

/**
 * A hit property for grouping on the text actually matched. Requires HitConcordances as input (so
 * we have the hit text available).
 *
 */
public class HitPropertyHitText extends HitProperty {
	boolean lowerCase;

	public HitPropertyHitText(boolean lowerCase) {
		super();
		this.lowerCase = lowerCase;
	}

	public HitPropertyHitText() {
		this(false);
	}

	@Override
	public String get(Hit result) {
		if (result.conc == null) {
			throw new RuntimeException(
					"Can only sort/group on hit text if results are concordances");
		}
		// NOTE: disabled XML stripping because we use the forward index or term vector for
		// sorting/grouping!
		return lowerCase ? result.conc[1].toLowerCase() : result.conc[1];
		// return Utilities.xmlToSortable(result.conc[1], lowerCase);
	}

	@Override
	public boolean needsConcordances() {
		return true;
	}

	@Override
	public String getName() {
		return "hit text";
	}
}
