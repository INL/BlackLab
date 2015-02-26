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
import org.junit.Before;
import org.junit.Test;

public class TestPerDocumentSortedSpans {
	private PerDocumentSortedSpans hpd;

	@Before
	public void setUp() {
		int[] doc = { 1, 1, 1, 2, 2 }, start = { 1, 1, 4, 2, 2 }, end = { 8, 6, 5, 4, 3 };
		Spans spans = new SpansStub(doc, start, end);
		hpd = new PerDocumentSortedSpans(spans, true, false);
	}

	@Test
	public void testNormal() throws IOException {
		int[] doc = { 1, 1, 1, 2, 2 }, start = { 4, 1, 1, 2, 2 }, end = { 5, 6, 8, 3, 4 };
		for (int i = 0; i < doc.length; i++) {
			Assert.assertTrue(hpd.next());
			Assert.assertEquals(doc[i], hpd.doc());
			Assert.assertEquals(start[i], hpd.start());
			Assert.assertEquals(end[i], hpd.end());
		}
		Assert.assertFalse(hpd.next());
	}
}
