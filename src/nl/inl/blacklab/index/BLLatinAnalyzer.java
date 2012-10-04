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
