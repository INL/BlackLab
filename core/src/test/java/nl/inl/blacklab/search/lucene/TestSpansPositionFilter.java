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

public class TestSpansPositionFilter {

    private static SpansPositionFilter getSpans(boolean swap, SpanQueryPositionFilter.Operation type, boolean invert) {
        BLSpans a = MockSpans.fromLists(
                new int[] { 0, 1, 1, 2, 2, 2, 3 },
                new int[] { 10, 10, 20, 10, 10, 30, 20 },
                new int[] { 20, 15, 25, 15, 20, 35, 25 });
        BLSpans b = MockSpans.fromLists(
                new int[] { 1, 2, 3 },
                new int[] { 11, 22, 20 },
                new int[] { 12, 23, 25 });
        if (swap) {
            BLSpans c = a;
            a = b;
            b = c;
        }
        SpansInBuckets bb = new SpansInBucketsPerDocument(b);
        return new SpansPositionFilter(a, bb, false, type, invert, 0, 0);
    }

    @Test
    public void testContaining() throws IOException {
        SpansPositionFilter spans = getSpans(false, SpanQueryPositionFilter.Operation.CONTAINING, false);
        Spans exp = MockSpans.fromLists(
                new int[] { 1, 3 },
                new int[] { 10, 20 },
                new int[] { 15, 25 });
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testNotContaining() throws IOException {
        SpansPositionFilter spans = getSpans(false, SpanQueryPositionFilter.Operation.CONTAINING, true);
        Spans exp = MockSpans.fromLists(
                new int[] { 0, 1, 2, 2, 2 },
                new int[] { 10, 20, 10, 10, 30 },
                new int[] { 20, 25, 15, 20, 35 });
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testWithin() throws IOException {
        SpansPositionFilter spans = getSpans(true, SpanQueryPositionFilter.Operation.WITHIN, false);
        Spans exp = MockSpans.fromLists(
                new int[] { 1, 3 },
                new int[] { 11, 20 },
                new int[] { 12, 25 });
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testNotWithin() throws IOException {
        SpansPositionFilter spans = getSpans(true, SpanQueryPositionFilter.Operation.WITHIN, true);
        Spans exp = MockSpans.fromLists(
                new int[] { 2 },
                new int[] { 22 },
                new int[] { 23 });
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testStartsAt() throws IOException {
        SpansPositionFilter spans = getSpans(false, SpanQueryPositionFilter.Operation.STARTS_AT, false);
        Spans exp = MockSpans.fromLists(
                new int[] { 3 },
                new int[] { 20 },
                new int[] { 25 });
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testEndsAt() throws IOException {
        SpansPositionFilter spans = getSpans(false, SpanQueryPositionFilter.Operation.ENDS_AT, false);
        Spans exp = MockSpans.fromLists(
                new int[] { 3 },
                new int[] { 20 },
                new int[] { 25 });
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testMatches() throws IOException {
        SpansPositionFilter spans = getSpans(false, SpanQueryPositionFilter.Operation.MATCHES, false);
        Spans exp = MockSpans.fromLists(
                new int[] { 3 },
                new int[] { 20 },
                new int[] { 25 });
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testNotMatches() throws IOException {
        SpansPositionFilter spans = getSpans(true, SpanQueryPositionFilter.Operation.MATCHES, true);
        Spans exp = MockSpans.fromLists(
                new int[] { 1, 2 },
                new int[] { 11, 22 },
                new int[] { 12, 23 });
        TestUtil.assertEquals(exp, spans);
    }

}
