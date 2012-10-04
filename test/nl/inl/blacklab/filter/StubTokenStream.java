/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.filter;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

public class StubTokenStream extends TokenStream {
	private CharTermAttribute ta;

	private int i = -1;

	private String[] terms;

	public StubTokenStream(String[] terms) {
		this.terms = terms;
		ta = addAttribute(CharTermAttribute.class);
		PositionIncrementAttribute pa = addAttribute(PositionIncrementAttribute.class);
		pa.setPositionIncrement(1);
	}

	@Override
	public boolean incrementToken() {
		i++;
		if (i >= terms.length)
			return false;
		ta.copyBuffer(terms[i].toCharArray(), 0, terms[i].length());
		return true;
	}

}
