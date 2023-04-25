package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.Spans;
import org.junit.Test;

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;

public class TestSpansRepetition {
    @Test
    public void testRepetition() throws IOException {
        int[] aDoc = { 1, 1, 1, 1, 1, 1 };
        int[] aStart = { 1, 2, 3, 5, 6, 10 };
        int[] aEnd = { 2, 3, 4, 6, 7, 11 };
        BLSpans a = new MockSpans(aDoc, aStart, aEnd);

        Spans spans = new SpansRepetition(a, 2, 4);

        int[] expDoc = { 1, 1, 1, 1 };
        int[] expStart = { 1, 1, 2, 5 };
        int[] expEnd = { 3, 4, 4, 7 };
        BLSpans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans);
    }
}
