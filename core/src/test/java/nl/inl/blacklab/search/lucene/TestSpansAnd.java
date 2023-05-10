package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.Spans;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;

@RunWith(Parameterized.class)
public class TestSpansAnd {

    @Parameterized.Parameters(name = "and type {0}")
    public static Collection<Boolean> typeToUse() {
        return List.of(false, true);
    }

    @Parameterized.Parameter
    public boolean expectDuplicates;

    private BLSpans getSpans() {
        BLSpans a = MockSpans.fromLists(
                new int[] { 1, 1, 2, 2, 2, 3 },
                new int[] { 10, 20, 10, 10, 30, 10 },
                new int[] { 15, 25, 15, 20, 35, 15 });
        BLSpans b = MockSpans.fromLists(
                new int[] { 1, 2, 2, 3 },
                new int[] { 10, 10, 20, 20 },
                new int[] { 15, 20, 25, 25 });
        return expectDuplicates ? new SpansAndDuplicates(a, b) : new SpansAnd(a, b);
    }

    private BLSpans getSpansWithDupes() {
        BLSpans a = MockSpans.fromLists(
                new int[] {  1,  1,  1,  2,  2,  2,  2,  3 },
                new int[] { 10, 10, 20, 10, 10, 10, 30, 10 },
                new int[] { 15, 15, 25, 15, 20, 20, 35, 15 });
        BLSpans b = MockSpans.fromLists(
                new int[] {  1,  2,  2,  2,  3,  3 },
                new int[] { 10, 10, 10, 20, 20, 20 },
                new int[] { 15, 20, 20, 25, 25, 25 });
        return new SpansAndDuplicates(a, b);
    }

    @Test
    public void testAndSpans() throws IOException {
        Spans exp = MockSpans.fromLists(
                new int[] { 1, 2 },
                new int[] { 10, 10 },
                new int[] { 15, 20 });
        TestUtil.assertEquals(exp, getSpans());
    }

    @Test
    public void testAndSpansDuplicates() throws IOException {
        Spans exp = MockSpans.fromLists(
                new int[] {  1,  1,  2,  2,  2,  2 },
                new int[] { 10, 10, 10, 10, 10, 10 },
                new int[] { 15, 15, 20, 20, 20, 20 });
        TestUtil.assertEquals(exp, getSpansWithDupes());
    }

    @Test
    public void testAndSpansAdvance() throws IOException {
        Spans exp = MockSpans.single(2, 10, 20);
        BLSpans spans = getSpans();
        Assert.assertEquals(2, spans.advance(2));
        TestUtil.assertEquals(exp, spans, true);
    }

    @Test
    public void testAndSpansAdvanceNoResults() throws IOException {
        MockSpans exp = MockSpans.emptySpans();
        BLSpans spans = getSpans();
        Assert.assertEquals(DocIdSetIterator.NO_MORE_DOCS, spans.advance(3));
        TestUtil.assertEquals(exp, spans, true);
    }

    @Test
    public void testAndSpansAdvanceBeyond() throws IOException {
        MockSpans exp = MockSpans.emptySpans();
        BLSpans spans = getSpans();
        Assert.assertEquals(DocIdSetIterator.NO_MORE_DOCS, spans.advance(1000));
        TestUtil.assertEquals(exp, spans, true);
    }
}
