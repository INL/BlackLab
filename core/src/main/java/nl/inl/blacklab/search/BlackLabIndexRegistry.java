package nl.inl.blacklab.search;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.lucene.index.IndexReader;

public class BlackLabIndexRegistry {
    
    private static final Map<IndexReader, BlackLabIndex> searcherFromIndexReader = new IdentityHashMap<>();

    /** Thread on which we run initializations (opening forward indexes, etc.).
     *  Single-threaded because these kinds of initializations are memory and CPU heavy. */
    private static ExecutorService initializationExecutorService = null;
    
    public static synchronized BlackLabIndex fromIndexReader(IndexReader reader) {
        return searcherFromIndexReader.get(reader);
    }

    public static synchronized void registerSearcher(IndexReader reader, BlackLabIndex index) {
        initializationExecutorService();
        searcherFromIndexReader.put(reader, index);
    }

    public static synchronized void removeSearcher(BlackLabIndex index) {
        searcherFromIndexReader.remove(index.reader());
        if (searcherFromIndexReader.isEmpty() && initializationExecutorService != null) {
            initializationExecutorService.shutdown();
            initializationExecutorService = null;
        }
    }

    public static synchronized ExecutorService initializationExecutorService() {
        if (initializationExecutorService == null) {
            initializationExecutorService = Executors.newSingleThreadExecutor();
        }
        return initializationExecutorService;
    }

}
