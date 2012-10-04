/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
/**
 *
 */
package nl.inl.blacklab.index.complex;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import nl.inl.util.Utilities;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

/**
 * Takes an Iterable<String> and iterates through it as a TokenStream.
 *
 * The Strings are taken as terms, and the position increment is always 1.
 */
class TokenStreamFromList extends TokenStream {
	protected Iterator<String> iterator;

	/**
	 * Term text of the current token
	 */
	protected CharTermAttribute termAttr;

	/**
	 * Position increment of the current token
	 */
	protected PositionIncrementAttribute positionIncrementAttr;

	public TokenStreamFromList(Iterable<String> tokens) {
		clearAttributes();
		termAttr = addAttribute(CharTermAttribute.class);
		positionIncrementAttr = addAttribute(PositionIncrementAttribute.class);
		positionIncrementAttr.setPositionIncrement(1);

		iterator = tokens.iterator();
	}

	@Override
	public boolean incrementToken() {
		// Capture token contents
		if (iterator.hasNext()) {
			String word = iterator.next();
			termAttr.copyBuffer(word.toCharArray(), 0, word.length());
			return true;
		}
		return false;
	}

	public static void main(String[] args) throws IOException {
		TokenStream s = new TokenStreamFromList(Arrays.asList("a", "b", "c"));
		try {
			CharTermAttribute term = s.addAttribute(CharTermAttribute.class);
			s.incrementToken();
			System.out.println(Utilities.getTerm(term));
			s.incrementToken();
			System.out.println(Utilities.getTerm(term));
			s.incrementToken();
			System.out.println(Utilities.getTerm(term));
			System.out.println(s.incrementToken());
		} finally {
			s.close();
		}
	}

}