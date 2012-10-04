/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.sequences;

import java.io.IOException;

import junit.framework.Assert;
import nl.inl.blacklab.search.lucene.SpansStub;

import org.apache.lucene.search.spans.Spans;
import org.junit.Test;

public class TestSpansExpansion {
	@Test
	public void testExpansionRight() throws IOException {
		int[] aDoc = new int[] { 1, 2 };
		int[] aStart = new int[] { 1, 2 };
		int[] aEnd = new int[] { 2, 3 };
		Spans a = new SpansStub(aDoc, aStart, aEnd);

		SpansExpansionRaw spans = new SpansExpansionRaw(a, false, 1, 2);

		Assert.assertTrue(spans.next());
		Assert.assertEquals(1, spans.doc());
		Assert.assertEquals(1, spans.start());
		Assert.assertEquals(3, spans.end());

		Assert.assertTrue(spans.next());
		Assert.assertEquals(1, spans.doc());
		Assert.assertEquals(1, spans.start());
		Assert.assertEquals(4, spans.end());

		Assert.assertTrue(spans.next());
		Assert.assertEquals(2, spans.doc());
		Assert.assertEquals(2, spans.start());
		Assert.assertEquals(4, spans.end());

		Assert.assertTrue(spans.next());
		Assert.assertEquals(2, spans.doc());
		Assert.assertEquals(2, spans.start());
		Assert.assertEquals(5, spans.end());

		// Done
		Assert.assertFalse(spans.next());
	}

	@Test
	public void testExpansionLeft() throws IOException {
		int[] aDoc = new int[] { 1, 2 };
		int[] aStart = new int[] { 1, 2 };
		int[] aEnd = new int[] { 2, 3 };
		Spans a = new SpansStub(aDoc, aStart, aEnd);

		SpansExpansionRaw spans = new SpansExpansionRaw(a, true, 2, 3);

		// NOTE: first hit cannot be expanded at all (minimum expansion would set start to -1),
		// second hit can only be expanded once.

		Assert.assertTrue(spans.next());
		Assert.assertEquals(2, spans.doc());
		Assert.assertEquals(0, spans.start());
		Assert.assertEquals(3, spans.end());

		// Done
		Assert.assertFalse(spans.next());
	}
}
