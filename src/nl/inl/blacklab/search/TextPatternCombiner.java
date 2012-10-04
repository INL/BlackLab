/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for combining several text patterns into a single new compound TextPattern
 */
public abstract class TextPatternCombiner extends TextPattern {
	protected List<TextPattern> clauses = new ArrayList<TextPattern>();

	public TextPatternCombiner(TextPattern... clauses) {
		for (TextPattern clause : clauses) {
			addClause(clause);
		}
	}

	public int numberOfClauses() {
		return clauses.size();
	}

	@Override
	public abstract <T> T translate(TextPatternTranslator<T> translator, String fieldName);

	public void addClause(TextPattern clause) {
		clauses.add(clause);
	}

}
