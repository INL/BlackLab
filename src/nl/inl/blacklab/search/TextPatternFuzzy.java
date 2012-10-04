/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search;

import org.apache.lucene.index.Term;

/**
 * A TextPattern matching a word with fuzzy matching.
 */
public class TextPatternFuzzy extends TextPattern {
	protected String value;

	private float similarity;

	private int prefixLength;

	public TextPatternFuzzy(String value, float similarity) {
		this(value, similarity, 0);
	}

	public TextPatternFuzzy(String value, float similarity, int prefixLength) {
		this.value = value;
		this.similarity = similarity;
		this.prefixLength = prefixLength;
	}

	public Term getTerm(String fieldName) {
		return new Term(fieldName, value);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, String fieldName) {
		return translator.fuzzy(fieldName, value, similarity, prefixLength);
	}

}
