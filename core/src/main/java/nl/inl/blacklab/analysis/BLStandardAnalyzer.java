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
package nl.inl.blacklab.analysis;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import nl.inl.blacklab.filter.RemoveAllAccentsFilter;
import nl.inl.blacklab.filter.RemovePunctuationFilter;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;

/**
 * A simple analyzer based on StandardTokenizer that isn't limited to Latin.
 *
 * Has the option of analyzing case-/accent-sensitive or -insensitive, depending on the field name.
 */
public class BLStandardAnalyzer extends Analyzer {

	@Override
	protected TokenStreamComponents createComponents(String fieldName) {
		Tokenizer source = new StandardTokenizerFactory(Collections.<String,String>emptyMap()).create();
		TokenStream filter = source;
		boolean caseSensitive = ComplexFieldUtil.isCaseSensitive(fieldName);
		if (!caseSensitive)
		{
			filter = new LowerCaseFilter(filter);// lowercase all
		}
		boolean diacSensitive = ComplexFieldUtil.isDiacriticsSensitive(fieldName);
		if (!diacSensitive)
		{
			filter = new RemoveAllAccentsFilter(filter); // remove accents
		}
		if (!(caseSensitive && diacSensitive))
		{
			// Is this necessary and does it do what we want?
			// e.g. do we want "zon" to ever match "zo'n"? Or are there examples
			//      where this is useful/required?
			filter = new RemovePunctuationFilter(filter); // remove punctuation
		}
		return new TokenStreamComponents(source, filter);
	}

	public static void main(String[] args) throws IOException {
		String TEST_STR = "Hé jij И!  раскази и повѣсти. Ст]' Дѣдо  	Нисторъ. Ива";

		try (Analyzer a = new BLStandardAnalyzer()) {
			TokenStream ts = a.tokenStream("test", new StringReader(TEST_STR));
			CharTermAttribute ta = ts.addAttribute(CharTermAttribute.class);
			while (ts.incrementToken()) {
				System.out.println(new String(ta.buffer(), 0, ta.length()));
			}
			TokenStream ts2 = a.tokenStream(ComplexFieldUtil.propertyField("test", null, "s"),
					new StringReader(TEST_STR));
			ta = ts2.addAttribute(CharTermAttribute.class);
			while (ts2.incrementToken()) {
				System.out.println(new String(ta.buffer(), 0, ta.length()));
			}
		}
	}

	/* CODE JESSE: */
	static Pattern prePunctuationPattern = Pattern.compile("(^|\\s)\\p{P}+");
	static Pattern postPunctuationPattern = Pattern.compile("\\p{P}+($|\\s)");

	public String prePunctuation = "";
	public String postPunctuation = "";
	public String trimmedToken = "";

	public void tokenize(String t) {
		Matcher m1 = prePunctuationPattern.matcher(t);
		Matcher m2 = postPunctuationPattern.matcher(t);

		int s = 0;
		int e = t.length();

		if (m1.find())
			s = m1.end();
		if (m2.find())
			e = m2.start();

		if (e < s)
			e = s;
		trimmedToken = t.substring(s, e);
		prePunctuation = t.substring(0, s);
		postPunctuation = t.substring(e, t.length());
	}
}
