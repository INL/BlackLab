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

import org.junit.Test;

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;

public class TestSpansRepetition {
    @Test
    public void testRepetition() throws IOException {
        int[] aDoc = { 1, 1, 1, 1, 1, 1 };
        int[] aStart = { 1, 2, 3, 5, 6, 10 };
        int[] aEnd = { 2, 3, 4, 6, 7, 11 };
        BLSpans a = new MockSpans(aDoc, aStart, aEnd);

        SpansRepetition spans = new SpansRepetition(a, 2, 4);

        int[] expDoc = { 1, 1, 1, 1 };
        int[] expStart = { 1, 1, 2, 5 };
        int[] expEnd = { 3, 4, 4, 7 };
        BLSpans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans);
    }
}
