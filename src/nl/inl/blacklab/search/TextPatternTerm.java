/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search;

import org.apache.lucene.index.Term;

/**
 * A TextPattern matching a word.
 */
public class TextPatternTerm extends TextPattern {
	protected String value;

	public String getValue() {
		return value;
	}

	public TextPatternTerm(String value) {
		this.value = value;
	}

	public Term getTerm(String fieldName) {
		return new Term(fieldName, value);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, String fieldName) {
		return translator.term(fieldName, value);
	}

}
