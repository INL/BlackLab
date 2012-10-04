/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.suggest;

import java.util.Arrays;
import java.util.Set;

import nl.inl.blacklab.search.Searcher;

public class FuzzyIndexSuggester extends Suggester {
	private Searcher searcher;
	private String field;
	private float similarity;

	public FuzzyIndexSuggester(Searcher searcher, String field, float similarity) {
		this.searcher = searcher;
		this.field = field;
		this.similarity = similarity;
	}

	@Override
	public void addSuggestions(String original, Suggestions sugg) {
		Set<String> terms = searcher.getMatchingTermsFromIndex(field, Arrays.asList(original),
				similarity);
		for (String t : terms) {
			if (!t.equals(original))
				sugg.addSuggestion("fuzzy", t);
		}
	}
}
