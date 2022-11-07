package nl.inl.blacklab.search;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.index.BLIndexObjectFactory;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLabIndex.IndexType;
import nl.inl.util.VersionFile;

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
public final class BlackLabEngine implements AutoCloseable {

    /**
     * All BlackLabEngines that have been instantiated, so we can close them on shutdown.
     */
    private static final Set<BlackLabEngine> engines = new HashSet<>();

    /**
     * Map from IndexReader to BlackLab, for use from inside SpanQuery/Spans classes
     */
    private static final Map<IndexReader, BlackLabEngine> indexReader2BlackLabEngine = new IdentityHashMap<>();

    /** Close all opened engines */
    static synchronized void closeAll() {
        List<BlackLabEngine> copy = new ArrayList<>(engines);
        engines.clear();
        for (BlackLabEngine engine : copy) {
            engine.close();
        }
    }

    static {
        // On program exit, make sure all the engines (and their threads) have been closed, or we might hang.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> closeAll()));
    }

    /**
     * Map from IndexReader to BlackLabIndex, for use from inside SpanQuery/Spans classes
     */
    private final Map<IndexReader, BlackLabIndex> indexReader2BlackLabIndex = new IdentityHashMap<>();

    /** Thread on which we run initializations (opening forward indexes, etc.).
     *  Single-threaded because these kinds of initializations are memory and CPU heavy. */
    private final ExecutorService initializationExecutorService;

    /** Threads on which we run searches. This pool is not limited in size,
     *  but new top-level searches (i.e. not started by other searches) are queued
     *  until server load is deemed low enough that they can start.
     */
    private final ExecutorService searchExecutorService;

    /** How many threads may a single search use? */
    private final int maxThreadsPerSearch;

    /** Give each searchthread a unique number */
    private final AtomicInteger threadCounter = new AtomicInteger(1);

    /** Was close() called on this engine? */
    private boolean wasClosed;

    /** Are we running inside Solr? Used to choose BLIndexObjectFactory. */
    private boolean runningFromSolr;

    /**
     * Create a new engine instance.
     *
     * @param searchThreads (ignored)
     * @param maxThreadsPerSearch max. threads per search.
     * @deprecated use {@link #BlackLabEngine(int)}
     */
    @SuppressWarnings("unused")
    @Deprecated
    BlackLabEngine(int searchThreads, int maxThreadsPerSearch) {
        this(maxThreadsPerSearch);
    }

    BlackLabEngine(int maxThreadsPerSearch) {
        synchronized (engines) {
            engines.add(this);
        }
        initializationExecutorService = Executors.newSingleThreadExecutor();
        this.searchExecutorService = Executors.newCachedThreadPool(runnable -> {
            Thread worker = Executors.defaultThreadFactory().newThread(runnable);
            int threadNumber = threadCounter.getAndUpdate(i -> (i + 1) % 10000);
            worker.setName("SearchThread-" + threadNumber);
            return worker;
        });

        this.maxThreadsPerSearch = maxThreadsPerSearch;

        runningFromSolr = false; // TODO: detect this. how?
    }

    /**
     * Gracefully shut down an ExecutorService.
     *
     * Taken from <a href="https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html">Java docs</a>.
     *
     * @param pool thread pool to shut down
     */
    private static void closeExecutorPool(ExecutorService pool) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    public static BlackLabIndex indexFromReader(IndexReader reader) {
        BlackLabEngine blackLabEngine;
        synchronized (indexReader2BlackLabEngine) {
            blackLabEngine = indexReader2BlackLabEngine.get(reader);
        }
        return blackLabEngine == null ? null : blackLabEngine.getIndexFromReader(reader);
    }

    /**
     * Create or open an index.
     *
     * @param directory index directory
     * @param create force creating a new index even if one already exists?
     * @param formatIdentifier default document format to use
     * @return the index writer
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    public BlackLabIndexWriter openForWriting(File directory, boolean create, String formatIdentifier)
            throws ErrorOpeningIndex {
        return openForWriting(directory, create, formatIdentifier, null, null);
    }

    /**
     * Create or open an index.
     *
     * @param directory index directory
     * @param create force creating a new index even if one already exists?
     * @param formatIdentifier default document format to use
     * @param indexTemplateFile optional file to use as template for index (legacy)
     * @return the index writer
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    public BlackLabIndexWriter openForWriting(File directory, boolean create, String formatIdentifier,
            File indexTemplateFile) throws ErrorOpeningIndex {
        return openForWriting(directory, create, formatIdentifier, indexTemplateFile, null);
    }

    /**
     * Create or open an index.
     *
     * @param directory index directory
     * @param create force creating a new index even if one already exists?
     * @param formatIdentifier default document format to use
     * @param indexTemplateFile optional file to use as template for index (legacy)
     * @param indexType index format to use for creating index: classic with external files or integrated
     * @return the index writer
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    public BlackLabIndexWriter openForWriting(File directory, boolean create, String formatIdentifier,
            File indexTemplateFile, IndexType indexType) throws ErrorOpeningIndex {
        BlackLabIndexWriter indexWriter;
        if (create) {
            if (indexTemplateFile == null) {
                // Create index from format configuration (modern)
                // (or a legacy DocIndexer, but no index template file, so the defaults will be used)
                // No indexTemplateFile, but maybe the formatIdentifier is backed by a ConfigInputFormat (instead of
                // some other DocIndexer implementation)
                // this ConfigInputFormat could then still be used as a minimal template to setup the index
                // (if there's no ConfigInputFormat, that's okay too, a default index template will be used instead)
                ConfigInputFormat format = DocumentFormats.getConfigInputFormat(formatIdentifier);

                // template might still be null, in that case a default will be created
                indexWriter = openForWriting(directory, true, format, indexType);
            } else {
                // Create index from index template file (legacy)
                indexWriter = openForWriting(directory, true, indexTemplateFile, indexType);
            }
            BlackLabIndexWriter.setMetadataDocumentFormatIfMissing(indexWriter, formatIdentifier);
        } else {
            // opening an existing index
            indexWriter = openForWriting(directory, false);
        }
        return indexWriter;
    }

    @Override
    public synchronized void close() {
        if (wasClosed)
            return;
        wasClosed = true;
        closeExecutorPool(searchExecutorService);
        closeExecutorPool(initializationExecutorService);
        synchronized (indexReader2BlackLabIndex) {
            List<BlackLabIndex> copy = new ArrayList<>(indexReader2BlackLabIndex.values()); // avoid concurrent mod.
            for (BlackLabIndex index: copy) {
                index.close();
            }
        }
        synchronized (engines) {
            engines.remove(this);
        }
    }

    /**
     * Return the current default index type, external or integrated
     * @return the default index type
     */
    public IndexType getDefaultIndexType() {
        return BlackLab.isFeatureEnabled(BlackLab.FEATURE_INTEGRATE_EXTERNAL_FILES) ?
                IndexType.INTEGRATED :
                IndexType.EXTERNAL_FILES;
    }

    public BlackLabIndex open(File indexDir) throws ErrorOpeningIndex {
        // Detect index type and instantiate appropriate class
        boolean isIntegratedIndex = !VersionFile.exists(indexDir);
        IndexType indexType = determineIndexType(indexDir, false, null);
        return indexType == IndexType.INTEGRATED ?
            new BlackLabIndexIntegrated(this, indexDir, false, false, (File) null):
            new BlackLabIndexExternal(this, indexDir, false, false, (File) null);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param forceCreateNew if true, create a new index even if one existed there
     * @return index writer
     * @throws ErrorOpeningIndex if the index could not be opened
     */
    public BlackLabIndexWriter openForWriting(File indexDir, boolean forceCreateNew) throws ErrorOpeningIndex {
        return openForWriting(indexDir, forceCreateNew, (File) null, null);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param forceCreateNew if true, create a new index even if one existed there
     * @param indexTemplateFile JSON template to use for index structure / metadata
     * @return index writer
     * @throws ErrorOpeningIndex if index couldn't be opened
     */
    public BlackLabIndexWriter openForWriting(File indexDir, boolean forceCreateNew, File indexTemplateFile)
            throws ErrorOpeningIndex {
        return openForWriting(indexDir, forceCreateNew, indexTemplateFile, null);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param forceCreateNew if true, create a new index even if one existed there
     * @param indexTemplateFile (optional) JSON template to use for index structure / metadata
     * @param indexType (optional) type of index to create (external or integrated), or null to use the default type
     * @return index writer
     * @throws ErrorOpeningIndex if index couldn't be opened
     */
    public BlackLabIndexWriter openForWriting(File indexDir, boolean forceCreateNew, File indexTemplateFile, IndexType indexType)
            throws ErrorOpeningIndex {
        // If no preference for index type given, use the current default
        indexType = determineIndexType(indexDir, forceCreateNew, indexType);
        return indexType == IndexType.INTEGRATED ?
                new BlackLabIndexIntegrated(this, indexDir, true, forceCreateNew, indexTemplateFile) :
                new BlackLabIndexExternal(this, indexDir, true, forceCreateNew, indexTemplateFile);
    }

    private IndexType determineIndexType(File indexDir, boolean forceCreateNew, IndexType indexType) {
        if (indexType == null) {
            // Index type not specified.
            if (forceCreateNew || !BlackLabIndex.isIndex(indexDir)) {
                // New index. Use the default type
                indexType = getDefaultIndexType();
            } else {
                // Existing index. Detect type.
                indexType = VersionFile.exists(indexDir) ? IndexType.EXTERNAL_FILES : IndexType.INTEGRATED;
            }
        }
        return indexType;
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
        return openForWriting(indexDir, createNewIndex, config, null);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param createNewIndex if true, create a new index even if one existed there
     * @param config input format config to use as template for index structure /
     *            metadata (if creating new index)
     * @param indexType type of index to create (external or integrated), or null to use the default type
     * @return index writer
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    public BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex, ConfigInputFormat config, IndexType indexType)
            throws ErrorOpeningIndex {
        indexType = determineIndexType(indexDir, createNewIndex, indexType);
        return indexType == IndexType.INTEGRATED ?
                new BlackLabIndexIntegrated(this, indexDir, true, createNewIndex, config) :
                new BlackLabIndexExternal(this, indexDir, true, createNewIndex, config);
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

    public synchronized void registerIndex(IndexReader reader, BlackLabIndex index) {
        indexReader2BlackLabIndex.put(reader, index);
        synchronized (indexReader2BlackLabEngine) {
            indexReader2BlackLabEngine.put(reader, this);
        }
    }

    public synchronized void removeIndex(BlackLabIndex index) {
        synchronized (indexReader2BlackLabEngine) {
            indexReader2BlackLabEngine.remove(index.reader());
        }
        indexReader2BlackLabIndex.remove(index.reader());
        if (BlackLab.isImplicitInstance(this) && indexReader2BlackLabIndex.isEmpty()) {
            // We are the implicit instance and our last searcher has been closed. Clean up.
            try {
                close();
            } finally {
                BlackLab.discardImplicitInstance();
            }
        }
    }

    public ExecutorService initializationExecutorService() {
        return initializationExecutorService;
    }

    public ExecutorService searchExecutorService() {
        return searchExecutorService;
    }

    synchronized BlackLabIndex getIndexFromReader(IndexReader reader) {
        return indexReader2BlackLabIndex.get(reader);
    }

    public int maxThreadsPerSearch() {
        return maxThreadsPerSearch;
    }

    public BLIndexObjectFactory indexObjectFactory() {
        return BLIndexObjectFactory.get(runningFromSolr);
    }
}
