/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import junit.framework.Assert;

import org.apache.lucene.search.spans.Spans;
import org.junit.Before;
import org.junit.Test;

public class TestSpansLimiter {
	private Spans results;

	@Before
	public void setUp() {
		results = new SpansLimiter(HelpersForTests.getSimpleResults(10), 2, 3);
	}

	@Test
	public void testIterate() throws IOException {
		Assert.assertTrue(results.next());
		Assert.assertEquals(7, results.doc());
		Assert.assertTrue(results.next());
		Assert.assertEquals(10, results.doc());
		Assert.assertTrue(results.next());
		Assert.assertEquals(13, results.doc());
		Assert.assertFalse(results.next());
		Assert.assertFalse(results.next());
	}

}
