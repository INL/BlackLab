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

import org.apache.lucene.search.spans.Spans;
import org.junit.Test;

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;

public class TestSpansUnique {
    @Test
    public void test() throws IOException {
        int[] aDoc = { 1, 1, 2, 3, 3 };
        int[] aStart = { 10, 10, 10, 1, 1 };
        int[] aEnd = { 11, 11, 11, 2, 2 };
        BLSpans a = new MockSpans(aDoc, aStart, aEnd);

        Spans spans = new SpansUnique(a);

        int[] expDoc = { 1, 2, 3 };
        int[] expStart = { 10, 10, 1 };
        int[] expEnd = { 11, 11, 2 };
        Spans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans);
    }
}
