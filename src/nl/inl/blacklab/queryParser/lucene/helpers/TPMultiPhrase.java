/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.queryParser.lucene.helpers;

import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternTranslator;

public class TPMultiPhrase extends TextPattern {

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, String fieldName) {
		throw new RuntimeException("MultiPhrase is not supported");
	}

	public void add(org.apache.lucene.index.Term[] array, int position) {
		throw new RuntimeException("MultiPhrase is not supported");
	}

	public void add(org.apache.lucene.index.Term[] array) {
		throw new RuntimeException("MultiPhrase is not supported");
	}

	public void setSlop(int phraseSlop) {
		throw new RuntimeException("MultiPhrase is not supported");
	}

}
