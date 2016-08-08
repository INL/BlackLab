/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.filter;

import java.io.IOException;
import java.io.StringReader;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Transcribes words with special and accented characters according to rules for German.
 *
 * Adapted from sample code from Lucene in Action, 2nd ed.
 */
public class TranscribeGermanAccentsFilter extends TokenFilter {
	/**
	 * Transcribe German accents
	 * @param input string to process
	 * @return the string with accents transcribed to ASCII characters
	 */
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

	/**
	 * Test program
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		Tokenizer t = new WhitespaceTokenizer();
		t.setReader(new StringReader("Aachen Düsseldorf Köln Berlin Österreich"));
		try (TokenStream ts = new TranscribeGermanAccentsFilter(t)) {
			CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
			while (ts.incrementToken()) {
				System.out.println(new String(term.buffer(), 0, term.length()));
			}
		}
	}

	private CharTermAttribute termAtt;

	/**
	 * @param input input token stream
	 */
	public TranscribeGermanAccentsFilter(TokenStream input) {
		super(input);
		termAtt = addAttribute(CharTermAttribute.class);
	}

	@Override
	final public boolean incrementToken() throws IOException {
		if (input.incrementToken()) {
			String t = new String(termAtt.buffer(), 0, termAtt.length());
			t = process(t);
			termAtt.copyBuffer(t.toCharArray(), 0, t.length());
			return true;
		}
		return false;
	}

}
