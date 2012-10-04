/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.queryParser.lucene.helpers;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternTerm;
import nl.inl.blacklab.search.TextPatternTranslator;

/**
 * A text pattern matching a phrase, modeled after PhraseQuery. Used with the Lucene Query Language
 * parser.
 */
public class TPPhrase extends TextPattern {

	List<TextPattern> terms = new ArrayList<TextPattern>();

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, String fieldName) {
		List<T> clauses = new ArrayList<T>();
		for (TextPattern t : terms) {
			clauses.add(t.translate(translator, fieldName));
		}
		return translator.sequence(fieldName, clauses);
	}

	public void add(String term, int position) {
		if (position != terms.size())
			throw new RuntimeException(
					"TextPatternPhrase: adding words at specific positions is not supported");
		add(term);
	}

	public void add(String term) {
		terms.add(new TextPatternTerm(term));
	}

	public void setSlop(int phraseSlop) {
		// throw new RuntimeException("Phrase slop is not supported");

		// the parser calls this even if we don't explicitly set it; silently ignore for now
	}

}
