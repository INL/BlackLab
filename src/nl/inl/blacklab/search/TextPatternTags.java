/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search;

import org.apache.lucene.index.Term;

/**
 * A TextPattern matching a word.
 */
public class TextPatternTags extends TextPattern {

	protected String elementName;

	public TextPatternTags(String elementName) {
		this.elementName = elementName;
	}

	public Term getTerm(String fieldName) {
		return new Term(fieldName, elementName);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, String fieldName) {
		return translator.tags(fieldName, elementName);
	}

}
