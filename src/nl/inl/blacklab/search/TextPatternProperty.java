/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search;

/**
 * TextPattern for wrapping another TextPattern so that it applies to a certain word property.
 *
 * For example, to find lemmas starting with "bla": <code>
 * TextPattern tp = new TextPatternProperty("lemma", new TextPatternWildcard("bla*"));
 * </code>
 */
public class TextPatternProperty extends TextPattern {
	private TextPattern input;

	private String propertyName;

	private String altName;

	public TextPatternProperty(String propertyName, String altName, TextPattern input) {
		this.propertyName = propertyName == null ? "" : propertyName;
		this.altName = altName == null ? "" : altName;
		this.input = input;
	}

	public TextPatternProperty(String propertyName, TextPattern input) {
		this(propertyName, null, input);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, String fieldName) {
		return translator.property(fieldName, propertyName, altName, input);
	}
}
