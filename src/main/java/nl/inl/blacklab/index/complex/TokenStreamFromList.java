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
package nl.inl.blacklab.index.complex;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

/**
 * Takes an Iterable<String> and iterates through it as a TokenStream.
 *
 * The Strings are taken as terms, and the position increment is always 1.
 */
class TokenStreamFromList extends TokenStream {

	/** Iterator over the terms */
	protected Iterator<String> iterator;

	/** Iterator over the position increments */
	private Iterator<Integer> incrementIt;

	/**
	 * Term text of the current token
	 */
	protected CharTermAttribute termAttr;

	/**
	 * Position increment of the current token
	 */
	protected PositionIncrementAttribute positionIncrementAttr;

	public TokenStreamFromList(Iterable<String> tokens, Iterable<Integer> increments) {
		clearAttributes();
		termAttr = addAttribute(CharTermAttribute.class);
		positionIncrementAttr = addAttribute(PositionIncrementAttribute.class);
		positionIncrementAttr.setPositionIncrement(1);

		iterator = tokens.iterator();
		incrementIt = increments.iterator();
	}

	@Override
	public boolean incrementToken() {
		// Capture token contents
		if (iterator.hasNext()) {
			String word = iterator.next();
			termAttr.copyBuffer(word.toCharArray(), 0, word.length());
			positionIncrementAttr.setPositionIncrement(incrementIt.next());
			return true;
		}
		return false;
	}

	public static void main(String[] args) throws IOException {
		TokenStream s = new TokenStreamFromList(Arrays.asList("a", "b", "c"), Arrays.asList(1, 1, 1));
		try {
			CharTermAttribute term = s.addAttribute(CharTermAttribute.class);
			s.incrementToken();
			System.out.println(new String(term.buffer(), 0, term.length()));
			s.incrementToken();
			System.out.println(new String(term.buffer(), 0, term.length()));
			s.incrementToken();
			System.out.println(new String(term.buffer(), 0, term.length()));
			System.out.println(s.incrementToken());
		} finally {
			s.close();
		}
	}

}