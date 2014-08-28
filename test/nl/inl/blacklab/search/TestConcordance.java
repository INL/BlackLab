package nl.inl.blacklab.search;

import junit.framework.Assert;

import org.junit.Test;

public class TestConcordance {

	@SuppressWarnings("deprecation")
	@Test
	public void testConcordanceOldConstructor() {
		Concordance c = new Concordance(new String[] {"the quick ", "brown", " fox"});
		Assert.assertEquals("the quick ", c.left);
		Assert.assertEquals("brown", c.hit);
		Assert.assertEquals(" fox", c.right);
		Assert.assertEquals("the quick ", c.left());
		Assert.assertEquals("brown", c.match());
		Assert.assertEquals(" fox", c.right());
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testConcordanceNewConstructor() {
		Concordance c = new Concordance("the quick brown fox", 10, 15);
		Assert.assertEquals("the quick ", c.left);
		Assert.assertEquals("brown", c.hit);
		Assert.assertEquals(" fox", c.right);
		Assert.assertEquals("the quick ", c.left());
		Assert.assertEquals("brown", c.match());
		Assert.assertEquals(" fox", c.right());
	}
}
