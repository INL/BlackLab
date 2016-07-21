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

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.MockHits;
import nl.inl.blacklab.MockSearcher;

public class TestHitsSample {

	private final static int[] aDoc   = new int[] { 1, 1, 2, 3, 3, 3 };
	private final static int[] aStart = new int[] { 1, 4, 2, 1, 3, 5 };
	private final static int[] aEnd   = new int[] { 2, 5, 3, 2, 4, 7 };

	private static void assertSampleRatio(int[] expected, float ratio, long seed) {
		Hits hits = HitsSample.fromHits(new MockHits(new MockSearcher(), aDoc, aStart, aEnd), ratio, seed);
		int i = 0;
		Assert.assertEquals(expected.length, hits.size());
		for (Hit hit: hits) {
			Assert.assertEquals(aDoc[expected[i]],   hit.doc);
			Assert.assertEquals(aStart[expected[i]], hit.start);
			Assert.assertEquals(aEnd[expected[i]],   hit.end);
			i++;
		}
	}

	private static void assertSampleNumber(int[] expected, int number, long seed) {
		Hits hits = HitsSample.fromHits(new MockHits(new MockSearcher(), aDoc, aStart, aEnd), number, seed);
		int i = 0;
		Assert.assertEquals(expected.length, hits.size());
		for (Hit hit: hits) {
			Assert.assertEquals(aDoc[expected[i]],   hit.doc);
			Assert.assertEquals(aStart[expected[i]], hit.start);
			Assert.assertEquals(aEnd[expected[i]],   hit.end);
			i++;
		}
	}

	@Test
	public void testSample() {
		assertSampleRatio(new int[0], 0, 0);
		assertSampleRatio(new int[] {3}, 0.1667f, 1);
		assertSampleRatio(new int[] {0, 1, 5}, 0.5f, 1337);
		assertSampleRatio(new int[] {0, 1, 2, 3}, 0.6667f, 42);
		assertSampleRatio(new int[] {0, 1, 2, 3, 4, 5}, 1f, Long.MAX_VALUE);

		assertSampleNumber(new int[0], 0, 0);
		assertSampleNumber(new int[] {3}, 1, 1);
		assertSampleNumber(new int[] {0, 1, 5}, 3, 1337);
		assertSampleNumber(new int[] {0, 1, 2, 3}, 4, 42);
		assertSampleNumber(new int[] {0, 1, 2, 3, 4, 5}, 6, Long.MAX_VALUE);
	}

}
