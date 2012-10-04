/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.filter;

import java.io.IOException;
import java.io.StringReader;

import nl.inl.util.Utilities;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;

/**
 * Adds alternative German spellings for words with special and accented characters.
 */
public class TranscribeGermanAccentsSynonymFilter extends AbstractSynonymFilter {
	public TranscribeGermanAccentsSynonymFilter(TokenStream input) {
		super(input);
	}

	public static void main(String[] args) throws IOException {
		TokenStream ts = new WhitespaceTokenizer(Version.LUCENE_36, new StringReader(
				"Aachen Düsseldorf Köln Berlin Österreich"));
		try {
			ts = new TranscribeGermanAccentsSynonymFilter(ts);
			ts = new RemoveAllAccentsFilter(ts);

			CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
			while (ts.incrementToken()) {
				System.out.println(Utilities.getTerm(term));
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
