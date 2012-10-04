/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search;

/**
 * A TextPattern returning hits from the "include" clause, but only in documents where the "exclude"
 * clause doesn't occur.
 */
public class TextPatternDocLevelAndNot extends TextPattern {

	private TextPattern include;

	private TextPattern exclude;

	public TextPatternDocLevelAndNot(TextPattern include, TextPattern exclude) {
		this.include = include;
		this.exclude = exclude;
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, String fieldName) {
		return translator.docLevelAndNot(include.translate(translator, fieldName),
				exclude.translate(translator, fieldName));
	}

}
