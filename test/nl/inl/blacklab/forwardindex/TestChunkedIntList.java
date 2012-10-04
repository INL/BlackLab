/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.forwardindex;

import java.util.Arrays;
import java.util.List;

import junit.framework.Assert;

import org.junit.Test;

public class TestChunkedIntList {
	@Test
	public void testEmpty() {
		List<Integer> l = new ChunkedIntList();
		Assert.assertEquals(0, l.size());
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testGetNegative() {
		List<Integer> l = new ChunkedIntList();
		l.get(-1);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testGetOutOfBounds() {
		List<Integer> l = new ChunkedIntList();
		l.get(0);
	}

	@Test
	public void testAdd() {
		List<Integer> l = new ChunkedIntList();
		l.add(42);
		Assert.assertEquals((Integer) 42, l.get(0));
		Assert.assertEquals(1, l.size());
	}

	@Test
	public void testFromCollection() {
		List<Integer> l = getFibList();
		Assert.assertEquals((Integer) 1, l.get(0));
		Assert.assertEquals((Integer) 1, l.get(1));
		Assert.assertEquals((Integer) 2, l.get(2));
		Assert.assertEquals((Integer) 3, l.get(3));
		Assert.assertEquals((Integer) 5, l.get(4));
		Assert.assertEquals(5, l.size());
	}

	@Test
	public void testAddBeyondChunk() {
		List<Integer> l = getFibList(3);
		Assert.assertEquals((Integer) 1, l.get(0));
		Assert.assertEquals((Integer) 1, l.get(1));
		Assert.assertEquals((Integer) 2, l.get(2));
		Assert.assertEquals((Integer) 3, l.get(3));
		Assert.assertEquals((Integer) 5, l.get(4));
		Assert.assertEquals(5, l.size());
	}

	@Test
	public void testAddMiddle() {
		List<Integer> l = getFibList();
		l.add(1, 42);
		Assert.assertEquals((Integer) 1, l.get(0));
		Assert.assertEquals((Integer) 42, l.get(1));
		Assert.assertEquals((Integer) 1, l.get(2));
		Assert.assertEquals((Integer) 2, l.get(3));
		Assert.assertEquals((Integer) 3, l.get(4));
		Assert.assertEquals((Integer) 5, l.get(5));
		Assert.assertEquals(6, l.size());
	}

	@Test
	public void testAddMiddleSmallChunk() {
		List<Integer> l = getFibList(3);
		l.add(1, 42);
		Assert.assertEquals((Integer) 1, l.get(0));
		Assert.assertEquals((Integer) 42, l.get(1));
		Assert.assertEquals((Integer) 1, l.get(2));
		Assert.assertEquals((Integer) 2, l.get(3));
		Assert.assertEquals((Integer) 3, l.get(4));
		Assert.assertEquals((Integer) 5, l.get(5));
		Assert.assertEquals(6, l.size());
	}

	private List<Integer> getFibList(int chunkSize) {
		List<Integer> l = new ChunkedIntList(Arrays.asList(1, 1, 2, 3, 5), chunkSize);
		return l;
	}

	private List<Integer> getFibList() {
		List<Integer> l = new ChunkedIntList(Arrays.asList(1, 1, 2, 3, 5));
		return l;
	}

	@Test
	public void testRemove() {
		List<Integer> l = getFibList();
		l.remove(3);
		Assert.assertEquals((Integer) 1, l.get(0));
		Assert.assertEquals((Integer) 1, l.get(1));
		Assert.assertEquals((Integer) 2, l.get(2));
		Assert.assertEquals((Integer) 5, l.get(3));
		Assert.assertEquals(4, l.size());
	}

	@Test
	public void testRemoveSmallChunk() {
		List<Integer> l = getFibList(3);
		l.remove(1);
		l.remove(2);
		Assert.assertEquals((Integer) 1, l.get(0));
		Assert.assertEquals((Integer) 2, l.get(1));
		Assert.assertEquals((Integer) 5, l.get(2));
		Assert.assertEquals(3, l.size());
	}

}
