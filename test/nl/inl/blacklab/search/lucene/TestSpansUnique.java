/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import junit.framework.Assert;

import org.apache.lucene.search.spans.Spans;
import org.junit.Test;

public class TestSpansUnique {
	@Test
	public void test() throws IOException {
		int[] aDoc = new int[] { 1, 1, 2, 3, 3 };
		int[] aStart = new int[] { 10, 10, 10, 1, 1 };
		int[] aEnd = new int[] { 11, 11, 11, 2, 2 };
		Spans a = new SpansStub(aDoc, aStart, aEnd);

		Spans spans = new SpansUnique(a);

		// First hit
		Assert.assertTrue(spans.next());
		Assert.assertEquals(1, spans.doc());
		Assert.assertEquals(10, spans.start());
		Assert.assertEquals(11, spans.end());

		// Second hit
		Assert.assertTrue(spans.next());
		Assert.assertEquals(2, spans.doc());
		Assert.assertEquals(10, spans.start());
		Assert.assertEquals(11, spans.end());

		// Third hit
		Assert.assertTrue(spans.next());
		Assert.assertEquals(3, spans.doc());
		Assert.assertEquals(1, spans.start());
		Assert.assertEquals(2, spans.end());

		// Done
		Assert.assertFalse(spans.next());
	}
}
