package nl.inl.blacklab.search;

import java.io.Closeable;
import java.io.File;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;

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
public final class BlackLabEngine implements Closeable {
    
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
    
    /** How many threads may a single search use? */
    private int maxThreadsPerSearch;
    
    BlackLabEngine(int searchThreads, int maxThreadsPerSearch) {
        initializationExecutorService = Executors.newSingleThreadExecutor();
        searchExecutorService = Executors.newWorkStealingPool(searchThreads);
        this.maxThreadsPerSearch = maxThreadsPerSearch;
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
    
    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param createNewIndex if true, create a new index even if one existed there
     * @return index writer
     * @throws ErrorOpeningIndex if the index could not be opened
     */
    public BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex) throws ErrorOpeningIndex {
        return openForWriting(indexDir, createNewIndex, (File) null);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param createNewIndex if true, create a new index even if one existed there
     * @param indexTemplateFile JSON template to use for index structure / metadata
     * @return index writer
     * @throws ErrorOpeningIndex if index couldn't be opened
     */
    public BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex, File indexTemplateFile)
            throws ErrorOpeningIndex {
        return new BlackLabIndexImpl(this, indexDir, true, createNewIndex, indexTemplateFile);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param createNewIndex if true, create a new index even if one existed there
     * @param config input format config to use as template for index structure /
     *            metadata (if creating new index)
     * @return index writer
     * @throws ErrorOpeningIndex if the index couldn't be opened 
     */
    public BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex, ConfigInputFormat config)
            throws ErrorOpeningIndex {
        return new BlackLabIndexImpl(this, indexDir, true, createNewIndex, config);
    }

    /**
     * Create an empty index.
     *
     * @param indexDir where to create the index
     * @param config format configuration for this index; used to base the index
     *            metadata on
     * @return a BlackLabIndexWriter for the new index, in index mode
     * @throws ErrorOpeningIndex if the index couldn't be opened 
     */
    public BlackLabIndexWriter create(File indexDir, ConfigInputFormat config) throws ErrorOpeningIndex {
        return openForWriting(indexDir, true, config);
    }

    public synchronized void registerSearcher(IndexReader reader, BlackLabIndex index) {
        searcherFromIndexReader.put(reader, index);
        BlackLab.blackLabFromIndexReader.put(reader, this);
    }

    public synchronized void removeSearcher(BlackLabIndex index) {
        BlackLab.blackLabFromIndexReader.remove(index.reader());
        searcherFromIndexReader.remove(index.reader());
        if (this == BlackLab.implicitInstance && searcherFromIndexReader.isEmpty()) {
            // We are the implicit instance and our last searcher has been closed. Clean up.
            try {
                close();
            } finally {
                BlackLab.implicitInstance = null;
            }
        }
    }

    public ExecutorService initializationExecutorService() {
        return initializationExecutorService;
    }

    public ExecutorService searchExecutorService() {
        return searchExecutorService;
    }

    BlackLabIndex indexFromReader(IndexReader reader) {
        return searcherFromIndexReader.get(reader);
    }

    public int maxThreadsPerSearch() {
        return maxThreadsPerSearch;
    }

}
