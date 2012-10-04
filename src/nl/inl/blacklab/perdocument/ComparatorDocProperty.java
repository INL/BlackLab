/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.perdocument;

import java.text.Collator;
import java.util.Comparator;

import nl.inl.util.Utilities;

public class ComparatorDocProperty implements Comparator<DocResult> {
	private DocProperty prop;

	Collator collator;

	boolean sortReverse;

	public ComparatorDocProperty(DocProperty prop, Collator collator) {
		this.prop = prop;
		this.collator = collator;
		sortReverse = prop.defaultSortDescending();
	}

	@Override
	public int compare(DocResult first, DocResult second) {
		String a = Utilities.sanitizeForSorting(prop.get(first));
		String b = Utilities.sanitizeForSorting(prop.get(second));
		if (sortReverse)
			return collator.compare(b, a);
		return collator.compare(a, b);
	}

}
