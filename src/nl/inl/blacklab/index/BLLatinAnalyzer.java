/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
/**
 *
 */
package nl.inl.blacklab.index;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.util.Utilities;

import org.apache.lucene.analysis.ASCIIFoldingFilter;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;

/**
 * A Latin-specific analyzer.
 *
 * Use BLDefaultAnalyzer instead, it's more generic.
 */
public final class BLLatinAnalyzer extends Analyzer {
	@Override
	public TokenStream tokenStream(String fieldName, Reader reader) {
		TokenStream ts = new StandardTokenizer(Version.LUCENE_30, reader);
		if (!ComplexFieldUtil.isAlternative(fieldName, "s")) // not case- and accent-sensitive?
		{
			ts = new LowerCaseFilter(Version.LUCENE_36, ts); // lowercase all
			ts = new ASCIIFoldingFilter(ts); // remove accents
		}
		return ts;
	}

	public static void main(String[] args) throws IOException {
		String TEST_STR = "Hé jij daar!";

		Analyzer a = new BLLatinAnalyzer();
		try {
			TokenStream ts = a.tokenStream("test", new StringReader(TEST_STR));
			CharTermAttribute ta = ts.addAttribute(CharTermAttribute.class);
			while (ts.incrementToken()) {
				System.out.println(Utilities.getTerm(ta));
			}
			TokenStream ts2 = a.tokenStream(ComplexFieldUtil.fieldName("test", null, "s"),
					new StringReader(TEST_STR));
			ta = ts2.addAttribute(CharTermAttribute.class);
			while (ts2.incrementToken()) {
				System.out.println(Utilities.getTerm(ta));
			}
		} finally {
			a.close();
		}
	}
}
