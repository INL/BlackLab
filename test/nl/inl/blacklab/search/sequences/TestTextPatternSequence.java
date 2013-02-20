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
package nl.inl.blacklab.search.sequences;

import junit.framework.Assert;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternTerm;
import nl.inl.blacklab.search.TextPatternTranslatorString;

import org.junit.Test;

public class TestTextPatternSequence {
	@Test
	public void testSequence() {
		// "the" followed by "fox", with 1-3 tokens in between
		TextPattern seq = new TextPatternSequence(new TextPatternTerm("the"), new TextPatternTerm(
				"fox"));

		TextPatternTranslatorString trans = new TextPatternTranslatorString();
		String str = seq.translate(trans);

		Assert.assertEquals("SEQ(TERM(contents, the), TERM(contents, fox))", str);
	}

	@Test
	public void testSequenceAnyMiddle() {
		// "the" followed by "fox", with 1-3 tokens in between
		TextPattern seq = new TextPatternSequence(new TextPatternTerm("the"),
				new TextPatternAnyToken(1, 3), new TextPatternTerm("fox"));

		TextPatternTranslatorString trans = new TextPatternTranslatorString();
		String str = seq.translate(trans);

		Assert.assertEquals("SEQ(TERM(contents, the), EXPAND(TERM(contents, fox), true, 1, 3))", str);
	}

	@Test
	public void testSequenceAnyRight() {
		// "the" followed by "fox", with 1-3 tokens in between
		TextPattern seq = new TextPatternSequence(new TextPatternTerm("the"),
				new TextPatternAnyToken(1, 3));

		TextPatternTranslatorString trans = new TextPatternTranslatorString();
		String str = seq.translate(trans);

		Assert.assertEquals("EXPAND(TERM(contents, the), false, 1, 3)", str);
	}

	@Test
	public void testSequenceAnyLeft() {
		// "the" followed by "fox", with 1-3 tokens in between
		TextPattern seq = new TextPatternSequence(new TextPatternAnyToken(1, 3),
				new TextPatternTerm("fox"));

		TextPatternTranslatorString trans = new TextPatternTranslatorString();
		String str = seq.translate(trans);

		Assert.assertEquals("EXPAND(TERM(contents, fox), true, 1, 3)", str);
	}

}
