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

import nl.inl.blacklab.mocks.MockBlackLabIndex;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.SampleParameters;

public class TestHitsSample {

    private final static int[] A_DOC = { 1, 1, 2, 3, 3, 3 };
    private final static int[] A_START = { 1, 4, 2, 1, 3, 5 };
    private final static int[] A_END = { 2, 5, 3, 2, 4, 7 };

    private static void assertSample(int[] expected, SampleParameters param) {
        try (MockBlackLabIndex index = new MockBlackLabIndex()) {
            Hits hits = Hits.fromArrays(index.createDefaultQueryInfo(), A_DOC, A_START, A_END).sample(param);
            int i = 0;
            Assert.assertEquals(expected.length, hits.size());
            for (Hit hit : hits) {
                Assert.assertEquals(A_DOC[expected[i]], hit.doc());
                Assert.assertEquals(A_START[expected[i]], hit.start());
                Assert.assertEquals(A_END[expected[i]], hit.end());
                i++;
            }
        }
    }

    @Test
    public void testSample() {
        assertSample(new int[0], SampleParameters.percentage(0, 0));
        assertSample(new int[] { 3 }, SampleParameters.percentage(0.1667f, 1));
        assertSample(new int[] { 0, 1, 5 }, SampleParameters.percentage(0.5f, 1337));
        assertSample(new int[] { 0, 1, 2, 3 }, SampleParameters.percentage(0.6667f, 42));
        assertSample(new int[] { 0, 1, 2, 3, 4, 5 }, SampleParameters.percentage(1f, Long.MAX_VALUE));

        assertSample(new int[0], SampleParameters.fixedNumber(0, 0));
        assertSample(new int[] { 3 }, SampleParameters.fixedNumber(1, 1));
        assertSample(new int[] { 0, 1, 5 }, SampleParameters.fixedNumber(3, 1337));
        assertSample(new int[] { 0, 1, 2, 3 }, SampleParameters.fixedNumber(4, 42));
        assertSample(new int[] { 0, 1, 2, 3, 4, 5 }, SampleParameters.fixedNumber(6, Long.MAX_VALUE));
    }

}
