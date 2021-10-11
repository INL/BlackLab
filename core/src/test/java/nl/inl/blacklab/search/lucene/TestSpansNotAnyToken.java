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

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.Spans;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * Test SpansNot without a clause ("all tokens")
 */
public class TestSpansNotAnyToken {

    private static SpansNot getSpans() {
        SpansNot spans = new SpansNot(null, "test", null);
        spans.setTest(true, 3); // no IndexReader available
        return spans;
    }

    @Test
    public void testSpansNot() throws IOException {
        Spans exp = MockSpans.fromLists(
                new int[] { 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2 },
                new int[] { 0, 1, 2, 3, 4, 0, 1, 2, 3, 4, 0, 1, 2, 3, 4 },
                new int[] { 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5 });
        TestUtil.assertEquals(exp, getSpans());
    }

    @Test
    public void testSpansNotAdvance() throws IOException {
        Spans exp = MockSpans.fromLists(
                new int[] { 2, 2, 2, 2, 2 },
                new int[] { 0, 1, 2, 3, 4 },
                new int[] { 1, 2, 3, 4, 5 });
        SpansNot spans = getSpans();
        spans.advance(2);
        TestUtil.assertEquals(exp, spans, true);
    }

    @Test
    public void testSpansNotAdvanceToCurrent() throws IOException {
        Spans exp = MockSpans.fromLists(
                new int[] { 1, 1, 1, 1, 1, 2, 2, 2, 2, 2 },
                new int[] { 0, 1, 2, 3, 4, 0, 1, 2, 3, 4 },
                new int[] { 1, 2, 3, 4, 5, 1, 2, 3, 4, 5 });
        SpansNot spans = getSpans();
        Assert.assertEquals(0, spans.nextDoc());
        Assert.assertEquals(1, spans.advance(0));
        TestUtil.assertEquals(exp, spans, true);
    }

    @Test
    public void testSpansNotAdvanceNoResults() throws IOException {
        Spans exp = MockSpans.emptySpans();
        SpansNot spans = getSpans();
        Assert.assertEquals(DocIdSetIterator.NO_MORE_DOCS, spans.advance(3));
        TestUtil.assertEquals(exp, spans, true);
    }

    @Test
    public void testSpansNotPositionBeforeDoc() throws IOException {
        SpansNot spans = getSpans();
        Assert.assertEquals(Spans.NO_MORE_POSITIONS, spans.nextStartPosition());
        spans = getSpans();
        Assert.assertEquals(Spans.NO_MORE_POSITIONS, spans.advanceStartPosition(1));
    }

}
