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

public class TestSpansSequence {
	@Test
	public void testSequenceSpansSimple() throws IOException {
		int[] aDoc = new int[] { 1, 1, 1 };
		int[] aStart = new int[] { 1, 1, 2 };
		int[] aEnd = new int[] { 2, 3, 4 };
		Spans a = new SpansStub(aDoc, aStart, aEnd);

		int[] bDoc = new int[] { 1, 1, 1 };
		int[] bStart = new int[] { 2, 2, 3 };
		int[] bEnd = new int[] { 4, 5, 4 };
		Spans b = new SpansStub(bDoc, bStart, bEnd);

		SpansSequenceRaw spans = new SpansSequenceRaw(a, b);

		// First hit
		Assert.assertTrue(spans.next());
		Assert.assertEquals(1, spans.doc());
		Assert.assertEquals(1, spans.start());
		Assert.assertEquals(4, spans.end());

		// Second hit
		Assert.assertTrue(spans.next());
		Assert.assertEquals(1, spans.doc());
		Assert.assertEquals(1, spans.start());
		Assert.assertEquals(5, spans.end());

		// Third hit
		Assert.assertTrue(spans.next());
		Assert.assertEquals(1, spans.doc());
		Assert.assertEquals(1, spans.start());
		Assert.assertEquals(4, spans.end());

		// Done
		Assert.assertFalse(spans.next());
	}

	/**
	 * This test exists to guard against reoccurence of a particular bug: When the right spans was
	 * further along inside a document than the left spans, the left spans would be advanced to
	 * catch up with the right one. However, during this advancing, the document id was not checked,
	 * so left could actually end up in another document than right, potentially skipping hits or
	 * even generating false hits.
	 *
	 * @throws IOException
	 */
	@Test
	public void testSequenceSpansAdvanceBeyondDocError() throws IOException {
		int[] aDoc = new int[] { 1, 2, 2 };
		int[] aStart = new int[] { 1, 2, 4 };
		int[] aEnd = new int[] { 2, 3, 5 };
		Spans a = new SpansStub(aDoc, aStart, aEnd);

		int[] bDoc = new int[] { 1, 2 };
		int[] bStart = new int[] { 5, 3 };
		int[] bEnd = new int[] { 6, 4 };
		Spans b = new SpansStub(bDoc, bStart, bEnd);

		SpansSequenceRaw spans = new SpansSequenceRaw(a, b);

		// First hit
		Assert.assertTrue(spans.next());
		Assert.assertEquals(2, spans.doc());
		Assert.assertEquals(2, spans.start());
		Assert.assertEquals(4, spans.end());

		// Done
		Assert.assertFalse(spans.next());
	}
}
