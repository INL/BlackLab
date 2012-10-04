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
import java.util.Stack;

import nl.inl.util.Utilities;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.Version;

/**
 * Abstract base class for implementing synonym filters.
 *
 * This can be used for true synonyms, but also for alternative spellings for certain words, such as
 * a version with accented characters transcribed to unaccented versions.
 *
 * This filter could be applied at either index time or search time.
 *
 * Subclasses should override the getSynonyms() method, and for example use a database to find the
 * appropriate synonyms.
 *
 * (Adapted from sample code from Lucene in Action, 2nd ed.)
 */
public abstract class AbstractSynonymFilter extends TokenFilter {
	/** Include the original token, or just the synonyms? */
	private boolean includeOriginalTokens;

	/**
	 * Construct a synonym filter.
	 *
	 * @param input
	 *            the input tokens to find synonyms for
	 * @param includeOriginalTokens
	 *            Include the original tokens, or just the synonyms?
	 */
	public AbstractSynonymFilter(TokenStream input, boolean includeOriginalTokens) {
		super(input);
		this.includeOriginalTokens = includeOriginalTokens;
		synonymStack = new Stack<State>();
		termAttr = addAttribute(CharTermAttribute.class);
		addAttribute(PositionIncrementAttribute.class);
		addAttribute(TypeAttribute.class);
		helperAttSource = input.cloneAttributes();
	}

	/**
	 * Construct a synonym filter that includes the original tokens.
	 *
	 * @param input
	 *            the input tokens to find synonyms for
	 */
	public AbstractSynonymFilter(TokenStream input) {
		this(input, true);
	}

	public static void main(String[] args) throws IOException {
		TokenStream ts = new WhitespaceTokenizer(Version.LUCENE_36, new StringReader(
				"Dit is een test"));
		ts = new AbstractSynonymFilter(ts) {
			@Override
			public String[] getSynonyms(String s) {
				if (s.equals("test"))
					return new String[] { "testje" };
				if (s.equals("is"))
					return new String[] { "zijn" };
				return null;
			}
		};

		CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
		while (ts.incrementToken()) {
			System.out.println(Utilities.getTerm(term));
		}
	}

	public static final String TOKEN_TYPE_SYNONYM = "SYNONYM";

	public abstract String[] getSynonyms(String s);

	private Stack<State> synonymStack;
	private CharTermAttribute termAttr;

	/**
	 * A copy of the input attributes. Used to construct the states we push on the stack.
	 */
	private AttributeSource helperAttSource;

	@Override
	public boolean incrementToken() throws IOException {
		// If we don't want the original token but just the synonyms,
		// we may have to loop to the first synonym. See end of loop.
		do {
			// Do we have any synonyms left?
			if (synonymStack.size() > 0) {
				// Yes, shift one in.
				State syn = synonymStack.pop();
				restoreState(syn);

				// We're at a synonym. This is always ok (regardless of the
				// value of includeOriginalTokens), so exit the loop.
				break;
			}

			if (!input.incrementToken()) {
				// We're done.
				return false;
			}

			addAliasesToStack();

			// Now we're at the original token. This is only ok if
			// includeOriginalTokens == true; hence the loop.
		} while (!includeOriginalTokens);

		return true; // We're at a desired token (original or synonym)
	}

	private void addAliasesToStack() {
		String[] synonyms = getSynonyms(Utilities.getTerm(termAttr));
		if (synonyms == null)
			return;
		State current = captureState();

		for (int i = 0; i < synonyms.length; i++) {
			helperAttSource.restoreState(current);
			setTerm(helperAttSource, synonyms[i]);
			setType(helperAttSource, TOKEN_TYPE_SYNONYM);
			setPositionIncrement(helperAttSource, 0);
			synonymStack.push(helperAttSource.captureState());
		}
	}

	public static void setPositionIncrement(AttributeSource source, int posIncr) {
		PositionIncrementAttribute attr = source.addAttribute(PositionIncrementAttribute.class);
		attr.setPositionIncrement(posIncr);
	}

	public static void setTerm(AttributeSource source, String term) {
		CharTermAttribute attr = source.addAttribute(CharTermAttribute.class);
		attr.copyBuffer(term.toCharArray(), 0, term.length());
	}

	public static void setType(AttributeSource source, String type) {
		TypeAttribute attr = source.addAttribute(TypeAttribute.class);
		attr.setType(type);
	}

}
