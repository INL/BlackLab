package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;

public class TestPerDocumentSortedSpans {
    private static PerDocumentSortedSpans hpd;

    @BeforeClass
    public static void setUp() {
        int[] doc = { 1, 1, 1, 2, 2 };
        int[] start = { 1, 1, 4, 2, 2 };
        int[] end = { 8, 6, 5, 4, 3 };
        BLSpans spans = new MockSpans(doc, start, end);
        hpd = PerDocumentSortedSpans.endPoint(spans);
    }

    @Test
    public void testNormal() throws IOException {
        int[] doc = { 1, 1, 1, 2, 2 };
        int[] start = { 4, 1, 1, 2, 2 };
        int[] end = { 5, 6, 8, 3, 4 };
        BLSpans exp = new MockSpans(doc, start, end);
        TestUtil.assertEquals(exp, hpd);
    }

}
