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
import nl.inl.blacklab.search.lucene.SpanQueryExpansion.Direction;

public class TestSpansExpansion {
    @Test
    public void testExpansionRight() throws IOException {
        int[] aDoc = { 1, 2 };
        int[] aStart = { 1, 2 };
        int[] aEnd = { 2, 3 };
        BLSpans a = new MockSpans(aDoc, aStart, aEnd);

        SpansExpansionRaw spans = new SpansExpansionRaw(null, "test", a, Direction.RIGHT, 1, 2);
        spans.setTest(true); // for test, all documents are 5 long

        int[] expDoc = { 1, 1, 2, 2 };
        int[] expStart = { 1, 1, 2, 2 };
        int[] expEnd = { 3, 4, 4, 5 };
        Spans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testExpansionRightInfinite() throws IOException {
        int[] aDoc = { 1, 1, 1 };
        int[] aStart = { 0, 3, 4 };
        int[] aEnd = { 1, 4, 5 };
        BLSpans a = new MockSpans(aDoc, aStart, aEnd);

        SpansExpansionRaw spans = new SpansExpansionRaw(null, "test", a, Direction.RIGHT, 1, BLSpanQuery.MAX_UNLIMITED);
        spans.setTest(true); // for test, all documents are 5 long

        int[] expDoc = { 1, 1, 1, 1, 1 };
        int[] expStart = { 0, 0, 0, 0, 3 };
        int[] expEnd = { 2, 3, 4, 5, 5 };
        Spans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testExpansionLeft() throws IOException {
        int[] aDoc = { 1, 2 };
        int[] aStart = { 1, 2 };
        int[] aEnd = { 2, 3 };
        BLSpans a = new MockSpans(aDoc, aStart, aEnd);

        SpansExpansionRaw spans = new SpansExpansionRaw(null, "test", a, Direction.LEFT, 2, 3);

        // NOTE: first hit cannot be expanded at all (minimum expansion would set start to -1),
        // second hit can only be expanded once.

        int[] expDoc = { 2 };
        int[] expStart = { 0 };
        int[] expEnd = { 3 };
        Spans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans);
    }
}
