/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search;

/**
 * A TextPattern matching words that start with the specified prefix.
 */
public class TextPatternPrefix extends TextPatternTerm {
	public TextPatternPrefix(String value) {
		super(value);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, String fieldName) {
		return translator.prefix(fieldName, value);
	}

}
