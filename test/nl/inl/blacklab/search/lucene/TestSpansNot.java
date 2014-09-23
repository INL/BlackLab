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
package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import junit.framework.Assert;

import org.apache.lucene.search.spans.Spans;
import org.junit.Test;

public class TestSpansNot {

	@Test
	public void testSpansNot() throws IOException {
		int[] aDoc = new int[]   { 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2 };
		int[] aStart = new int[] { 0, 1, 2, 3, 0, 1, 2, 3, 4, 1, 3 };
		int[] aEnd = new int[]   { 1, 2, 3, 4, 1, 2, 3, 4, 5, 2, 4 };
		Spans a = new SpansStub(aDoc, aStart, aEnd);

		SpansNot spans = new SpansNot(false, null, "test", a);
		spans.setTest(true); // no IndexReader available

		// First hit
		Assert.assertTrue(spans.next());
		Assert.assertEquals(0, spans.doc());
		Assert.assertEquals(4, spans.start());
		Assert.assertEquals(5, spans.end());

		// Second hit
		Assert.assertTrue(spans.next());
		Assert.assertEquals(2, spans.doc());
		Assert.assertEquals(0, spans.start());
		Assert.assertEquals(1, spans.end());

		// Third hit
		Assert.assertTrue(spans.next());
		Assert.assertEquals(2, spans.doc());
		Assert.assertEquals(2, spans.start());
		Assert.assertEquals(3, spans.end());

		// Fourth hit
		Assert.assertTrue(spans.next());
		Assert.assertEquals(2, spans.doc());
		Assert.assertEquals(4, spans.start());
		Assert.assertEquals(5, spans.end());

		// Done
		Assert.assertFalse(spans.next());
	}
}
