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

import java.util.Iterator;
import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

/**
 * Takes a List&lt;String&gt; plus two List&lt;Integer&gt;'s and iterates through them as a
 * TokenStream.
 *
 * The Strings are taken as terms. The two integer-lists are taken as start chars and end chars.
 * Token position increment is always 1.
 */
class TokenStreamWithOffsets extends TokenStream {
	/**
	 * Term text of the current token
	 */
	protected CharTermAttribute termAttr;

	/**
	 * Position increment of the current token
	 */
	protected PositionIncrementAttribute positionIncrementAttr;

	/**
	 * Character offsets of the current token
	 */
	private OffsetAttribute offsetAttr;

	protected Iterator<String> iterator;

	protected Iterator<Integer> incrementIt;

	private Iterator<Integer> startCharIt;

	private Iterator<Integer> endCharIt;

	public TokenStreamWithOffsets(List<String> tokens, List<Integer> increments, List<Integer> startChar,
			List<Integer> endChar) {
		clearAttributes();
		termAttr = addAttribute(CharTermAttribute.class);
		offsetAttr = addAttribute(OffsetAttribute.class);
		positionIncrementAttr = addAttribute(PositionIncrementAttribute.class);
		positionIncrementAttr.setPositionIncrement(1);

		iterator = tokens.iterator();
		incrementIt = increments.iterator();
		startCharIt = startChar.iterator();
		endCharIt = endChar.iterator();
	}

	@Override
	final public boolean incrementToken() {
		// Capture token contents
		if (iterator.hasNext()) {
			String term = iterator.next();
			if (term == null)
				System.err.println("TERM==NULL");
			termAttr.copyBuffer(term.toCharArray(), 0, term.length());
			positionIncrementAttr.setPositionIncrement(incrementIt.next());
			offsetAttr.setOffset(startCharIt.next(), endCharIt.next());
			return true;
		}
		return false;
	}

}
