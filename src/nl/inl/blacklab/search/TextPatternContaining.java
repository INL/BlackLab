/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search;

/**
 * A TextPattern searching for a TextPattern inside the hits from another TextPattern. This may be
 * used to search inside sentences, sXML tags, etc.
 */
public class TextPatternContaining extends TextPattern {
	private TextPattern containers;

	private TextPattern search;

	public TextPatternContaining(TextPattern containers, TextPattern search) {
		this.containers = containers;
		this.search = search;
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, String fieldName) {
		T trContainers = containers.translate(translator, fieldName);
		T trSearch = search.translate(translator, fieldName);
		return translator.containing(fieldName, trContainers, trSearch);
	}

}
