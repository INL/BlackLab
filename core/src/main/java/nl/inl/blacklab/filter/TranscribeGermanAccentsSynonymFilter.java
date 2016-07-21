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

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Adds alternative German spellings for words with special and accented characters.
 */
public class TranscribeGermanAccentsSynonymFilter extends AbstractSynonymFilter {
	/**
	 * @param input input token stream
	 */
	public TranscribeGermanAccentsSynonymFilter(TokenStream input) {
		super(input);
	}

	public static void main(String[] args) throws IOException {
		Tokenizer t = new WhitespaceTokenizer();
		t.setReader(new StringReader("Aachen Düsseldorf Köln Berlin Österreich"));
		TokenStream ts = new TranscribeGermanAccentsSynonymFilter(t);
		try {
			ts.reset();
			ts = new RemoveAllAccentsFilter(ts);
			ts.reset();

			CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
			while (ts.incrementToken()) {
				System.out.println(new String(term.buffer(), 0, term.length()));
			}
		} finally {
			ts.close();
		}
	}

	@Override
	public String[] getSynonyms(String inputToken) {
		// See if there's a transcription
		String transcription = TranscribeGermanAccentsFilter.process(inputToken);
		boolean hasTranscription = !transcription.equals(inputToken);

		// Construct the synonym array
		if (hasTranscription)
			return new String[] { transcription };

		return null;
	}
}
