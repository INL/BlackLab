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

import java.io.IOException;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.Spans;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockBlackLabIndex;
import nl.inl.blacklab.mocks.MockSpans;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;

public class TestHits {

    @Test
    public void testHitsIterate() {
        int[] aDoc = { 1, 2 };
        int[] aStart = { 1, 2 };
        int[] aEnd = { 2, 3 };
        try (MockBlackLabIndex index = new MockBlackLabIndex()) {
            Hits hits = Hits.fromArrays(index.createDefaultQueryInfo(), aDoc, aStart, aEnd);
    
            int i = 0;
            for (Hit hit : hits) {
                Assert.assertEquals(aDoc[i], hit.doc());
                Assert.assertEquals(aStart[i], hit.start());
                Assert.assertEquals(aEnd[i], hit.end());
                i++;
            }
        }
    }

    @Test
    public void testMockSpans() throws IOException {
        int[] aDoc = { 1, 2 };
        int[] aStart = { 1, 2 };
        int[] aEnd = { 2, 3 };
        Spans spans = new MockSpans(aDoc, aStart, aEnd);
        Assert.assertNotEquals(DocIdSetIterator.NO_MORE_DOCS, spans.nextDoc());
        Assert.assertEquals(1, spans.docID());
        Assert.assertEquals(1, spans.nextStartPosition());
        Assert.assertEquals(2, spans.endPosition());
        Assert.assertEquals(Spans.NO_MORE_POSITIONS, spans.nextStartPosition());
        Assert.assertNotEquals(DocIdSetIterator.NO_MORE_DOCS, spans.nextDoc());
        Assert.assertEquals(2, spans.docID());
        Assert.assertEquals(2, spans.nextStartPosition());
        Assert.assertEquals(3, spans.endPosition());
        Assert.assertEquals(Spans.NO_MORE_POSITIONS, spans.nextStartPosition());
    }

}
