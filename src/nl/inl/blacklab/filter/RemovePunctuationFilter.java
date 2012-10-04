/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.filter;

import java.io.IOException;
import java.util.regex.Pattern;

import nl.inl.util.Utilities;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Replaces punctuation with space.
 */
public class RemovePunctuationFilter extends TokenFilter {
	final static Pattern punctuationPattern = Pattern.compile("\\p{P}+");

	public static String process(String input) {
		return punctuationPattern.matcher(input).replaceAll("");
	}

	public static void main(String[] args) {
		String input = "Hé, jij daar!";
		System.out.println(process(input));
	}

	private CharTermAttribute termAtt;

	public RemovePunctuationFilter(TokenStream input) {
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
