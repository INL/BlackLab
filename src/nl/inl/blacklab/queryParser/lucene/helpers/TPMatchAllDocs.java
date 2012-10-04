/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.queryParser.lucene.helpers;

import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternTranslator;

public class TPMatchAllDocs extends TextPattern {

	public TPMatchAllDocs() {
		throw new RuntimeException("Match all docs is not supported");
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, String fieldName) {
		throw new RuntimeException("Match all docs is not supported");
	}

}
