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

public class TestSpansRepetition {
	@Test
	public void testRepetition() throws IOException {
		int[] aDoc = new int[] { 1, 1, 1, 1, 1, 1 };
		int[] aStart = new int[] { 1, 2, 3, 5, 6, 10 };
		int[] aEnd = new int[] { 2, 3, 4, 6, 7, 11 };
		Spans a = new SpansStub(aDoc, aStart, aEnd);

		SpansRepetition spans = new SpansRepetition(a, 2, 4);

		Assert.assertTrue(spans.next());
		Assert.assertEquals(1, spans.doc());
		Assert.assertEquals(1, spans.start());
		Assert.assertEquals(3, spans.end());

		Assert.assertTrue(spans.next());
		Assert.assertEquals(1, spans.doc());
		Assert.assertEquals(1, spans.start());
		Assert.assertEquals(4, spans.end());

		Assert.assertTrue(spans.next());
		Assert.assertEquals(1, spans.doc());
		Assert.assertEquals(2, spans.start());
		Assert.assertEquals(4, spans.end());

		Assert.assertTrue(spans.next());
		Assert.assertEquals(1, spans.doc());
		Assert.assertEquals(5, spans.start());
		Assert.assertEquals(7, spans.end());

		// Done
		Assert.assertFalse(spans.next());
	}
}
