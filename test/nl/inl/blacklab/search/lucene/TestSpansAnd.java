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
