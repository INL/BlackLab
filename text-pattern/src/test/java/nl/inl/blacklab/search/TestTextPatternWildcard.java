package nl.inl.blacklab.search;

import org.junit.Test;

public class TestTextPatternWildcard {

    @Test
    public void testBla() {
        // OK
    }

//	@Test
//	public void testGetAppropriatePatternSimple() {
//		TextPattern t = TextPatternWildcard.getAppropriatePattern("blabla");
//		Assert.assertEquals(TextPatternTerm.class, t.getClass());
//		Assert.assertEquals("blabla", ((TextPatternTerm) t).getValue());
//	}
//
//	@Test
//	public void testGetAppropriatePatternPrefix() {
//		TextPattern t = TextPatternWildcard.getAppropriatePattern("blabla*");
//		Assert.assertEquals(TextPatternPrefix.class, t.getClass());
//		Assert.assertEquals("blabla", ((TextPatternPrefix) t).getValue());
//	}
//
//	@Test
//	public void testGetAppropriatePatternWildcard() {
//		TextPattern t = TextPatternWildcard.getAppropriatePattern("*bla");
//		Assert.assertEquals(TextPatternWildcard.class, t.getClass());
//		Assert.assertEquals("*bla", ((TextPatternWildcard) t).getValue());
//
//		t = TextPatternWildcard.getAppropriatePattern("*bl??a*");
//		Assert.assertEquals(TextPatternWildcard.class, t.getClass());
//		Assert.assertEquals("*bl??a*", ((TextPatternWildcard) t).getValue());
//	}

}
