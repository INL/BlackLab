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

public class TestSpansTags {

    @Test
    public void test() throws IOException {
        int[] aDoc = { 1, 2, 2 };
        int[] aStart = { 10, 1, 4 };
        int[] aEnd = { 21, 2, 6 };
        BLSpans a = MockSpans.withEndInPayload(aDoc, aStart, aEnd);
        Spans spans = new SpansTags(a);

        int[] expDoc = { 1, 2, 2 };
        int[] expStart = { 10, 1, 4 };
        int[] expEnd = { 21, 2, 6 };
        Spans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testNested() throws IOException {
        int[] aDoc = { 1, 1 };
        int[] aStart = { 2, 4 };
        int[] aEnd = { 7, 5 };
        BLSpans a = MockSpans.withEndInPayload(aDoc, aStart, aEnd);

        Spans spans = new SpansTags(a);

        int[] expDoc = { 1, 1 };
        int[] expStart = { 2, 4 };
        int[] expEnd = { 7, 5 };
        Spans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans);
    }

    /**
     * Test the case where there's an empty tag between two tokens.
     *
     * E.g.: <code>quick &lt;b&gt;&lt;/b&gt; brown</code>
     *
     * @throws IOException
     */
    @Test
    public void testEmptyTag() throws IOException {
        int[] aDoc = { 1, 1 };
        int[] aStart = { 2, 4 };
        int[] aEnd = { 2, 7 };
        BLSpans a = MockSpans.withEndInPayload(aDoc, aStart, aEnd);

        Spans spans = new SpansTags(a);

        int[] expDoc = { 1, 1 };
        int[] expStart = { 2, 4 };
        int[] expEnd = { 2, 7 };
        Spans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testSkip() throws IOException {
        int[] aDoc = { 1, 1, 2, 2 };
        int[] aStart = { 2, 4, 12, 14 };
        int[] aEnd = { 5, 7, 17, 15 };
        BLSpans a = MockSpans.withEndInPayload(aDoc, aStart, aEnd);

        Spans spans = new SpansTags(a);
        spans.advance(2);

        int[] expDoc = { 2, 2 };
        int[] expStart = { 12, 14 };
        int[] expEnd = { 17, 15 };
        Spans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans, true);
    }
}
