/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.sequences;

import java.io.IOException;
import java.util.List;

import junit.framework.Assert;
import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.lucene.SpansStub;

import org.apache.lucene.search.spans.Spans;
import org.junit.Before;
import org.junit.Test;

public class TestSpansInBuckets {
	private SpansInBuckets hpd;

	@Before
	public void setUp() {
		int[] doc = { 1, 4, 4, 5 }, start = { 1, 2, 3, 4 }, end = { 5, 6, 7, 8 };
		Spans spans = new SpansStub(doc, start, end);
		hpd = new SpansInBucketsPerDocument(spans);
	}

	@Test
	public void testNextNormal() throws IOException {
		Assert.assertTrue(hpd.next());
		List<Hit> l = hpd.getHits();
		Assert.assertEquals(1, hpd.doc());
		Assert.assertEquals(1, l.size());
		Assert.assertEquals(1, l.get(0).start);
		Assert.assertEquals(5, l.get(0).end);

		Assert.assertTrue(hpd.next());
		l = hpd.getHits();
		Assert.assertEquals(4, hpd.doc());
		Assert.assertEquals(2, l.size());
		Assert.assertEquals(2, l.get(0).start);
		Assert.assertEquals(6, l.get(0).end);

		Assert.assertTrue(hpd.next());
		l = hpd.getHits();
		Assert.assertEquals(5, hpd.doc());
		Assert.assertEquals(1, l.size());

		Assert.assertFalse(hpd.next());
	}

	@Test
	public void testSkipToFirst() throws IOException {
		Assert.assertTrue(hpd.skipTo(1));
		List<Hit> l = hpd.getHits();
		Assert.assertEquals(1, hpd.doc());
		Assert.assertEquals(1, l.size());
		Assert.assertEquals(1, l.get(0).start);
		Assert.assertEquals(5, l.get(0).end);
	}

	@Test
	public void testSkipToNormal() throws IOException {
		Assert.assertTrue(hpd.skipTo(4));
		Assert.assertEquals(4, hpd.doc());
		Assert.assertEquals(2, hpd.getHits().size());
	}

	@Test
	public void testSkipToNonExistent() throws IOException {
		Assert.assertTrue(hpd.skipTo(2));
		Assert.assertEquals(4, hpd.doc());
	}

	@Test
	public void testSkipToPastEnd() throws IOException {
		Assert.assertFalse(hpd.skipTo(6));
	}
}
