/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.queryParser.lucene.helpers;

import java.text.Collator;

import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternTranslator;

public class TPTermRange extends TextPattern {

	public TPTermRange(String field, String part1, String part2, boolean inclusive,
			boolean inclusive2, Collator rangeCollator) {
		throw new RuntimeException("Term Range is not supported");
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, String fieldName) {
		throw new RuntimeException("Term Range is not supported");
	}

}
