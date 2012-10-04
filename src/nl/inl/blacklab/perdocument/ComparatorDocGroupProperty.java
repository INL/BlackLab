/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.perdocument;

import java.text.Collator;
import java.util.Comparator;

public class ComparatorDocGroupProperty implements Comparator<DocGroup> {
	private DocGroupProperty prop;

	Collator collator;

	boolean sortReverse;

	public ComparatorDocGroupProperty(DocGroupProperty prop, boolean sortReverse, Collator collator) {
		this.prop = prop;
		this.collator = collator;
		this.sortReverse = prop.defaultSortDescending() ? !sortReverse : sortReverse;
	}

	@Override
	public int compare(DocGroup first, DocGroup second) {
		String a = prop.get(first);
		String b = prop.get(second);
		if (sortReverse)
			return collator.compare(b, a);
		return collator.compare(a, b);
	}

}
