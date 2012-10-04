/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.filter;

import java.io.IOException;

import nl.inl.util.StringUtil;
import nl.inl.util.Utilities;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Removes any accents from the input.
 *
 * NOTE: Lucene includes ASCIIFoldingFilter, but this works with non-ASCII characters too.
 *
 * Uses Normalizer, so Java 1.6+ is needed. If this is not available, use an approach such as
 * RemoveDutchAccentsFilter.
 */
public class RemoveAllAccentsFilter extends TokenFilter {
	public static String process(String input) {
		return StringUtil.removeAccents(input);
	}

	public static void main(String[] args) {
		System.out.println(process("Hé jij daar!"));
	}

	private CharTermAttribute termAtt;

	public RemoveAllAccentsFilter(TokenStream input) {
		super(input);
		termAtt = addAttribute(CharTermAttribute.class);
	}

	@Override
	public boolean incrementToken() throws IOException {
		if (input.incrementToken()) {
			String t = Utilities.getTerm(termAtt);
			t = process(t);
			termAtt.copyBuffer(t.toCharArray(), 0, t.length());
			return true;
		}
		return false;
	}

}
