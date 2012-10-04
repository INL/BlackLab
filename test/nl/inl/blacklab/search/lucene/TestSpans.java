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

public class TestSpans {
	private Spans results;

	@Before
	public void setUp() {
		results = HelpersForTests.getSimpleResults(3);
	}

	@Test
	public void testNext() throws IOException {
		results.next();
		Assert.assertEquals(1, results.doc());
		results.next();
		Assert.assertEquals(4, results.doc());
		results.next();
		Assert.assertEquals(7, results.doc());
		Assert.assertFalse(results.next());
	}

}
