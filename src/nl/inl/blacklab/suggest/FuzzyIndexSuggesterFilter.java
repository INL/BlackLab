/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.suggest;

import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.Searcher;

import org.apache.log4j.Logger;

public class FuzzyIndexSuggesterFilter extends Suggester {
	protected static final Logger logger = Logger.getLogger(FuzzyIndexSuggesterFilter.class);

	private Searcher searcher;

	private String field;

	private float similarity;

	private Suggester suggester;

	public FuzzyIndexSuggesterFilter(Suggester suggester, Searcher searcher, String field,
			float similarity) {
		this.searcher = searcher;
		this.field = field;
		this.similarity = similarity;
		this.suggester = suggester;
	}

	@Override
	public void addSuggestions(String original, Suggestions sugg) {
		Suggestions s = new Suggestions(original);
		suggester.addSuggestions(original, s);
		for (Map.Entry<String, List<String>> e : s.getAllSuggestions().entrySet()) {
			String suggestionType = e.getKey();
			List<String> suggestions = e.getValue();

			for (String t : searcher.getMatchingTermsFromIndex(field, suggestions, similarity)) {
				sugg.addSuggestion(suggestionType, t);
			}
		}
	}
}
