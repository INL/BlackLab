/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
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
		String str = seq.translate(trans, "contents");

		Assert.assertEquals("SEQ(TERM(the), TERM(fox))", str);
	}

	@Test
	public void testSequenceAnyMiddle() {
		// "the" followed by "fox", with 1-3 tokens in between
		TextPattern seq = new TextPatternSequence(new TextPatternTerm("the"),
				new TextPatternAnyToken(1, 3), new TextPatternTerm("fox"));

		TextPatternTranslatorString trans = new TextPatternTranslatorString();
		String str = seq.translate(trans, "contents");

		Assert.assertEquals("SEQ(TERM(the), EXPAND(TERM(fox), true, 1, 3))", str);
	}

	@Test
	public void testSequenceAnyRight() {
		// "the" followed by "fox", with 1-3 tokens in between
		TextPattern seq = new TextPatternSequence(new TextPatternTerm("the"),
				new TextPatternAnyToken(1, 3));

		TextPatternTranslatorString trans = new TextPatternTranslatorString();
		String str = seq.translate(trans, "contents");

		Assert.assertEquals("EXPAND(TERM(the), false, 1, 3)", str);
	}

	@Test
	public void testSequenceAnyLeft() {
		// "the" followed by "fox", with 1-3 tokens in between
		TextPattern seq = new TextPatternSequence(new TextPatternAnyToken(1, 3),
				new TextPatternTerm("fox"));

		TextPatternTranslatorString trans = new TextPatternTranslatorString();
		String str = seq.translate(trans, "contents");

		Assert.assertEquals("EXPAND(TERM(fox), true, 1, 3)", str);
	}

}
