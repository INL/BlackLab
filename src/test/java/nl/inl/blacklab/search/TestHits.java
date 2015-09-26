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
package nl.inl.blacklab.search;

import org.apache.lucene.search.spans.Spans;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.MockSpans;

public class TestHits {

	@Test
	public void testHitsIterate() {
		int[] aDoc = new int[] { 1, 2 };
		int[] aStart = new int[] { 1, 2 };
		int[] aEnd = new int[] { 2, 3 };
		Spans a = new MockSpans(aDoc, aStart, aEnd);

		@SuppressWarnings("deprecation")
		Hits hits = new Hits(null, null, a);

		int i = 0;
		for (Hit hit: hits) {
			Assert.assertEquals(aDoc[i], hit.doc);
			Assert.assertEquals(aStart[i], hit.start);
			Assert.assertEquals(aEnd[i], hit.end);
			i++;
		}
	}

}
