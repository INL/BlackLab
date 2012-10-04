/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.filter;

import org.apache.lucene.analysis.TokenStream;

/**
 * A very simple example of an implementation of a synonym filter.
 */
public class ToyExampleSynonymFilter extends AbstractSynonymFilter {
	public ToyExampleSynonymFilter(TokenStream input) {
		super(input);
	}

	@Override
	public String[] getSynonyms(String s) {
		// toy example
		if (s.equals("old"))
			return new String[] { "olde" };

		return null;
	}
}
