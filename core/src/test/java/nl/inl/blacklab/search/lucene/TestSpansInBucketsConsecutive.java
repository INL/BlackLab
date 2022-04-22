package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;
import nl.inl.blacklab.mocks.MockSpansInBuckets;

public class TestSpansInBucketsConsecutive {
    private SpansInBuckets hpd;

    @Before
    public void setUp() {
        int[] doc = { 1, 1, 2, 2, 2, 2 };
        int[] start = { 1, 2, 3, 4, 6, 7 };
        int[] end = { 2, 3, 4, 5, 7, 8 };
        BLSpans spans = new MockSpans(doc, start, end);
        hpd = new SpansInBucketsConsecutive(spans);
    }

    @Test
    public void testListInterface() throws IOException {

        int[] bDoc = { 1, 2, 2 };
        int[] bStart = { 0, 2, 4 };

        int[] hStart = { 1, 2, 3, 4, 6, 7 };
        int[] hEnd = { 2, 3, 4, 5, 7, 8 };
        SpansInBuckets exp = new MockSpansInBuckets(bDoc, bStart, hStart, hEnd);
        TestUtil.assertEquals(exp, hpd);
    }

}
