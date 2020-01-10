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

public class TestSpansSequence {

    @Test
    public void testSequenceSpansBasic() throws IOException {
        int[] aDoc = { 1, 1, 1 };
        int[] aStart = { 1, 1, 2 };
        int[] aEnd = { 2, 3, 4 };
        BLSpans a = new MockSpans(aDoc, aStart, aEnd);

        int[] bDoc = { 1, 1, 1 };
        int[] bStart = { 2, 2, 3 };
        int[] bEnd = { 4, 5, 4 };
        BLSpans b = new MockSpans(bDoc, bStart, bEnd);

        Spans spans = new SpansSequenceRaw(a, b);

        int[] expDoc = { 1, 1, 1 };
        int[] expStart = { 1, 1, 1 };
        int[] expEnd = { 4, 5, 4 };
        Spans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans);
    }

    @Test
    public void testSequenceSpansSimple() throws IOException {
        int[] aDoc = { 1, 1, 1 };
        int[] aStart = { 1, 2, 4 };
        int[] aEnd = { 2, 3, 5 };
        BLSpans a = new MockSpans(aDoc, aStart, aEnd);

        int[] bDoc = { 1, 1, 1 };
        int[] bStart = { 2, 4, 5 };
        int[] bEnd = { 3, 5, 6 };
        BLSpans b = new MockSpans(bDoc, bStart, bEnd);

        Spans spans = new SpansSequenceSimple(a, b);

        int[] expDoc = { 1, 1 };
        int[] expStart = { 1, 4 };
        int[] expEnd = { 3, 6 };
        Spans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans);
    }

    /**
     * This test exists to guard against reoccurence of a particular bug: When the
     * right spans was further along inside a document than the left spans, the left
     * spans would be advanced to catch up with the right one. However, during this
     * advancing, the document id was not checked, so left could actually end up in
     * another document than right, potentially skipping hits or even generating
     * false hits.
     *
     * @throws IOException
     */
    @Test
    public void testSequenceSpansAdvanceBeyondDocError() throws IOException {
        int[] aDoc = { 1, 2, 2 };
        int[] aStart = { 1, 2, 4 };
        int[] aEnd = { 2, 3, 5 };
        BLSpans a = new MockSpans(aDoc, aStart, aEnd);

        int[] bDoc = { 1, 2 };
        int[] bStart = { 5, 3 };
        int[] bEnd = { 6, 4 };
        BLSpans b = new MockSpans(bDoc, bStart, bEnd);

        Spans spans = new SpansSequenceRaw(a, b);

        int[] expDoc = { 2 };
        int[] expStart = { 2 };
        int[] expEnd = { 4 };
        Spans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans);
    }
}
