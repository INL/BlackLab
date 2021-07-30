package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.analysis.BLDutchAnalyzer;
import nl.inl.blacklab.analysis.BLNonTokenizingAnalyzer;
import nl.inl.blacklab.analysis.BLStandardAnalyzer;
import nl.inl.blacklab.analysis.BLWhitespaceAnalyzer;
import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.contentstore.ContentStoresManager;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.IndexTooOld;
import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.requestlogging.SearchLogger;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldImpl;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataImpl;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.searches.SearchCacheDummy;
import nl.inl.blacklab.searches.SearchEmpty;
import nl.inl.util.LuceneUtil;
import nl.inl.util.VersionFile;
import nl.inl.util.XmlHighlighter.UnbalancedTagsStrategy;

public class BlackLabIndexImpl implements BlackLabIndexWriter {

    // Class variables
    //---------------------------------------------------------------

    protected static final Logger logger = LogManager.getLogger(BlackLabIndexImpl.class);

    /** Analyzer based on WhitespaceTokenizer */
    protected static final Analyzer WHITESPACE_ANALYZER = new BLWhitespaceAnalyzer();

    /** Analyzer for Dutch and other Latin script languages */
    protected static final Analyzer DEFAULT_ANALYZER = new BLDutchAnalyzer();

    /** Analyzer based on StandardTokenizer */
    protected static final Analyzer STANDARD_ANALYZER = new BLStandardAnalyzer();

    /** Analyzer that doesn't tokenize */
    protected static final Analyzer NONTOKENIZING_ANALYZER = new BLNonTokenizingAnalyzer();

    private static final ContextSize DEFAULT_CONTEXT_SIZE = ContextSize.get(5);

    /** Log detailed debug messages about opening an index? */
    static boolean traceIndexOpening = false;

    /** Log detailed debug messages about query optimization? */
    static boolean traceOptimization = false;

    /**
     * Log debug messages about query execution at various stages, to analyze what
     * makes a query slow?
     */
    static boolean traceQueryExecution = false;

    /** The collator to use for sorting. Defaults to English collator. */
    private static Collator defaultCollator = Collator.getInstance(new Locale("en", "GB"));


    // Static methods
    //---------------------------------------------------------------

    /**
     * Cut a few words from a string.
     *
     * Note, this just splits on whitespace and glues words back with space. Might
     * not work very well in all cases, but it's not likely to be used anyway (we
     * generally don't cut a few words from a metadata field).
     *
     * @param content the string to cut from
     * @param startAtWord first word to include
     * @param endAtWord first word not to include
     * @return the cut string
     */
    static String getWordsFromString(String content, int startAtWord,
            int endAtWord) {
        if (startAtWord == -1 && endAtWord == -1)
            return content;
        // We want specific words from the field; quick-n-dirty way to do this
        // (will probably never be used, but let's try to be generic)
        String[] words = content.split("\\s+");
        if (startAtWord == -1)
            startAtWord = 0;
        if (endAtWord == -1)
            endAtWord = words.length;
        StringBuilder b = new StringBuilder();
        for (int i = startAtWord; i < endAtWord; i++) {
            if (b.length() > 0)
                b.append(" ");
            b.append(words[i]);
        }
        return b.toString();
    }

    public static Collator defaultCollator() {
        return defaultCollator;
    }

    public static void setDefaultCollator(Collator defaultCollator) {
        BlackLabIndexImpl.defaultCollator = defaultCollator;
    }

    /**
     * Return a timestamp for when BlackLab was built.
     *
     * @return build timestamp (format: yyyy-MM-dd HH:mm:ss), or UNKNOWN if the
     *         timestamp could not be found for some reason (i.e. not running from a
     *         JAR, or key not found in manifest).
     */
    public static String blackLabBuildTime() {
        return valueFromManifest("Build-Time", "UNKNOWN");
    }

    /**
     * Return the BlackLab version.
     *
     * @return BlackLab version, or UNKNOWN if the version could not be found for
     *         some reason (i.e. not running from a JAR, or key not found in
     *         manifest).
     */
    public static String blackLabVersion() {
        return valueFromManifest("Implementation-Version", "UNKNOWN");
    }

    /**
     * Get a value from the manifest file, if available.
     *
     * @param key key to get the value for, e.g. "Build-Time".
     * @param defaultValue value to return if no manifest found or key not found
     * @return value from the manifest, or the default value if not found
     */
    static String valueFromManifest(String key, String defaultValue) {
        try {
            URL res = BlackLabIndexImpl.class.getResource(BlackLabIndexImpl.class.getSimpleName() + ".class");
            URLConnection conn = res.openConnection();
            if (!(conn instanceof JarURLConnection)) {
                // Not running from a JAR, no manifest to read
                return defaultValue;
            }
            JarURLConnection jarConn = (JarURLConnection) res.openConnection();
            Manifest mf = jarConn.getManifest();
            String value = null;
            if (mf != null) {
                Attributes atts = mf.getMainAttributes();
                if (atts != null) {
                    value = atts.getValue(key);
                }
            }
            return value == null ? defaultValue : value;
        } catch (IOException e) {
            throw new BlackLabRuntimeException("Could not read '" + key + "' from manifest", e);
        }
    }

    /**
     * Instantiate analyzer based on an analyzer alias.
     *
     * @param analyzerName type of analyzer
     *            (default|whitespace|standard|nontokenizing)
     * @return the analyzer, or null if the name wasn't recognized
     */
    public static Analyzer analyzerInstance(String analyzerName) {
        analyzerName = analyzerName.toLowerCase();
        if (analyzerName.equals("whitespace")) {
            return WHITESPACE_ANALYZER;
        } else if (analyzerName.equals("default")) {
            return DEFAULT_ANALYZER;
        } else if (analyzerName.equals("standard")) {
            return STANDARD_ANALYZER;
        } else if (analyzerName.matches("(non|un)tokeniz(ing|ed)")) {
            return NONTOKENIZING_ANALYZER;
        }
        return null;
    }

    public static void setTraceIndexOpening(boolean traceIndexOpening) {
        logger.debug("Trace index opening: " + traceIndexOpening);
        BlackLabIndexImpl.traceIndexOpening = traceIndexOpening;
    }

    public static void setTraceOptimization(boolean traceOptimization) {
        logger.debug("Trace optimization: " + traceOptimization);
        BlackLabIndexImpl.traceOptimization = traceOptimization;
    }

    public static void setTraceQueryExecution(boolean traceQueryExecution) {
        logger.debug("Trace query execution: " + traceQueryExecution);
        BlackLabIndexImpl.traceQueryExecution = traceQueryExecution;
    }

    public static boolean traceOptimization() {
        return traceOptimization;
    }

    public static boolean traceIndexOpening() {
        return traceIndexOpening;
    }

    public static boolean traceQueryExecution() {
        return traceQueryExecution;
    }

    // Instance variables
    //---------------------------------------------------------------


    /** BlackLab instance used to create us */
    private BlackLabEngine blackLab;

    /** The collator to use for sorting. Defaults to English collator. */
    private Collator collator = BlackLabIndexImpl.defaultCollator;

    /** Analyzer used for indexing our metadata fields */
    protected Analyzer analyzer = new BLStandardAnalyzer();

    /** Structure of our index */
    protected IndexMetadataWriter indexMetadata;

    protected ContentStoresManager contentStores = new ContentStoresManager();

    /**
     * ForwardIndices allow us to quickly find what token occurs at a specific
     * position. This speeds up grouping and sorting. There may be several indices
     * on a annotated field, e.g.: word form, lemma, part of speech.
     *
     * Indexed by annotation.
     */
    protected Map<AnnotatedField, ForwardIndex> forwardIndices = new HashMap<>();

    protected SearchSettings searchSettings;

    /** Should we default to case-/diacritics-sensitive searching? [default: both insensitive] */
    protected MatchSensitivity defaultMatchSensitivity = MatchSensitivity.INSENSITIVE;

    /**
     * How we fix well-formedness for snippets of XML: by adding or removing
     * unbalanced tags
     */
    private UnbalancedTagsStrategy defaultUnbalancedTagsStrategy = UnbalancedTagsStrategy.ADD_TAG;

    /** If true, we want to add/delete documents. If false, we're just searching. */
    protected boolean indexMode = false;

    /**
     * The Lucene index reader
     */
    IndexReader reader;

    /**
     * The Lucene IndexSearcher, for dealing with non-Span queries (for per-document
     * scoring)
     */
    private IndexSearcher indexSearcher;

    /**
     * Directory where our index resides
     */
    private File indexLocation;

    /**
     * If true, we've just created a new index. New indices cannot be searched, only
     * added to.
     */
    private boolean isEmptyIndex = false;

    /** The index writer. Only valid in indexMode. */
    private IndexWriter indexWriter = null;

    private ContextSize defaultContextSize = DEFAULT_CONTEXT_SIZE;

    /** Search cache to use */
    private SearchCache cache = new SearchCacheDummy();


    // Constructors
    //---------------------------------------------------------------


    /**
     * Open an index.
     *
     * @param indexDir the index directory
     * @param indexMode if true, open in index mode; if false, open in search mode.
     * @param createNewIndex if true, delete existing index in this location if it
     *            exists.
     * @param config input format config to use as template for index structure /
     *            metadata (if creating new index)
     * @throws IndexTooOld if the index is too old to be opened by this BlackLab version
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    BlackLabIndexImpl(BlackLabEngine blackLab, File indexDir, boolean indexMode, boolean createNewIndex, ConfigInputFormat config) throws ErrorOpeningIndex {
        this.blackLab = blackLab;
        searchSettings = SearchSettings.defaults();
        try {
            this.indexMode = indexMode;

            try {
                BlackLab.applyConfigToIndex(this);
            } catch (InvalidConfiguration e) {
                throw new InvalidConfiguration(e.getMessage() + " (BlackLab configuration file)", e.getCause());
            }

            openIndex(indexDir, indexMode, createNewIndex);

            // Determine the index structure
            if (traceIndexOpening)
                logger.debug("  Determining index structure...");
            IndexMetadataImpl indexMetadataImpl = new IndexMetadataImpl(reader, indexDir, createNewIndex, config);
            indexMetadata = indexMetadataImpl;
            if (!indexMode)
                indexMetadata.freeze();

            finishOpeningIndex(indexDir, indexMode, createNewIndex);
        } catch (IOException e) {
            throw new ErrorOpeningIndex("Could not open index: " + indexDir, e);
        }
    }

    /**
     * Open an index.
     *
     * @param blackLab our BlackLab instance
     * @param indexDir the index directory
     * @param indexMode if true, open in index mode; if false, open in search mode.
     * @param createNewIndex if true, delete existing index in this location if it
     *            exists.
     * @param indexTemplateFile index template file to use to create index
     * @throws ErrorOpeningIndex
     */
    BlackLabIndexImpl(BlackLabEngine blackLab, File indexDir, boolean indexMode, boolean createNewIndex, File indexTemplateFile) throws ErrorOpeningIndex {
        this.blackLab = blackLab;
        searchSettings = SearchSettings.defaults();
        this.indexMode = indexMode;

        try {
            BlackLab.applyConfigToIndex(this);

            openIndex(indexDir, indexMode, createNewIndex);

            // Determine the index structure
            if (traceIndexOpening)
                logger.debug("  Determining index structure...");
            IndexMetadataImpl indexMetadataImpl = new IndexMetadataImpl(reader, indexDir, createNewIndex, indexTemplateFile);
            indexMetadata = indexMetadataImpl;
            if (!indexMode)
                indexMetadata.freeze();

            finishOpeningIndex(indexDir, indexMode, createNewIndex);
        } catch (IOException e) {
            throw new ErrorOpeningIndex(e);
        }
    }

    // Methods for querying the index
    //---------------------------------------------------------------


    @Override
    public SearchSettings searchSettings() {
        return searchSettings;
    }

    @Override
    public void setSearchSettings(SearchSettings searchSettings) {
        this.searchSettings = searchSettings;
    }

    @Override
    public UnbalancedTagsStrategy defaultUnbalancedTagsStrategy() {
        return defaultUnbalancedTagsStrategy;
    }

    @Override
    public void setDefaultUnbalancedTagsStrategy(UnbalancedTagsStrategy strategy) {
        this.defaultUnbalancedTagsStrategy = strategy;
    }

    @Override
    public void setCollator(Collator collator) {
        this.collator = collator;
    }

    @Override
    public Collator collator() {
        return collator;
    }

    @Override
    public IndexMetadataWriter metadata() {
        return indexMetadata;
    }

    @Override
    public void forEachDocument(DocTask task) {
        final int maxDoc = reader().maxDoc();
        final Bits liveDocs = MultiFields.getLiveDocs(reader());
        for (int docId = 0; docId < maxDoc; docId++) {
            if (liveDocs == null || liveDocs.get(docId)) {
                task.perform(doc(docId));
            }
        }
    }

    protected AnnotatedField fieldFromQuery(BLSpanQuery q) {
        return annotatedField(q.getField());
    }

    @Override
    public Hits find(BLSpanQuery query, SearchSettings settings, SearchLogger logger) throws WildcardTermTooBroad {
        QueryInfo queryInfo = QueryInfo.create(this, fieldFromQuery(query), true, logger);
        return Hits.fromSpanQuery(queryInfo, query, settings == null ? searchSettings() : settings);
    }

    @Override
    public QueryExplanation explain(BLSpanQuery query, SearchLogger searchLogger) throws WildcardTermTooBroad {
        try {
            IndexReader indexReader = reader();
            query.setQueryInfo(QueryInfo.create(this, fieldFromQuery(query), true, searchLogger));
            return new QueryExplanation(query, query.optimize(indexReader).rewrite(indexReader));
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public ContentAccessor contentAccessor(Field field) {
        synchronized (contentStores) {
            ContentAccessor ca = contentStores.contentAccessor(field);
            if (indexMode && ca == null) {
                // Index mode. Create new content store or open existing one.
                try {
                    openContentStore(field);
                } catch (ErrorOpeningIndex e) {
                    throw BlackLabRuntimeException.wrap(e);
                }
                ca = contentStores.contentAccessor(field);
            }
            return ca;
        }
    }

    /**
     * Register a ContentStore as a content accessor.
     *
     * This tells the BlackLabIndex how the content of different fields may be accessed.
     * This is used for making concordances, for example. Some fields are stored in
     * the Lucene index, while others may be stored on the file system, a database,
     * etc.
     *
     * A ContentStore is a filesystem-based way to access the contents.
     *
     * @param field the field for which this is the content accessor
     * @param contentStore the ContentStore object by which to access the content
     *
     */
    protected void registerContentStore(Field field, ContentStore contentStore) {
        contentStores.put(field, contentStore);

        // Start reading the content store's TOC in the background, so it doesn't
        // trigger on the first search
        blackLab.initializationExecutorService().execute(new Runnable() {
            @Override
            public void run() {
                //logger.debug("START initialize CS: " + field.name());
                contentStore.initialize();
                //logger.debug("END   initialize CS: " + field.name());
            }
        });
    }

    @Override
    public ForwardIndex forwardIndex(AnnotatedField field) {
        synchronized (forwardIndices) {
            ForwardIndex forwardIndex = forwardIndices.get(field);
            if (forwardIndex == null) {
                forwardIndex = ForwardIndex.open(this, field);
                forwardIndices.put(field, forwardIndex);
            }
            return forwardIndex;
        }
    }

    @Override
    public AnnotationForwardIndex annotationForwardIndex(Annotation annotation) {
        return forwardIndex(annotation.field()).get(annotation);
    }

    protected void addForwardIndex(Annotation annotation, AnnotationForwardIndex forwardIndex) {
        forwardIndex(annotation.field()).put(annotation, forwardIndex);
    }

    @Override
    public MatchSensitivity defaultMatchSensitivity() {
        return defaultMatchSensitivity;
    }

    @Override
    public void setDefaultMatchSensitivity(MatchSensitivity m) {
        defaultMatchSensitivity = m;
    }

    @Override
    public Analyzer analyzer() {
        return analyzer;
    }

    @Override
    public DocResults queryDocuments(Query documentFilterQuery, SearchLogger searchLogger) {
        return DocResults.fromQuery(QueryInfo.create(this, mainAnnotatedField(), true, searchLogger), documentFilterQuery);
    }

    public boolean canDoNfaMatching() {
        if (forwardIndices.isEmpty())
            return false;
        ForwardIndex fi = forwardIndices.values().iterator().next();
        return fi.canDoNfaMatching();
    }

    protected void openIndex(File indexDir, boolean indexMode, boolean createNewIndex)
            throws IOException, CorruptIndexException, LockObtainFailedException {
        if (!indexMode && createNewIndex)
            throw new BlackLabRuntimeException("Cannot create new index, not in index mode");

        if (!createNewIndex) {
            if (!indexMode || VersionFile.exists(indexDir)) {
                if (!BlackLabIndex.isIndex(indexDir)) {
                    throw new IllegalArgumentException("Not a BlackLab index, or wrong version! "
                            + VersionFile.report(indexDir));
                }
            }
        }

        if (traceIndexOpening)
            logger.debug("Constructing BlackLabIndex...");

        if (indexMode) {
            if (traceIndexOpening)
                logger.debug("  Opening IndexWriter...");
            indexWriter = openIndexWriter(indexDir, createNewIndex, null);
            if (traceIndexOpening)
                logger.debug("  Opening corresponding IndexReader...");
            reader = DirectoryReader.open(indexWriter, false);
        } else {
            // Open Lucene index
            if (traceIndexOpening)
                logger.debug("  Following symlinks...");
            Path indexPath = indexDir.toPath().toRealPath();
            while (Files.isSymbolicLink(indexPath)) {
                // Resolve symlinks, as FSDirectory.open() can't handle them
                indexPath = Files.readSymbolicLink(indexPath);
            }
            if (traceIndexOpening)
                logger.debug("  Opening IndexReader...");
            reader = DirectoryReader.open(FSDirectory.open(indexPath));
        }
        this.indexLocation = indexDir;

//      logger.debug("TOTAL TERM FREQ contents%lemma@i: " + reader.getSumTotalTermFreq("contents%lemma@i"));
//      logger.debug("TOTAL TERM FREQ test: " + reader.getSumTotalTermFreq("test"));

    }

    protected void finishOpeningIndex(File indexDir, boolean indexMode, boolean createNewIndex)
            throws IOException, CorruptIndexException, LockObtainFailedException, ErrorOpeningIndex {
        isEmptyIndex = indexMetadata.isNewIndex();

        // TODO: we need to create the analyzer before opening the index, because
        //   we can't change the analyzer attached to the IndexWriter (and passing a different
        //   analyzer in addDocument() went away in Lucene 5.x).
        //   For now, if we're in index mode, we re-open the index with the analyzer we determined.
        if (traceIndexOpening)
            logger.debug("  Creating analyzers...");
        createAnalyzers();

        if (indexMode) {
            // Re-open the IndexWriter with the analyzer we've created above (see comment above)
            if (traceIndexOpening)
                logger.debug("  Re-opening IndexWriter with newly created analyzers...");
            reader.close();
            reader = null;
            indexWriter.close();
            indexWriter = null;
            indexWriter = openIndexWriter(indexDir, createNewIndex, analyzer);
            if (traceIndexOpening)
                logger.debug("  IndexReader too...");
            reader = DirectoryReader.open(indexWriter, false);
        }

        // Register ourselves in the mapping from IndexReader to BlackLabIndex,
        // so we can find the corresponding BlackLabIndex object from within Lucene code
        blackLab.registerSearcher(reader, this);

        // Detect and open the ContentStore for the contents field
        if (!createNewIndex) {
            if (traceIndexOpening)
                logger.debug("  Determining main contents field name...");
            AnnotatedField mainContentsField = indexMetadata.mainAnnotatedField();
            if (mainContentsField == null) {
                if (!indexMode) {
                    if (!isEmptyIndex)
                        throw new BlackLabRuntimeException("Could not detect main contents field");
                }
            }

            // Register content stores
            if (traceIndexOpening)
                logger.debug("  Opening content stores...");
            for (AnnotatedField field: indexMetadata.annotatedFields()) {
                if (field.hasContentStore()) {
                    File dir = new File(indexDir, "cs_" + field.name());
                    if (dir.exists()) {
                        if (traceIndexOpening)
                            logger.debug("    " + dir + "...");
                        registerContentStore(field, ContentStore.open(dir, indexMode, false));
                    }
                }
            }
        }

        if (traceIndexOpening)
            logger.debug("  Opening IndexSearcher...");
        indexSearcher = new IndexSearcher(reader);

        // Make sure large wildcard/regex expansions succeed
        if (traceIndexOpening)
            logger.debug("  Setting maxClauseCount...");
        BooleanQuery.setMaxClauseCount(100_000);

        // Open the forward indices
        if (!createNewIndex) {
            if (traceIndexOpening)
                logger.debug("  Opening forward indices...");
            for (AnnotatedField field: annotatedFields()) {
                for (Annotation annotation: field.annotations()) {
                    if (annotation.hasForwardIndex()) {
                        // This annotation has a forward index. Make sure it is open.
                        if (traceIndexOpening)
                            logger.debug("    " + annotation.luceneFieldPrefix() + "...");
                        annotationForwardIndex(annotation);
                    }
                }
            }
        }
    }

    @Override
    public boolean isEmpty() {
        return isEmptyIndex;
    }

    private void createAnalyzers() {
        Map<String, Analyzer> fieldAnalyzers = new HashMap<>();
        fieldAnalyzers.put("fromInputFile", analyzerInstance("nontokenizing"));
        Analyzer baseAnalyzer = analyzerInstance(indexMetadata.metadataFields().defaultAnalyzerName());
        for (MetadataField field: indexMetadata.metadataFields()) {
            String analyzerName = field.analyzerName();
            if (field.type() == FieldType.UNTOKENIZED)
                analyzerName = "nontokenizing";
            if (analyzerName.length() > 0 && !analyzerName.equalsIgnoreCase("default")) {
                Analyzer fieldAnalyzer = analyzerInstance(analyzerName);
                if (fieldAnalyzer == null) {
                    logger.error("Unknown analyzer name " + analyzerName + " for field " + field.name());
                } else {
                    if (fieldAnalyzer != baseAnalyzer)
                        fieldAnalyzers.put(field.name(), fieldAnalyzer);
                }
            }
        }

        analyzer = new PerFieldAnalyzerWrapper(baseAnalyzer, fieldAnalyzers);
    }

    @Override
    public void close() {
        try {
            if (blackLab != null) {
                blackLab.removeSearcher(this);
                blackLab = null;
            }

            if (reader != null) {
                reader.close();
                reader = null;
            }
            if (indexWriter != null) {
                indexWriter.commit();
                indexWriter.close();
                indexWriter = null;
            }

            if (contentStores != null) {
                contentStores.close();
                contentStores = null;
            }

            // Close the forward indices
            if (forwardIndices != null) {
                for (ForwardIndex fi : forwardIndices.values()) {
                    fi.close();
                }
                forwardIndices = null;
            }

        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public Doc doc(int docId) {
        return Doc.get(this, docId);
    }

    @Override
    public boolean docExists(int docId) {
        if (docId < 0 || docId >= reader.maxDoc())
            return false;
        Bits liveDocs = MultiFields.getLiveDocs(reader);
        return liveDocs == null || liveDocs.get(docId);
    }

    @Override
    public IndexReader reader() {
        return reader;
    }

    protected ContentStore openContentStore(Field field) throws ErrorOpeningIndex {
        File contentStoreDir = new File(indexLocation, "cs_" + field.name());
        ContentStore contentStore = ContentStore.open(contentStoreDir, indexMode, isEmptyIndex);
        registerContentStore(field, contentStore);
        return contentStore;
    }

    @Override
    public QueryExecutionContext defaultExecutionContext(AnnotatedField annotatedField) {
        if (annotatedField == null)
            throw new IllegalArgumentException("Unknown annotated field: null");
        Annotation mainAnnotation = annotatedField.mainAnnotation();
        if (mainAnnotation == null)
            throw new IllegalArgumentException("Main annotation not found for " + annotatedField.name());
        return new QueryExecutionContext(this, mainAnnotation, defaultMatchSensitivity);
    }

    @Override
    public String name() {
        return indexLocation.toString();
    }

    @Override
    public IndexSearcher searcher() {
        return indexSearcher;
    }

    // Methods for mutating the index
    //----------------------------------------------------------------

    @Override
    @Deprecated
    public IndexMetadataWriter metadataWriter() {
        return metadata();
    }

    @Override
    public IndexWriter openIndexWriter(File indexDir, boolean create, Analyzer useAnalyzer) throws IOException,
            CorruptIndexException, LockObtainFailedException {
        if (!indexDir.exists() && create) {
            if (!indexDir.mkdir())
                throw new BlackLabRuntimeException("Could not create dir: " + indexDir);
        }
        Path indexPath = indexDir.toPath();
        while (Files.isSymbolicLink(indexPath)) {
            // Resolve symlinks, as FSDirectory.open() can't handle them
            indexPath = Files.readSymbolicLink(indexPath);
        }
        Directory indexLuceneDir = FSDirectory.open(indexPath);
        if (useAnalyzer == null)
            useAnalyzer = new BLDutchAnalyzer();
        IndexWriterConfig config = LuceneUtil.getIndexWriterConfig(useAnalyzer, create);
        IndexWriter writer = new IndexWriter(indexLuceneDir, config);

        if (create)
            VersionFile.write(indexDir, "blacklab", "2");
        else {
            if (!BlackLabIndex.isIndex(indexDir)) {
                throw new IllegalArgumentException("Not a BlackLab index, or wrong type or version! "
                        + VersionFile.report(indexDir) + ": " + indexDir);
            }
        }

        return writer;
    }

    @Override
    public IndexWriter writer() {
        return indexWriter;
    }

    @Override
    public File indexDirectory() {
        return indexLocation;
    }

    protected void deleteFromForwardIndices(Document d) {
        // Delete this document in all forward indices
        for (Map.Entry<AnnotatedField, ForwardIndex> e : forwardIndices.entrySet()) {
            AnnotatedField field = e.getKey();
            ForwardIndex fi = e.getValue();
            for (Annotation annotation: field.annotations()) {
                if (annotation.hasForwardIndex())
                    fi.get(annotation).deleteDocumentByLuceneDoc(d);
            }
        }
    }

    @Override
    public Annotation getOrCreateAnnotation(AnnotatedField field, String annotName) {
        if (field.annotations().exists(annotName))
            return field.annotation(annotName);
        AnnotatedFieldImpl fld = (AnnotatedFieldImpl)field;
        return fld.getOrCreateAnnotation(annotName);
    }

    @Override
    public void rollback() {
        try {
            indexWriter.rollback();
            indexWriter = null;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public void delete(Query q) {
        logger.debug("Delete query: " + q);
        if (!indexMode)
            throw new BlackLabRuntimeException("Cannot delete documents, not in index mode");
        try {
            // Open a fresh reader to execute the query
            try (IndexReader freshReader = DirectoryReader.open(indexWriter, false)) {
                // Execute the query, iterate over the docs and delete from FI and CS.
                IndexSearcher s = new IndexSearcher(freshReader);
                Weight w = s.createNormalizedWeight(q, false);
                logger.debug("Doing delete. Number of leaves: " + freshReader.leaves().size());
                for (LeafReaderContext leafContext : freshReader.leaves()) {
                    Bits liveDocs = leafContext.reader().getLiveDocs();

                    Scorer scorer = w.scorer(leafContext);
                    if (scorer == null) {
                        logger.debug("  No hits in leafcontext");
                        continue; // no matching documents
                    }

                    // Iterate over matching docs
                    DocIdSetIterator it = scorer.iterator();
                    logger.debug("  Iterate over matching docs in leaf");
                    while (true) {
                        int docId = it.nextDoc();
                        if (docId == DocIdSetIterator.NO_MORE_DOCS)
                            break;
                        if (liveDocs != null && !liveDocs.get(docId)) {
                            // already deleted.
                            continue;
                        }
                        docId += leafContext.docBase;
                        Document d = freshReader.document(docId);
                        logger.debug("    About to delete docId " + docId + ", fromInputFile=" + d.get("fromInputFile") + " from FI and CS");

                        deleteFromForwardIndices(d);

                        // Delete this document in all content stores
                        contentStores.deleteDocument(d);
                    }
                }
            } finally {
                reader.close();
            }

            // Finally, delete the documents from the Lucene index
            logger.debug("  Delete docs from Lucene index");
            indexWriter.deleteDocuments(q);

        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((indexLocation == null) ? 0 : indexLocation.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BlackLabIndexImpl other = (BlackLabIndexImpl) obj;
        if (indexLocation == null) {
            if (other.indexLocation != null)
                return false;
        } else if (!indexLocation.equals(other.indexLocation))
            return false;
        return true;
    }

    @Override
    public void setDefaultContextSize(ContextSize defaultContextSize) {
        this.defaultContextSize = defaultContextSize;
    }

    @Override
    public ContextSize defaultContextSize() {
        return defaultContextSize;
    }

    @Override
    public boolean indexMode() {
        return indexMode;
    }

    @Override
    public TermFrequencyList termFrequencies(AnnotationSensitivity annotSensitivity, Query filterQuery, Set<String> terms) {
        Map<String, Integer> freq = LuceneUtil.termFrequencies(searcher(), filterQuery, annotSensitivity, terms);
        return new TermFrequencyList(QueryInfo.create(this, annotSensitivity.annotation().field()), freq, true);
    }

    @Override
    public SearchEmpty search(AnnotatedField field, boolean useCache, SearchLogger searchLogger) {
        return new SearchEmpty(QueryInfo.create(this, field, useCache, searchLogger));
    }

    @Override
    public SearchCache cache() {
        return cache;
    }

    @Override
    public void setCache(SearchCache cache) {
        this.cache = cache;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(" + indexLocation + ")";
    }

    @Override
    public BlackLabEngine blackLab() {
        return blackLab;
    }

    @Override
    public boolean isOpen() {
        return indexWriter.isOpen();
    }
}
