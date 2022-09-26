package nl.inl.blacklab.search.results;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.lucene.index.Term;
import org.eclipse.collections.impl.block.function.checked.ThrowingFunction;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import nl.inl.blacklab.search.BlackLabIndex.IndexType;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.testutil.TestIndex;

@RunWith(Parameterized.class)
public class TestSearchBehavior {
    public TestIndex testIndex = TestIndex.get(IndexType.INTEGRATED);

    @Parameterized.Parameter
    public boolean shouldThrow;

    @Parameterized.Parameters(name = "interrupt the search {0}")
    public static List<Object> data() {
        return List.of(true, false);
    }
    
    private final String SPANSREADER_CRASHED = "TEST_SPANSREADER_CRASHED";
    private boolean spansReaderWasInterrupted = false;
    
    SpansReader signalOnInterrupt = new SpansReader(null, null, null, null, null, null, null, null, null, null, null) {
        public synchronized void run() {
            try {
                Thread.sleep(100_000);
            } catch (InterruptedException e) {
                spansReaderWasInterrupted = true;
                throw new RuntimeException();
            }
        };
        void initialize() {};
    };

    SpansReader signalImmediately = new SpansReader(null, null, null, null, null, null, null, null, null, null, null) {
        public synchronized void run() {
            throw new RuntimeException(SPANSREADER_CRASHED);
        };
        void initialize() {};
    };

    @Test 
    public void testParallelSearchInterrupt() {
        BLSpanTermQuery patternQuery = new BLSpanTermQuery(null, new Term("contents%word@i", "the"));
        HitsFromQueryParallel h = new HitsFromQueryParallel(QueryInfo.create(testIndex.index()), patternQuery, SearchSettings.defaults());

        // insert a SpansReader that will a) immediately throw an exception, b) wait for Interrupt() and then throw an exception
        h.spansReaders.clear();
        h.spansReaders.add(shouldThrow ? signalImmediately : signalOnInterrupt);
        if (!shouldThrow) Thread.currentThread().interrupt();

        Throwable t = null;
        try {
            h.ensureResultsRead(-1);
        } catch (Exception e) {
            t = e;
            while (t.getCause() != null) t = t.getCause();
        }
        
        if (shouldThrow) {
            // HitsFromQueryParallel correctly re-threw on the exception thrown in its SpansReader.
            assertEquals(t.getMessage(), SPANSREADER_CRASHED);
        } else {
            // the spansreader was correctly interrupted by HitsFromQueryParallel.
            assertTrue("SpansReader received interrupt() after parent HitsFromQueryParallel was interrupted", spansReaderWasInterrupted);
        }
    }
}
