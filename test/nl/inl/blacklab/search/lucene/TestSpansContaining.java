/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import junit.framework.Assert;

import org.apache.lucene.search.spans.Spans;
import org.junit.Test;

public class TestSpansContaining {
	@Test
	public void test() throws IOException {
		int[] aDoc = new int[] { 1, 1, 2, 2, 2, 3 };
		int[] aStart = new int[] { 10, 20, 10, 10, 30, 20 };
		int[] aEnd = new int[] { 15, 25, 15, 20, 35, 25 };
		Spans a = new SpansStub(aDoc, aStart, aEnd);

		int[] bDoc = new int[] { 1, 2, 3 };
		int[] bStart = new int[] { 11, 22, 20 };
		int[] bEnd = new int[] { 12, 23, 25 };
		Spans b = new SpansStub(bDoc, bStart, bEnd);

		SpansContaining spansContaining = new SpansContaining(a, b);

		// First hit
		Assert.assertTrue(spansContaining.next());
		Assert.assertEquals(1, spansContaining.doc());
		Assert.assertEquals(10, spansContaining.start());
		Assert.assertEquals(15, spansContaining.end());

		// Second hit
		Assert.assertTrue(spansContaining.next());
		Assert.assertEquals(3, spansContaining.doc());
		Assert.assertEquals(20, spansContaining.start());
		Assert.assertEquals(25, spansContaining.end());

		// Done
		Assert.assertFalse(spansContaining.next());
	}
}
