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

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import nl.inl.util.StringUtil;

/**
 * Lowercases and/or removes any accents from the input.
 *
 * NOTE: Lucene includes ASCIIFoldingFilter, but this works with non-ASCII characters too.
 *
 * Uses Normalizer, so Java 1.6+ is needed. If this is not available, use an approach such as
 * RemoveDutchAccentsFilter.
 */
public class DesensitizeFilter extends TokenFilter {

	private CharTermAttribute termAtt;

	private boolean lowerCase;

	private boolean removeAccents;

	/**
	 * @param input the token stream to desensitize
	 * @param lowerCase whether to lower case tokens
	 * @param removeAccents whether to remove accents
	 */
	public DesensitizeFilter(TokenStream input, boolean lowerCase, boolean removeAccents) {
		super(input);
		this.lowerCase = lowerCase;
		this.removeAccents = removeAccents;
		termAtt = addAttribute(CharTermAttribute.class);
	}

	@Override
	final public boolean incrementToken() throws IOException {
		if (input.incrementToken()) {
			String t = new String(termAtt.buffer(), 0, termAtt.length());
			if (removeAccents)
				t = StringUtil.removeAccents(t);
			if (lowerCase)
				t = t.toLowerCase();
			termAtt.copyBuffer(t.toCharArray(), 0, t.length());
			return true;
		}
		return false;
	}

}
