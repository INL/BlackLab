/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search;

import junit.framework.Assert;

import org.junit.Test;

public class TestTextPatternRegex {

	@Test
	public void testGetAppropriatePatternSimple() {
		TextPattern t = TextPatternRegex.getAppropriatePattern("^blabla$");
		Assert.assertEquals(TextPatternTerm.class, t.getClass());
		Assert.assertEquals("blabla", ((TextPatternTerm) t).getValue());
	}

	@Test
	public void testGetAppropriatePatternPrefix() {
		TextPattern t = TextPatternRegex.getAppropriatePattern("^blabla");
		Assert.assertEquals(TextPatternPrefix.class, t.getClass());
		Assert.assertEquals("blabla", ((TextPatternPrefix) t).getValue());

		t = TextPatternRegex.getAppropriatePattern("^blabla.*$");
		Assert.assertEquals(TextPatternPrefix.class, t.getClass());
		Assert.assertEquals("blabla", ((TextPatternPrefix) t).getValue());
	}

	@Test
	public void testGetAppropriatePatternWildcard() {
		TextPattern t = TextPatternRegex.getAppropriatePattern("bla$");
		Assert.assertEquals(TextPatternWildcard.class, t.getClass());
		Assert.assertEquals("*bla", ((TextPatternWildcard) t).getValue());

		t = TextPatternRegex.getAppropriatePattern("^.*bla$");
		Assert.assertEquals(TextPatternWildcard.class, t.getClass());
		Assert.assertEquals("*bla", ((TextPatternWildcard) t).getValue());

		t = TextPatternRegex.getAppropriatePattern("bl..a");
		Assert.assertEquals(TextPatternWildcard.class, t.getClass());
		Assert.assertEquals("*bl??a*", ((TextPatternWildcard) t).getValue());

		t = TextPatternRegex.getAppropriatePattern("bl.+a");
		Assert.assertEquals(TextPatternWildcard.class, t.getClass());
		Assert.assertEquals("*bl?*a*", ((TextPatternWildcard) t).getValue());
	}

	@Test
	public void testGetAppropriatePatternRegex() {
		String[] regexes = { "bla[0-9]", "bla\\d", "bla|zwets", "bl??a" };

		for (String r : regexes) {
			TextPattern t = TextPatternRegex.getAppropriatePattern(r);
			Assert.assertEquals(TextPatternRegex.class, t.getClass());
			Assert.assertEquals(r, ((TextPatternRegex) t).getValue());
		}
	}

}
