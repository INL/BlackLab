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

import org.junit.Before;
import org.junit.Test;

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;
import nl.inl.blacklab.mocks.MockSpansInBuckets;

public class TestSpansInBucketsPerStartPoint {
    private SpansInBuckets hpd;

    @Before
    public void setUp() {
        int[] doc = { 1, 1, 1, 2, 2, 2 };
        int[] start = { 1, 1, 3, 3, 5, 5 };
        int[] end = { 2, 3, 4, 5, 6, 7 };
        BLSpans spans = new MockSpans(doc, start, end);
        hpd = new SpansInBucketsPerStartPoint(spans);
    }

    @Test
    public void testListInterface() throws IOException {

        int[] bDoc = { 1, 1, 2, 2 };
        int[] bStart = { 0, 2, 3, 4 };

        int[] hStart = { 1, 1, 3, 3, 5, 5 };
        int[] hEnd = { 2, 3, 4, 5, 6, 7 };

        SpansInBuckets exp = new MockSpansInBuckets(bDoc, bStart, hStart, hEnd);
        TestUtil.assertEquals(exp, hpd);
    }

}
