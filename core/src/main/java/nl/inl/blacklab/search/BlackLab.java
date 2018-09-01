package nl.inl.blacklab.search;

import java.io.File;
import java.util.IdentityHashMap;
import java.util.Map;

import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;

/**
 * Main BlackLab class, from which indexes can be opened.
 * 
 * You can either open indices using the static methods in this class,
 * or you can create() a BlackLabEngine and use that to open indices.
 * 
 * The first approach will implicitly create a default BlackLabEngine
 * in the background, with 4 search threads. If you want a different
 * number of search threads, call create() to create your own instance
 * of BlackLabEngine.
 * 
 * Don't try to mix these two methods; if an implicit engine exists and
 * you call create(), or if you call e.g. BlackLab.open() when you've
 * already created an engine explicitly, an exception will be thrown.
 * 
 * If you explicitly create an engine, make sure to close it when you're
 * done. For the implicit engine, this is done automatically when you
 * close your last index. 
 */
public final class BlackLab {
    
    private static final int DEFAULT_NUM_SEARCH_THREADS = 4;

    /**
     * If client doesn't explicitly create a BlackLab instance, one will be instantiated
     * automatically.
     */
    static BlackLabEngine implicitInstance = null;
    
    /**
     * Have we called create()? If so, don't create an implicit instance, but throw an exception.
     */
    private static boolean explicitlyCreated = false;
    
    /**
     * Map from IndexReader to BlackLab, for use from inside SpanQuery/Spans classes
     */
    static final Map<IndexReader, BlackLabEngine> blackLabFromIndexReader = new IdentityHashMap<>();

    public static BlackLabEngine create(int searchThreads) {
        if (implicitInstance != null)
            throw new UnsupportedOperationException("BlackLab.create() called, but an implicit instance exists already! Don't mix implicit and explicit BlackLabEngine!");
        explicitlyCreated = true;
        return new BlackLabEngine(searchThreads);
    }
    
    public static BlackLabEngine create() {
        return create(DEFAULT_NUM_SEARCH_THREADS);
    }
    
    public static BlackLabIndex open(File dir) throws ErrorOpeningIndex {
        return BlackLabIndex.open(implicitInstance(), dir);
    }
    
    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * Note that in index mode, searching operations may not take the latest changes
     * into account. It is wisest to only use index mode for indexing, then close
     * the Searcher and create a regular one for searching.
     *
     * @param indexDir the index directory
     * @param createNewIndex if true, create a new index even if one existed there
     * @return index writer
     * @throws ErrorOpeningIndex if the index could not be opened
     */
    public static BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex) throws ErrorOpeningIndex {
        return openForWriting(indexDir, createNewIndex, (File) null);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * Note that in index mode, searching operations may not take the latest changes
     * into account. It is wisest to only use index mode for indexing, then close
     * the Searcher and create a regular one for searching.
     *
     * @param indexDir the index directory
     * @param createNewIndex if true, create a new index even if one existed there
     * @param indexTemplateFile JSON template to use for index structure / metadata
     * @return index writer
     * @throws ErrorOpeningIndex if index couldn't be opened
     */
    public static BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex, File indexTemplateFile)
            throws ErrorOpeningIndex {
        return new BlackLabIndexImpl(implicitInstance(), indexDir, true, createNewIndex, indexTemplateFile);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * Note that in index mode, searching operations may not take the latest changes
     * into account. It is wisest to only use index mode for indexing, then close
     * the Searcher and create a regular one for searching.
     *
     * @param indexDir the index directory
     * @param createNewIndex if true, create a new index even if one existed there
     * @param config input format config to use as template for index structure /
     *            metadata (if creating new index)
     * @return index writer
     * @throws ErrorOpeningIndex if the index couldn't be opened 
     */
    public static BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex, ConfigInputFormat config)
            throws ErrorOpeningIndex {
        return new BlackLabIndexImpl(BlackLab.implicitInstance(), indexDir, true, createNewIndex, config);
    }

    /**
     * Create an empty index.
     *
     * @param indexDir where to create the index
     * @param config format configuration for this index; used to base the index
     *            metadata on
     * @return a Searcher for the new index, in index mode
     * @throws ErrorOpeningIndex if the index couldn't be opened 
     */
    public static BlackLabIndexWriter create(File indexDir, ConfigInputFormat config) throws ErrorOpeningIndex {
        return openForWriting(indexDir, true, config);
    }
    
    
    /**
     * Return the implicitly created instance of BlackLab.
     * 
     * Only used by BlackLabIndex if no BlackLab instance is provided during opening.
     * The instance will have 4 search threads.
     * 
     * @return implicitly instantiated BlackLab instance
     */
    public static synchronized BlackLabEngine implicitInstance() {
        if (explicitlyCreated)
            throw new UnsupportedOperationException("Already called create(); cannot create an implicit instance anymore! Don't mix implicit and explicit BlackLabEngine!");
        if (implicitInstance == null) {
            implicitInstance = new BlackLabEngine(DEFAULT_NUM_SEARCH_THREADS);
        }
        return implicitInstance;
    }

    public static synchronized BlackLabIndex fromIndexReader(IndexReader reader) {
        return blackLabFromIndexReader.get(reader).indexFromReader(reader);
    }
    
    private BlackLab() { }
    
}
