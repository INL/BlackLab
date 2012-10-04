/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.suggest;

import java.util.Comparator;

/**
 * For sorting words by descending similarity to a given target word
 */
final class LevenshteinComparator implements Comparator<String> {
	private Levenshtein levensthein;

	public LevenshteinComparator(String target) {
		levensthein = new Levenshtein(target);
	}

	@Override
	public int compare(String a, String b) {
		Float da = levensthein.similarity(a);
		Float db = levensthein.similarity(b);
		int result = -da.compareTo(db);
		if (result == 0)
			result = a.compareTo(b);
		return result;
	}
}
