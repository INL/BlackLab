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

public class TestSpansTags {
	@Test
	public void test() throws IOException {
		int[] aDoc = new int[] { 1, 2, 2 };
		int[] aStart = new int[] { 10, 1, 4 };
		int[] aEnd = new int[] { 11, 2, 5 };
		Spans a = new SpansStub(aDoc, aStart, aEnd);

		int[] bDoc = new int[] { 1, 2, 2 };
		int[] bStart = new int[] { 21, 2, 6 };
		int[] bEnd = new int[] { 22, 3, 7 };
		Spans b = new SpansStub(bDoc, bStart, bEnd);

		SpansTags spansTags = new SpansTags(a, b);

		// First hit
		Assert.assertTrue(spansTags.next());
		Assert.assertEquals(1, spansTags.doc());
		Assert.assertEquals(10, spansTags.start());
		Assert.assertEquals(21, spansTags.end());

		// Second hit
		Assert.assertTrue(spansTags.next());
		Assert.assertEquals(2, spansTags.doc());
		Assert.assertEquals(1, spansTags.start());
		Assert.assertEquals(2, spansTags.end());

		// Third hit
		Assert.assertTrue(spansTags.next());
		Assert.assertEquals(2, spansTags.doc());
		Assert.assertEquals(4, spansTags.start());
		Assert.assertEquals(6, spansTags.end());

		// Done
		Assert.assertFalse(spansTags.next());
	}
}
