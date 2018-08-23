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

import org.apache.lucene.search.DocIdSetIterator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;
import nl.inl.blacklab.mocks.MockSpansInBuckets;

public class TestSpansInBuckets {
    private SpansInBuckets hpd;

    @Before
    public void setUp() {
        int[] doc = { 1, 4, 4, 5 };
        int[] start = { 1, 2, 3, 4 };
        int[] end = { 5, 6, 7, 8 };
        BLSpans spans = new MockSpans(doc, start, end);
        hpd = new SpansInBucketsPerDocument(spans);
    }

    @Test
    public void testNextNormal() throws IOException {
        SpansInBuckets exp = MockSpansInBuckets.fromLists(
                new int[] { 1, 4, 5 },
                new int[] { 0, 1, 3 },
                new int[] { 1, 2, 3, 4 },
                new int[] { 5, 6, 7, 8 });
        TestUtil.assertEquals(exp, hpd);
    }

    @Test
    public void testSkipToFirst() throws IOException {
        Assert.assertEquals(1, hpd.advance(1));
        //List<Hit> l = hpd.getHits();
        Assert.assertEquals(1, hpd.docID());
        Assert.assertEquals(1, hpd.nextBucket());
        Assert.assertEquals(1, hpd.bucketSize());
        Assert.assertEquals(1, hpd.startPosition(0));
        Assert.assertEquals(5, hpd.endPosition(0));
        Assert.assertEquals(SpansInBuckets.NO_MORE_BUCKETS, hpd.nextBucket());
    }

    @Test
    public void testSkipToNormal() throws IOException {
        Assert.assertEquals(4, hpd.advance(4));
        Assert.assertEquals(4, hpd.docID());
        Assert.assertEquals(4, hpd.nextBucket());
        Assert.assertEquals(2, hpd.bucketSize());
    }

    @Test
    public void testSkipToNonExistent() throws IOException {
        Assert.assertEquals(4, hpd.advance(2));
        Assert.assertEquals(4, hpd.docID());
    }

    @Test
    public void testSkipToPastEnd() throws IOException {
        Assert.assertEquals(DocIdSetIterator.NO_MORE_DOCS, hpd.advance(6));
    }
}
