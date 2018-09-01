package nl.inl.blacklab.search;

import java.io.Closeable;
import java.io.File;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;

/**
 * Main BlackLab instance, from which indexes can be opened.
 * 
 * If you don't instantiate this, but call BlackLab.openIndex() directly,
 * an implicit instance will be created that will be closed when you close
 * the last index.
 * 
 * Instantiating this explicitly has the advantage of being able to pass
 * parameters, such as the number of search thread you want (default 4).
 */
public final class BlackLab implements Closeable {
    
    private static final int DEFAULT_NUM_SEARCH_THREADS = 4;

    /**
     * If client doesn't explicitly create a BlackLab instance, one will be instantiated
     * automatically.
     */
    private static BlackLab implicitInstance = null;
    
    /**
     * Map from IndexReader to BlackLab, for use from inside SpanQuery/Spans classes
     */
    private static final Map<IndexReader, BlackLab> blackLabFromIndexReader = new IdentityHashMap<>();

    public static BlackLab create(int searchThreads) {
        return new BlackLab(searchThreads);
    }
    
    public static BlackLab create() {
        return new BlackLab(DEFAULT_NUM_SEARCH_THREADS);
    }
    
//    private static BlackLabIndex openIndex(File dir, SearchSettings searchSettings) throws ErrorOpeningIndex {
//        return BlackLabIndex.open(implicitInstance(), dir, searchSettings);
//    }
    
    public static BlackLabIndex openIndex(File dir) throws ErrorOpeningIndex {
        return BlackLabIndex.open(implicitInstance(), dir);
    }
    
    /**
     * Return the implicitly created instance of BlackLab.
     * 
     * Only used by BlackLabIndex if no BlackLab instance is provided during opening.
     * The instance will have 4 search threads.
     * 
     * @return implicitly instantiated BlackLab instance
     */
    public static synchronized BlackLab implicitInstance() {
        if (implicitInstance == null) {
            implicitInstance = new BlackLab(DEFAULT_NUM_SEARCH_THREADS);
        }
        return implicitInstance;
    }

    public static synchronized BlackLabIndex fromIndexReader(IndexReader reader) {
        return blackLabFromIndexReader.get(reader).indexFromReader(reader);
    }
    
    /**
     * Map from IndexReader to BlackLabIndex, for use from inside SpanQuery/Spans classes
     */
    private Map<IndexReader, BlackLabIndex> searcherFromIndexReader = new IdentityHashMap<>();

    /** Thread on which we run initializations (opening forward indexes, etc.).
     *  Single-threaded because these kinds of initializations are memory and CPU heavy. */
    private ExecutorService initializationExecutorService = null;
    
    /** Thread on which we run searches. Unless we change the default, there will be
     *  four threads available. */
    private ExecutorService searchExecutorService = null;
    
    private BlackLab(int searchThreads) {
        initializationExecutorService = Executors.newSingleThreadExecutor();
        searchExecutorService = Executors.newWorkStealingPool(searchThreads);
    }
    
    @Override
    public void close() {
        if (searchExecutorService != null) {
            searchExecutorService.shutdownNow();
            searchExecutorService = null;
        }
        if (initializationExecutorService != null) {
            initializationExecutorService.shutdownNow();
            initializationExecutorService = null;
        }
        for (BlackLabIndex index: searcherFromIndexReader.values()) {
            index.close();
        }
        searcherFromIndexReader = null;
    }

    public BlackLabIndex open(File indexDir) throws ErrorOpeningIndex {
        return BlackLabIndex.open(this, indexDir);
    }

    public synchronized void registerSearcher(IndexReader reader, BlackLabIndex index) {
        searcherFromIndexReader.put(reader, index);
        blackLabFromIndexReader.put(reader, this);
    }

    public synchronized void removeSearcher(BlackLabIndex index) {
        blackLabFromIndexReader.remove(index.reader());
        searcherFromIndexReader.remove(index.reader());
        if (this == implicitInstance && searcherFromIndexReader.isEmpty()) {
            // We are the implicit instance and our last searcher has been closed. Clean up.
            try {
                close();
            } finally {
                implicitInstance = null;
            }
        }
    }

    public ExecutorService initializationExecutorService() {
        return initializationExecutorService;
    }

    public ExecutorService searchExecutorService() {
        return searchExecutorService;
    }

    private BlackLabIndex indexFromReader(IndexReader reader) {
        return searcherFromIndexReader.get(reader);
    }

}
