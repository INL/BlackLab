/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import junit.framework.Assert;

import org.apache.lucene.search.spans.Spans;
import org.junit.Test;

public class TestSpansAnd {
	@Test
	public void testAndSpans() throws IOException {
		int[] aDoc = new int[] { 1, 1, 2, 2, 2, 3 };
		int[] aStart = new int[] { 10, 20, 10, 10, 30, 10 };
		int[] aEnd = new int[] { 15, 25, 15, 20, 35, 15 };
		Spans a = new SpansStub(aDoc, aStart, aEnd);

		int[] bDoc = new int[] { 1, 2, 2, 3 };
		int[] bStart = new int[] { 10, 10, 20, 20 };
		int[] bEnd = new int[] { 15, 20, 25, 25 };
		Spans b = new SpansStub(bDoc, bStart, bEnd);

		SpansAnd andSpans = new SpansAnd(a, b);

		// First hit
		Assert.assertTrue(andSpans.next());
		Assert.assertEquals(1, andSpans.doc());
		Assert.assertEquals(10, andSpans.start());
		Assert.assertEquals(15, andSpans.end());

		// Second hit
		Assert.assertTrue(andSpans.next());
		Assert.assertEquals(2, andSpans.doc());
		Assert.assertEquals(10, andSpans.start());
		Assert.assertEquals(20, andSpans.end());

		// Done
		Assert.assertFalse(andSpans.next());
	}
}
