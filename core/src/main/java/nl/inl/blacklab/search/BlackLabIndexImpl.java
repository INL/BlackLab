package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
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
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldImpl;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataImpl;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.HitsImpl;
import nl.inl.blacklab.search.results.HitsSettings;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.util.ExUtil;
import nl.inl.util.LuceneUtil;
import nl.inl.util.VersionFile;
import nl.inl.util.XmlHighlighter.UnbalancedTagsStrategy;

public class BlackLabIndexImpl implements BlackLabIndex, BlackLabIndexWriter {
    
    // Class variables
    //---------------------------------------------------------------

    protected static final Logger logger = LogManager.getLogger(BlackLabIndexImpl.class);

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

    /** Analyzer based on WhitespaceTokenizer */
    final protected static Analyzer whitespaceAnalyzer = new BLWhitespaceAnalyzer();

    /** Analyzer for Dutch and other Latin script languages */
    final protected static Analyzer defaultAnalyzer = new BLDutchAnalyzer();

    /** Analyzer based on StandardTokenizer */
    final protected static Analyzer standardAnalyzer = new BLStandardAnalyzer();

    /** Analyzer that doesn't tokenize */
    final protected static Analyzer nonTokenizingAnalyzer = new BLNonTokenizingAnalyzer();

    
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

    public static Collator getDefaultCollator() {
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
    public static String getBlackLabBuildTime() {
        return getValueFromManifest("Build-Time", "UNKNOWN");
    }

    /**
     * Return the BlackLab version.
     *
     * @return BlackLab version, or UNKNOWN if the version could not be found for
     *         some reason (i.e. not running from a JAR, or key not found in
     *         manifest).
     */
    public static String getBlackLabVersion() {
        return getValueFromManifest("Implementation-Version", "UNKNOWN");
    }

    /**
     * Get a value from the manifest file, if available.
     *
     * @param key key to get the value for, e.g. "Build-Time".
     * @param defaultValue value to return if no manifest found or key not found
     * @return value from the manifest, or the default value if not found
     */
    static String getValueFromManifest(String key, String defaultValue) {
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
    static Analyzer getAnalyzerInstance(String analyzerName) {
        analyzerName = analyzerName.toLowerCase();
        if (analyzerName.equals("whitespace")) {
            return whitespaceAnalyzer;
        } else if (analyzerName.equals("default")) {
            return defaultAnalyzer;
        } else if (analyzerName.equals("standard")) {
            return standardAnalyzer;
        } else if (analyzerName.matches("(non|un)tokeniz(ing|ed)")) {
            return nonTokenizingAnalyzer;
        }
        return null;
    }

    public static void setTraceIndexOpening(boolean traceIndexOpening) {
        logger.debug("Trace index opening: " + traceIndexOpening);
        BlackLabIndexImpl.traceIndexOpening = traceIndexOpening;
    }

    public static boolean isTraceOptimization() {
        return traceOptimization;
    }

    public static void setTraceOptimization(boolean traceOptimization) {
        logger.debug("Trace optimization: " + traceOptimization);
        BlackLabIndexImpl.traceOptimization = traceOptimization;
    }

    public static void setTraceQueryExecution(boolean traceQueryExecution) {
        logger.debug("Trace query execution: " + traceQueryExecution);
        BlackLabIndexImpl.traceQueryExecution = traceQueryExecution;
    }

    // Instance variables
    //---------------------------------------------------------------
    

    /** The collator to use for sorting. Defaults to English collator. */
    private Collator collator = BlackLabIndexImpl.defaultCollator;

    /** Analyzer used for indexing our metadata fields */
    protected Analyzer analyzer = new BLStandardAnalyzer();

    /** Structure of our index */
    protected IndexMetadata indexMetadata;
    
    protected IndexMetadataWriter indexMetadataWriter;

    protected ContentStoresManager contentStores = new ContentStoresManager();

    /**
     * ForwardIndices allow us to quickly find what token occurs at a specific
     * position. This speeds up grouping and sorting. There may be several indices
     * on a annotated field, e.g.: word form, lemma, part of speech.
     *
     * Indexed by annotation.
     */
    protected Map<Annotation, ForwardIndex> forwardIndices = new HashMap<>();

    protected HitsSettings hitsSettings;

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

    
    // Constructors
    //---------------------------------------------------------------
    

    public BlackLabIndexImpl(HitsSettings settings) {
        hitsSettings = settings == null ? HitsSettings.defaults() : settings;
    }

    public BlackLabIndexImpl() {
        this(null);
    }

    /**
     * Open an index.
     *
     * @param indexDir the index directory
     * @param indexMode if true, open in index mode; if false, open in search mode.
     * @param createNewIndex if true, delete existing index in this location if it
     *            exists.
     * @param config input format config to use as template for index structure /
     *            metadata (if creating new index)
     * @throws IOException
     */
    BlackLabIndexImpl(File indexDir, boolean indexMode, boolean createNewIndex, ConfigInputFormat config)
            throws IOException {
        this();
        this.indexMode = indexMode;

        try {
            ConfigReader.applyConfig(this);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage() + " (BlackLab configuration file)", e.getCause());
        }

        openIndex(indexDir, indexMode, createNewIndex);

        // Determine the index structure
        if (traceIndexOpening)
            logger.debug("  Determining index structure...");
        IndexMetadataImpl indexMetadataImpl = new IndexMetadataImpl(reader, indexDir, createNewIndex, config);
        indexMetadata = indexMetadataImpl;
        if (indexMode)
            indexMetadataWriter = indexMetadataImpl;
        else
            indexMetadata.freeze();

        finishOpeningIndex(indexDir, indexMode, createNewIndex);
    }

    /**
     * Open an index.
     *
     * @param indexDir the index directory
     * @param indexMode if true, open in index mode; if false, open in search mode.
     * @param createNewIndex if true, delete existing index in this location if it
     *            exists.
     * @param indexTemplateFile index template file to use to create index
     * @throws ErrorOpeningIndex
     */
    BlackLabIndexImpl(File indexDir, boolean indexMode, boolean createNewIndex, File indexTemplateFile)
            throws ErrorOpeningIndex {
        this(indexDir, indexMode, createNewIndex, indexTemplateFile, null);
    }

    /**
     * Open an index.
     *
     * @param indexDir the index directory
     * @param indexMode if true, open in index mode; if false, open in search mode.
     * @param createNewIndex if true, delete existing index in this location if it
     *            exists.
     * @param indexTemplateFile index template file to use to create index
     * @param settings default search settings
     * @throws ErrorOpeningIndex
     */
    BlackLabIndexImpl(File indexDir, boolean indexMode, boolean createNewIndex, File indexTemplateFile, HitsSettings settings) throws ErrorOpeningIndex {
        this(settings);
        this.indexMode = indexMode;

        try {
            ConfigReader.applyConfig(this);

            openIndex(indexDir, indexMode, createNewIndex);

            // Determine the index structure
            if (traceIndexOpening)
                logger.debug("  Determining index structure...");
            IndexMetadataImpl indexMetadataImpl = new IndexMetadataImpl(reader, indexDir, createNewIndex, indexTemplateFile);
            indexMetadata = indexMetadataImpl;
            if (indexMode)
                indexMetadataWriter = indexMetadataImpl;
            else
                indexMetadata.freeze();

            finishOpeningIndex(indexDir, indexMode, createNewIndex);
        } catch (IOException e) {
            throw new ErrorOpeningIndex(e);
        }
    }

    // Methods for querying the index
    //---------------------------------------------------------------
    

    @Override
    public HitsSettings hitsSettings() {
        return hitsSettings;
    }
    
    @Override
    public void setHitsSettings(HitsSettings hitsSettings) {
        this.hitsSettings = hitsSettings;
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
    public IndexMetadata metadata() {
        return indexMetadata;
    }
    
    @Override
    public void forEachDocument(DocTask task) {
        for (Integer docId : docIdSet()) {
            task.perform(doc(docId));
        }
    }

    @Override
    public BLSpanQuery createSpanQuery(TextPattern pattern, AnnotatedField field, Query filter) {
        // Convert to SpanQuery
        //pattern = pattern.rewrite();
        BLSpanQuery spanQuery = pattern.translate(defaultExecutionContext(field));
        if (filter != null)
            spanQuery = new SpanQueryFiltered(spanQuery, filter);
        return spanQuery;
    }

    @Override
    public Hits find(BLSpanQuery query, HitsSettings settings) throws BooleanQuery.TooManyClauses {
        return HitsImpl.fromSpanQuery(this, query, settings);
    }

    @Override
    public Hits find(TextPattern pattern, AnnotatedField field, Query filter, HitsSettings settings)
            throws BooleanQuery.TooManyClauses {
        return HitsImpl.fromSpanQuery(this, createSpanQuery(pattern, field, filter), settings);
    }

    @Override
    public QueryExplanation explain(BLSpanQuery query) throws BooleanQuery.TooManyClauses {
        try {
            IndexReader indexReader = reader();
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
                openContentStore(field);
                ca = contentStores.contentAccessor(field);
            }
            return ca;
        }
    }

    /**
     * Register a ContentStore as a content accessor.
     *
     * This tells the Searcher how the content of different fields may be accessed.
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
    }

    @Override
    public ForwardIndex forwardIndex(Annotation annotation) {
        synchronized (forwardIndices) {
            ForwardIndex forwardIndex = forwardIndices.get(annotation);
            if (forwardIndex == null) {
                forwardIndex = openForwardIndex(annotation);
                if (forwardIndex == null)
                    throw new IllegalArgumentException("Annotation has no forward index: " + annotation);
                addForwardIndex(annotation, forwardIndex);
            }
            return forwardIndex;
        }
    }

    protected void addForwardIndex(Annotation annotation, ForwardIndex forwardIndex) {
        forwardIndices.put(annotation, forwardIndex);
    }

//    protected abstract ForwardIndex openForwardIndex(Annotation annotation);

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
    public DocResults queryDocuments(Query documentFilterQuery) {
        return DocResults.fromQuery(this, documentFilterQuery);
    }
    
    public boolean canDoNfaMatching() {
        if (forwardIndices.isEmpty())
            return false;
        ForwardIndex fi = forwardIndices.values().iterator().next();
        return fi.canDoNfaMatching();
    }

    public static boolean isTraceQueryExecution() {
        return traceQueryExecution;
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
            logger.debug("Constructing Searcher...");

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
            Path indexPath = indexDir.toPath();
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
            throws IOException, CorruptIndexException, LockObtainFailedException {
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

        // Register ourselves in the mapping from IndexReader to Searcher,
        // so we can find the corresponding Searcher object from within Lucene code
        BlackLabIndexRegistry.registerSearcher(reader, this);

        // Detect and open the ContentStore for the contents field
        if (!createNewIndex) {
            if (traceIndexOpening)
                logger.debug("  Determining main contents field name...");
            AnnotatedField mainContentsField = indexMetadata.annotatedFields().main();
            if (mainContentsField == null) {
                if (!indexMode) {
                    if (!isEmptyIndex)
                        throw new BlackLabRuntimeException("Could not detect main contents field");
                }
            } else {
                // See if we have a punctuation forward index. If we do,
                // default to creating concordances using that.
                if (mainContentsField.hasPunctuationForwardIndex()) {
                    hitsSettings = hitsSettings.withConcordanceType(ConcordanceType.FORWARD_INDEX);
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
                        registerContentStore(field, ContentStore.open(dir, false));
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
        BooleanQuery.setMaxClauseCount(100000);

        // Open the forward indices
        if (!createNewIndex) {
            if (traceIndexOpening)
                logger.debug("  Opening forward indices...");
            openForwardIndices();
        }
    }

    @Override
    public boolean isEmpty() {
        return isEmptyIndex;
    }

    private void createAnalyzers() {
        Map<String, Analyzer> fieldAnalyzers = new HashMap<>();
        fieldAnalyzers.put("fromInputFile", getAnalyzerInstance("nontokenizing"));
        Analyzer baseAnalyzer = getAnalyzerInstance(indexMetadata.metadataFields().defaultAnalyzerName());
        for (MetadataField field: indexMetadata.metadataFields()) {
            String analyzerName = field.analyzerName();
            if (field.type() == FieldType.UNTOKENIZED)
                analyzerName = "nontokenizing";
            if (analyzerName.length() > 0 && !analyzerName.equalsIgnoreCase("default")) {
                Analyzer fieldAnalyzer = getAnalyzerInstance(analyzerName);
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
            reader.close();
            if (indexWriter != null) {
                indexWriter.commit();
                indexWriter.close();
            }

            contentStores.close();

            // Close the forward indices
            for (ForwardIndex fi : forwardIndices.values()) {
                fi.close();
            }

            BlackLabIndexRegistry.removeSearcher(this);

        } catch (IOException e) {
            throw ExUtil.wrapRuntimeException(e);
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

    protected ContentStore openContentStore(Field field) {
        File contentStoreDir = new File(indexLocation, "cs_" + field.name());
        ContentStore contentStore = ContentStore.open(contentStoreDir, isEmptyIndex);
        registerContentStore(field, contentStore);
        return contentStore;
    }

    /**
     * Opens all the forward indices, to avoid this delay later.
     *
     * NOTE: used to be public; now private because it's done automatically when
     * constructing the Searcher.
     */
    private void openForwardIndices() {
        for (AnnotatedField field: indexMetadata.annotatedFields()) {
            for (Annotation annotation: field.annotations()) {
                if (annotation.hasForwardIndex()) {
                    // This annotation has a forward index. Make sure it is open.
                    if (traceIndexOpening)
                        logger.debug("    " + annotation.luceneFieldPrefix() + "...");
                    forwardIndex(annotation);
                }
            }
        }
    }

    protected ForwardIndex openForwardIndex(Annotation annotation) {
        ForwardIndex forwardIndex;
        File dir = new File(indexLocation, "fi_" + annotation.luceneFieldPrefix());
        if (!isEmptyIndex && !dir.exists()) {
            // Forward index doesn't exist
            return null;
        }
        // Open forward index
        forwardIndex = ForwardIndex.open(dir, indexMode, collator(), isEmptyIndex);
        forwardIndex.setIdTranslateInfo(reader, annotation); // how to translate from
                                                                // Lucene
                                                                // doc to fiid
        return forwardIndex;
    }

    @Override
    public QueryExecutionContext defaultExecutionContext(AnnotatedField annotatedField) {
        if (annotatedField == null)
            throw new IllegalArgumentException("Unknown annotated field: null");
        Annotation mainAnnotation = annotatedField.annotations().main();
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

    private Set<Integer> docIdSet() {

        final int maxDoc = reader.maxDoc();

        final Bits liveDocs = MultiFields.getLiveDocs(reader);

        return new AbstractSet<Integer>() {
            @Override
            public boolean contains(Object o) {
                Integer i = (Integer) o;
                return i < maxDoc && !isDeleted(i);
            }

            boolean isDeleted(Integer i) {
                return liveDocs != null && !liveDocs.get(i);
            }

            @Override
            public boolean isEmpty() {
                return maxDoc == reader.numDeletedDocs() + 1;
            }

            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {
                    int current = -1;
                    int next = -1;

                    @Override
                    public boolean hasNext() {
                        if (next < 0)
                            findNext();
                        return next < maxDoc;
                    }

                    private void findNext() {
                        next = current + 1;
                        while (next < maxDoc && isDeleted(next)) {
                            next++;
                        }
                    }

                    @Override
                    public Integer next() {
                        if (next < 0)
                            findNext();
                        if (next >= maxDoc)
                            throw new NoSuchElementException();
                        current = next;
                        next = -1;
                        return current;
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public int size() {
                return maxDoc - reader.numDeletedDocs() - 1;
            }
        };
    }
    
    // Methods for mutating the index
    //----------------------------------------------------------------
    
    @Override
    public IndexMetadataWriter metadataWriter() {
        return indexMetadataWriter;
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
                        + VersionFile.report(indexDir));
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
        for (Map.Entry<Annotation, ForwardIndex> e : forwardIndices.entrySet()) {
            Annotation annotation = e.getKey();
            ForwardIndex fi = e.getValue();
            int fiid = Integer.parseInt(d.get(annotation.forwardIndexIdField()));
            fi.deleteDocument(fiid);
        }
    }

    @Override
    public Annotation getOrCreateAnnotation(AnnotatedField field, String annotName) {
        if (field.annotations().exists(annotName))
            return field.annotations().get(annotName);
        AnnotatedFieldImpl fld = (AnnotatedFieldImpl)field;
        return fld.getOrCreateAnnotation(annotName);
    }

    @Override
    public void rollback() {
        try {
            indexWriter.rollback();
            indexWriter = null;
        } catch (IOException e) {
            throw ExUtil.wrapRuntimeException(e);
        }
    }

    @Override
    public void delete(Query q) {
        if (!indexMode)
            throw new BlackLabRuntimeException("Cannot delete documents, not in index mode");
        try {
            // Open a fresh reader to execute the query
            try (IndexReader freshReader = DirectoryReader.open(indexWriter, false)) {
                // Execute the query, iterate over the docs and delete from FI and CS.
                IndexSearcher s = new IndexSearcher(freshReader);
                Weight w = s.createNormalizedWeight(q, false);
                for (LeafReaderContext leafContext : freshReader.leaves()) {
                    Scorer scorer = w.scorer(leafContext);
                    if (scorer == null)
                        return; // no matching documents

                    // Iterate over matching docs
                    DocIdSetIterator it = scorer.iterator();
                    while (true) {
                        int docId = it.nextDoc() + leafContext.docBase;
                        if (docId == DocIdSetIterator.NO_MORE_DOCS)
                            break;
                        Document d = freshReader.document(docId);

                        deleteFromForwardIndices(d);

                        // Delete this document in all content stores
                        contentStores.deleteDocument(d);
                    }
                }
            } finally {
                reader.close();
            }

            // Finally, delete the documents from the Lucene index
            indexWriter.deleteDocuments(q);

        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }


}
