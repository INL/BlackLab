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
package nl.inl.blacklab.search.sequences;

import java.io.IOException;

import nl.inl.blacklab.search.lucene.SpansStub;

import org.apache.lucene.search.spans.Spans;
import org.junit.Assert;
import org.junit.Test;

public class TestSpansExpansion {
	@Test
	public void testExpansionRight() throws IOException {
		int[] aDoc = new int[] { 1, 2 };
		int[] aStart = new int[] { 1, 2 };
		int[] aEnd = new int[] { 2, 3 };
		Spans a = new SpansStub(aDoc, aStart, aEnd);

		SpansExpansionRaw spans = new SpansExpansionRaw(false, null, "test", a, false, 1, 2);
		spans.setTest(true); // for test, all documents are 5 long

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
	public void testExpansionRightInfinite() throws IOException {
		int[] aDoc = new int[] { 1, 1, 1 };
		int[] aStart = new int[] { 0, 3, 4 };
		int[] aEnd = new int[] { 1, 4, 5 };
		Spans a = new SpansStub(aDoc, aStart, aEnd);

		SpansExpansionRaw spans = new SpansExpansionRaw(false, null, "test", a, false, 1, -1);
		spans.setTest(true); // for test, all documents are 5 long

		Assert.assertTrue(spans.next());
		Assert.assertEquals(1, spans.doc());
		Assert.assertEquals(0, spans.start());
		Assert.assertEquals(2, spans.end());

		Assert.assertTrue(spans.next());
		Assert.assertEquals(1, spans.doc());
		Assert.assertEquals(0, spans.start());
		Assert.assertEquals(3, spans.end());

		Assert.assertTrue(spans.next());
		Assert.assertEquals(1, spans.doc());
		Assert.assertEquals(0, spans.start());
		Assert.assertEquals(4, spans.end());

		Assert.assertTrue(spans.next());
		Assert.assertEquals(1, spans.doc());
		Assert.assertEquals(0, spans.start());
		Assert.assertEquals(5, spans.end());

		Assert.assertTrue(spans.next());
		Assert.assertEquals(1, spans.doc());
		Assert.assertEquals(3, spans.start());
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

		SpansExpansionRaw spans = new SpansExpansionRaw(false, null, "test", a, true, 2, 3);

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
