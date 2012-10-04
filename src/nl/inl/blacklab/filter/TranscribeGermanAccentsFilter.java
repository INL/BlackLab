/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.filter;

import java.io.IOException;
import java.io.StringReader;

import nl.inl.util.Utilities;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;

/**
 * Transcribes words with special and accented characters according to rules for German.
 *
 * Adapted from sample code from Lucene in Action, 2nd ed.
 */
public class TranscribeGermanAccentsFilter extends TokenFilter {
	public static String process(String input) {
		// NOTE: could be sped up by looping over a char[]
		input = input.replaceAll("ö", "oe");
		input = input.replaceAll("ü", "ue");
		input = input.replaceAll("ä", "ae");
		input = input.replaceAll("Ö", "Oe");
		input = input.replaceAll("Ü", "Ue");
		input = input.replaceAll("Ä", "Ae");
		input = input.replaceAll("ß", "ss");
		return input;
	}

	public static void main(String[] args) throws IOException {
		TokenStream ts = new WhitespaceTokenizer(Version.LUCENE_36, new StringReader(
				"Aachen Düsseldorf Köln Berlin Österreich"));
		try {
			ts = new TranscribeGermanAccentsFilter(ts);

			CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
			while (ts.incrementToken()) {
				System.out.println(Utilities.getTerm(term));
			}
		} finally {
			ts.close();
		}
	}

	private CharTermAttribute termAtt;

	public TranscribeGermanAccentsFilter(TokenStream input) {
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
