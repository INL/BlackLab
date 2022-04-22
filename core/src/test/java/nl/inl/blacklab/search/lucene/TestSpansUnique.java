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
