/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import junit.framework.Assert;

import org.junit.Test;

public class TestChunkedList {
	@Test
	public void testEmpty() {
		List<String> l = new ChunkedList<String>();
		Assert.assertEquals(0, l.size());
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testGetNegative() {
		List<String> l = new ChunkedList<String>();
		l.get(-1);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testGetOutOfBounds() {
		List<String> l = new ChunkedList<String>();
		l.get(0);
	}

	@Test
	public void testAdd() {
		List<String> l = new ChunkedList<String>();
		l.add("42");
		Assert.assertEquals("42", l.get(0));
		Assert.assertEquals(1, l.size());
	}

	@Test
	public void testFromCollection() {
		List<String> l = getFibList();
		Assert.assertEquals("The", l.get(0));
		Assert.assertEquals("quick", l.get(1));
		Assert.assertEquals("brown", l.get(2));
		Assert.assertEquals("fox", l.get(3));
		Assert.assertEquals("jumps", l.get(4));
		Assert.assertEquals(5, l.size());
	}

	@Test
	public void testIterate() {
		List<String> l = getFibList();
		Iterator<String> it = l.iterator();
		Assert.assertTrue(it.hasNext());
		Assert.assertEquals("The", it.next());
		Assert.assertTrue(it.hasNext());
		Assert.assertEquals("quick", it.next());
		Assert.assertTrue(it.hasNext());
		Assert.assertEquals("brown", it.next());
		Assert.assertTrue(it.hasNext());
		Assert.assertEquals("fox", it.next());
		Assert.assertTrue(it.hasNext());
		Assert.assertEquals("jumps", it.next());
		Assert.assertFalse(it.hasNext());
	}

	@Test
	public void testAddBeyondChunk() {
		List<String> l = getFibList(3);
		Assert.assertEquals("The", l.get(0));
		Assert.assertEquals("quick", l.get(1));
		Assert.assertEquals("brown", l.get(2));
		Assert.assertEquals("fox", l.get(3));
		Assert.assertEquals("jumps", l.get(4));
		Assert.assertEquals(5, l.size());
	}

	@Test
	public void testAddMiddle() {
		List<String> l = getFibList();
		l.add(1, "really");
		Assert.assertEquals("The", l.get(0));
		Assert.assertEquals("really", l.get(1));
		Assert.assertEquals("quick", l.get(2));
		Assert.assertEquals("brown", l.get(3));
		Assert.assertEquals("fox", l.get(4));
		Assert.assertEquals("jumps", l.get(5));
		Assert.assertEquals(6, l.size());
	}

	@Test
	public void testAddMiddleSmallChunk() {
		List<String> l = getFibList(3);
		l.add(1, "really");
		Assert.assertEquals("The", l.get(0));
		Assert.assertEquals("really", l.get(1));
		Assert.assertEquals("quick", l.get(2));
		Assert.assertEquals("brown", l.get(3));
		Assert.assertEquals("fox", l.get(4));
		Assert.assertEquals("jumps", l.get(5));
		Assert.assertEquals(6, l.size());
	}

	private List<String> getFibList(int chunkSize) {
		List<String> l = new ChunkedList<String>(Arrays.asList("The", "quick", "brown", "fox", "jumps"), chunkSize);
		return l;
	}

	private List<String> getFibList() {
		List<String> l = new ChunkedList<String>(Arrays.asList("The", "quick", "brown", "fox", "jumps"));
		return l;
	}

	@Test
	public void testRemove() {
		List<String> l = getFibList();
		l.remove(3);
		Assert.assertEquals("The", l.get(0));
		Assert.assertEquals("quick", l.get(1));
		Assert.assertEquals("brown", l.get(2));
		Assert.assertEquals("jumps", l.get(3));
		Assert.assertEquals(4, l.size());
	}

	@Test
	public void testRemoveSmallChunk() {
		List<String> l = getFibList(3);
		l.remove(1);
		l.remove(2);
		Assert.assertEquals("The", l.get(0));
		Assert.assertEquals("brown", l.get(1));
		Assert.assertEquals("jumps", l.get(2));
		Assert.assertEquals(3, l.size());
	}

}
