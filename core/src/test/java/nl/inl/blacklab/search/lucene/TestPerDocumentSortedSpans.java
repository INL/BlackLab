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

public class TestPerDocumentSortedSpans {
    private PerDocumentSortedSpans hpd;

    @Before
    public void setUp() {
        int[] doc = { 1, 1, 1, 2, 2 };
        int[] start = { 1, 1, 4, 2, 2 };
        int[] end = { 8, 6, 5, 4, 3 };
        BLSpans spans = new MockSpans(doc, start, end);
        hpd = PerDocumentSortedSpans.endPoint(spans);
    }

    @Test
    public void testNormal() throws IOException {
        int[] doc = { 1, 1, 1, 2, 2 };
        int[] start = { 4, 1, 1, 2, 2 };
        int[] end = { 5, 6, 8, 3, 4 };
        BLSpans exp = new MockSpans(doc, start, end);
        TestUtil.assertEquals(exp, hpd);
    }

}
