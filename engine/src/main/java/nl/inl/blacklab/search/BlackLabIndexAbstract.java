package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexFormatTooNewException;
import org.apache.lucene.index.IndexFormatTooOldException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.MultiBits;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.analysis.BuiltinAnalyzers;
import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.contentstore.ContentStoreExternal;
import nl.inl.blacklab.contentstore.ContentStoreIntegrated;
import nl.inl.blacklab.contentstore.ContentStoresManager;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.index.BLIndexObjectFactory;
import nl.inl.blacklab.index.BLIndexWriterProxy;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.FieldType;
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
import nl.inl.util.XmlHighlighter.UnbalancedTagsStrategy;

public abstract class BlackLabIndexAbstract implements BlackLabIndexWriter {
    /** Document length in Lucene and forward index is always reported as one
     *  higher due to punctuation being a trailing value. We call this the
     *  "extra closing token". */
    public static final int IGNORE_EXTRA_CLOSING_TOKEN = 1;

    // Class variables
    //---------------------------------------------------------------

    protected static final Logger logger = LogManager.getLogger(BlackLabIndexAbstract.class);

    // Instance variables
    //---------------------------------------------------------------

    /** BlackLab instance used to create us */
    private final BlackLabEngine blackLab;

    /** The collator to use for sorting. Defaults to English collator. */
    private Collator collator = BlackLab.defaultCollator();

    /** Analyzer used for indexing our metadata fields */
    private Analyzer analyzer = BuiltinAnalyzers.STANDARD.getAnalyzer();

    /** Structure of our index */
    private final IndexMetadataWriter indexMetadata;

    final ContentStoresManager contentStores = new ContentStoresManager();

    /**
     * ForwardIndices allow us to quickly find what token occurs at a specific
     * position. This speeds up grouping and sorting. There may be several indices
     * on a annotated field, e.g.: word form, lemma, part of speech.
     *
     * Indexed by annotation.
     */
    protected final Map<AnnotatedField, ForwardIndex> forwardIndices = new HashMap<>();

    private SearchSettings searchSettings;

    /** Should we default to case-/diacritics-sensitive searching? [default: both insensitive] */
    private MatchSensitivity defaultMatchSensitivity = MatchSensitivity.INSENSITIVE;

    /**
     * How we fix well-formedness for snippets of XML: by adding or removing
     * unbalanced tags
     */
    private final UnbalancedTagsStrategy defaultUnbalancedTagsStrategy = UnbalancedTagsStrategy.ADD_TAG;

    /** If true, we want to add/delete documents. If false, we're just searching. */
    private final boolean indexMode;

    /**
     * The Lucene index reader
     */
    private IndexReader reader;

    /**
     * The Lucene IndexSearcher, for dealing with non-Span queries (for per-document
     * scoring)
     */
    private IndexSearcher indexSearcher;

    /**
     * Directory where our index resides
     */
    private final File indexLocation;

    /**
     * If true, we've just created a new index. New indices cannot be searched, only
     * added to.
     */
    private boolean isEmptyIndex = false;

    /** The index writer. Only valid in indexMode. */
    BLIndexWriterProxy indexWriter = null;

    /** How many words of context around matches to return by default */
    private ContextSize defaultContextSize = BlackLabIndex.DEFAULT_CONTEXT_SIZE;

    /** Search cache to use */
    private SearchCache cache = new SearchCacheDummy();

    /** Was this index closed? */
    private boolean closed;


    // Constructors
    //---------------------------------------------------------------


    /**
     * Open an index.
     *
     * @param blackLab BlackLab engine
     * @param indexDir the index directory
     * @param indexMode if true, open in index mode; if false, open in search mode.
     * @param createNewIndex if true, delete existing index in this location if it
     *         exists.
     * @param config input format config to use as template for index structure /
     *            metadata (if creating new index)
     * @throws IndexVersionMismatch if the index is too old or too new to be opened by this BlackLab version
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    BlackLabIndexAbstract(BlackLabEngine blackLab, File indexDir, boolean indexMode, boolean createNewIndex,
            ConfigInputFormat config) throws ErrorOpeningIndex {
        this.blackLab = blackLab;
        this.indexLocation = indexDir;
        searchSettings = SearchSettings.defaults();
        try {
            this.indexMode = indexMode;

            try {
                BlackLab.applyConfigToIndex(this);
            } catch (InvalidConfiguration e) {
                throw new InvalidConfiguration(e.getMessage() + " (BlackLab configuration file)", e.getCause());
            }

            if (!indexMode && createNewIndex)
                throw new BlackLabRuntimeException("Cannot create new index, not in index mode");
            checkCanOpenIndex(indexMode, createNewIndex);
            reader = openIndex(indexMode, createNewIndex);

            // Determine the index structure
            if (traceIndexOpening())
                logger.debug("  Determining index structure...");
            indexMetadata = getIndexMetadata(createNewIndex, config);
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
     *         exists.
     * @param indexTemplateFile index template file to use to create index
     */
    BlackLabIndexAbstract(BlackLabEngine blackLab, File indexDir, boolean indexMode, boolean createNewIndex,
            File indexTemplateFile) throws ErrorOpeningIndex {
        this.blackLab = blackLab;
        this.indexLocation = indexDir;
        searchSettings = SearchSettings.defaults();
        this.indexMode = indexMode;

        try {
            BlackLab.applyConfigToIndex(this);

            if (!indexMode && createNewIndex)
                throw new BlackLabRuntimeException("Cannot create new index, not in index mode");
            reader = openIndex(indexMode, createNewIndex);

            // Determine the index structure
            if (traceIndexOpening())
                logger.debug("  Determining index structure...");
            indexMetadata = getIndexMetadata(createNewIndex, indexTemplateFile);
            if (!indexMode)
                indexMetadata.freeze();

            finishOpeningIndex(indexDir, indexMode, createNewIndex);

        } catch (IndexFormatTooNewException|IndexFormatTooOldException e) { 
            throw new IndexVersionMismatch(e);
        } catch (IOException e) {
            throw new ErrorOpeningIndex(e);
        }
    }

    public BLIndexObjectFactory indexObjectFactory() {
        return blackLab.indexObjectFactory();
    }

    protected abstract IndexMetadataWriter getIndexMetadata(boolean createNewIndex, ConfigInputFormat config)
            throws IndexVersionMismatch;

    protected abstract IndexMetadataWriter getIndexMetadata(boolean createNewIndex, File indexTemplateFile)
            throws IndexVersionMismatch;

    boolean traceIndexOpening() {
        return BlackLab.config().getLog().getTrace().isIndexOpening();
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
        final Bits liveDocs = MultiBits.getLiveDocs(reader());
        int skipDocId = metadata().metadataDocId();
        for (int docId = 0; docId < maxDoc; docId++) {
            boolean isLiveDoc = liveDocs == null || liveDocs.get(docId);
            if (isLiveDoc && docId != skipDocId) {
                task.perform(this, docId);
            }
        }
    }

    protected AnnotatedField fieldFromQuery(BLSpanQuery q) {
        return annotatedField(q.getField());
    }

    @Override
    public Hits find(BLSpanQuery query, SearchSettings settings) {
        QueryInfo queryInfo = QueryInfo.create(this, fieldFromQuery(query), true);
        return Hits.fromSpanQuery(queryInfo, query, settings == null ? searchSettings() : settings);
    }

    @Override
    public QueryExplanation explain(BLSpanQuery query) {
        try {
            IndexReader indexReader = reader();
            query.setQueryInfo(QueryInfo.create(this, fieldFromQuery(query), true));
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
                    boolean createNewContentStore = isEmptyIndex;
                    openContentStore(field, true, createNewContentStore, indexLocation);
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
     */
    protected void registerContentStore(Field field, ContentStore contentStore) {
        contentStores.put(field, contentStore);

        // Start reading the content store's TOC in the background, so it doesn't
        // trigger on the first search
        blackLab.initializationExecutorService().execute(contentStore::initialize);
    }

    @Override
    public ForwardIndex forwardIndex(AnnotatedField field) {
        synchronized (forwardIndices) {
            ForwardIndex forwardIndex = forwardIndices.get(field);
            if (forwardIndex == null) {
                forwardIndex = createForwardIndex(field);
                forwardIndices.put(field, forwardIndex);
            }
            return forwardIndex;
        }
    }

    @Override
    public AnnotationForwardIndex annotationForwardIndex(Annotation annotation) {
        return forwardIndex(annotation.field()).get(annotation);
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
    public DocResults queryDocuments(Query documentFilterQuery) {
        return DocResults.fromQuery(QueryInfo.create(this, mainAnnotatedField(), true), documentFilterQuery);
    }

    public boolean canDoNfaMatching() {
        if (forwardIndices.isEmpty())
            return false;
        ForwardIndex fi = forwardIndices.values().iterator().next();
        return fi.canDoNfaMatching();
    }

    protected void checkCanOpenIndex(boolean indexMode, boolean createNewIndex) throws IllegalArgumentException {
        // subclass can override this
    }

    protected IndexReader openIndex(boolean indexMode, boolean createNewIndex) throws IOException,
            IndexVersionMismatch {
        if (traceIndexOpening())
            logger.debug("Constructing BlackLabIndex...");

        if (indexMode) {
            if (traceIndexOpening())
                logger.debug("  Opening IndexWriter...");
            IndexWriter luceneIndexWriter = openIndexWriter(indexLocation, createNewIndex, null);
            indexWriter = indexObjectFactory().indexWriterProxy(luceneIndexWriter);
            if (traceIndexOpening())
                logger.debug("  Opening corresponding IndexReader...");
            return DirectoryReader.open(luceneIndexWriter, false, false);
        } else {
            // Open Lucene index
            if (traceIndexOpening())
                logger.debug("  Following symlinks...");
            Path indexPath = indexLocation.toPath().toRealPath();
            while (Files.isSymbolicLink(indexPath)) {
                // Resolve symlinks, as FSDirectory.open() can't handle them
                indexPath = Files.readSymbolicLink(indexPath);
            }
            if (traceIndexOpening())
                logger.debug("  Opening IndexReader...");
            try {
                return DirectoryReader.open(FSDirectory.open(indexPath));
            } catch (IllegalArgumentException e) {
                if (e.getMessage().contains("Codec with name"))
                    throw new IndexVersionMismatch("Error opening index, Codec not available; wrong BlackLab version?", e);
                throw e;
            }
        }
    }

    protected void finishOpeningIndex(File indexDir, boolean indexMode, boolean createNewIndex)
            throws IOException, ErrorOpeningIndex {
        isEmptyIndex = indexMetadata.isNewIndex();

        // TODO: we need to create the analyzer before opening the index, because
        //   we can't change the analyzer attached to the IndexWriter (and passing a different
        //   analyzer in addDocument() went away in Lucene 5.x).
        //   For now, if we're in index mode, we re-open the index with the analyzer we determined.
        if (traceIndexOpening())
            logger.debug("  Creating analyzers...");
        createAnalyzers();

        if (indexMode) {
            // Re-open the IndexWriter with the analyzer we've created above (see comment above)
            if (traceIndexOpening())
                logger.debug("  Re-opening IndexWriter with newly created analyzers...");
            reader.close();
            indexWriter.close();
            IndexWriter luceneIndexWriter = openIndexWriter(indexDir, createNewIndex, analyzer);
            if (traceIndexOpening())
                logger.debug("  IndexReader too...");
            reader = DirectoryReader.open(luceneIndexWriter, false, false);
            indexWriter = indexObjectFactory().indexWriterProxy(luceneIndexWriter);
        }

        // Register ourselves in the mapping from IndexReader to BlackLabIndex,
        // so we can find the corresponding BlackLabIndex object from within Lucene code
        blackLab.registerIndex(reader, this);

        // Detect and open the ContentStore for the contents field
        if (!createNewIndex) {
            if (traceIndexOpening())
                logger.debug("  Checking if we have a main contents field...");
            AnnotatedField mainContentsField = indexMetadata.mainAnnotatedField();
            if (mainContentsField == null) {
                if (!indexMode) {
                    if (!isEmptyIndex)
                        throw new BlackLabRuntimeException("Main contents field unknown");
                }
            }

            // Register content stores
            if (traceIndexOpening())
                logger.debug("  Opening content stores...");
            for (AnnotatedField field: indexMetadata.annotatedFields()) {
                if (field.hasContentStore()) {
                    openContentStore(field, indexMode, false, indexDir);
                }
            }
        }

        if (traceIndexOpening())
            logger.debug("  Opening IndexSearcher...");
        indexSearcher = new IndexSearcher(reader);

        // Make sure large wildcard/regex expansions succeed
        if (traceIndexOpening())
            logger.debug("  Setting maxClauseCount...");
        BooleanQuery.setMaxClauseCount(100_000);

        // Open the forward indices
        if (!createNewIndex) {
            if (traceIndexOpening())
                logger.debug("  Opening forward indices...");
            for (AnnotatedField field: annotatedFields()) {
                for (Annotation annotation: field.annotations()) {
                    if (annotation.hasForwardIndex()) {
                        // This annotation has a forward index. Make sure it is open.
                        if (traceIndexOpening())
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
        fieldAnalyzers.put("fromInputFile", BuiltinAnalyzers.fromString("nontokenizing").getAnalyzer());
        Analyzer baseAnalyzer = BuiltinAnalyzers.fromString(indexMetadata.metadataFields().defaultAnalyzerName())
                .getAnalyzer();
        for (MetadataField field: indexMetadata.metadataFields()) {
            String analyzerName = field.analyzerName();
            if (field.type() == FieldType.UNTOKENIZED)
                analyzerName = "nontokenizing";
            if (analyzerName.length() > 0 && !analyzerName.equalsIgnoreCase("default")) {
                Analyzer fieldAnalyzer = BuiltinAnalyzers.fromString(analyzerName).getAnalyzer();
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
        synchronized(this) {
            if (closed)
                return;
            closed = true;
        }
        try {
            blackLab.removeIndex(this);
            reader.close();
            if (indexWriter != null) {
                indexWriter.commit();
                indexWriter.close();
            }
            contentStores.close();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public boolean docExists(int docId) {
        if (docId < 0 || docId >= reader.maxDoc())
            return false;
        Bits liveDocs = MultiBits.getLiveDocs(reader);
        return liveDocs == null || liveDocs.get(docId);
    }

    @Override
    public IndexReader reader() {
        return reader;
    }

    protected void openContentStore(Field field, boolean indexMode, boolean createNewContentStore, File indexDir) throws ErrorOpeningIndex {
        ContentStore cs;
        if (this instanceof BlackLabIndexIntegrated) {
            String luceneField = AnnotatedFieldNameUtil.contentStoreField(field.name());
            cs = ContentStoreIntegrated.open(reader, luceneField);
        } else {
            // Classic external index format. Open external content store.
            File dir = new File(indexDir, "cs_" + field.name());
            if (dir.exists() || createNewContentStore) {
                if (traceIndexOpening())
                    logger.debug("    " + dir + "...");
                cs = ContentStoreExternal.open(dir, indexMode, createNewContentStore);
            } else {
                throw new IllegalStateException("Field " + field.name() +
                        " should have content store, but directory " + dir + " not found!");
            }
        }
        registerContentStore(field, cs);
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

    protected IndexWriter openIndexWriter(File indexDir, boolean create, Analyzer useAnalyzer) throws IOException {
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
            useAnalyzer = BuiltinAnalyzers.DUTCH.getAnalyzer();

        IndexWriterConfig config = new IndexWriterConfig(useAnalyzer);
        config.setOpenMode(create ? OpenMode.CREATE : OpenMode.CREATE_OR_APPEND);
        config.setRAMBufferSizeMB(150); // faster indexing
        customizeIndexWriterConfig(config);
        return new IndexWriter(indexLuceneDir, config);
    }

    protected void customizeIndexWriterConfig(IndexWriterConfig config) {
        // subclasses can override
    }

    @Override
    public BLIndexWriterProxy writer() {
        return indexWriter;
    }

    @Override
    public File indexDirectory() {
        return indexLocation;
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
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((indexLocation == null) ? 0 : indexLocation.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof BlackLabIndexAbstract))
            return false;
        BlackLabIndexAbstract that = (BlackLabIndexAbstract) o;
        return indexLocation.equals(that.indexLocation);
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
    public TermFrequencyList termFrequencies(AnnotationSensitivity annotSensitivity, Query filterQuery,
            Set<String> terms) {
        Map<String, Integer> freq = LuceneUtil.termFrequencies(searcher(), filterQuery, annotSensitivity, terms);
        return new TermFrequencyList(QueryInfo.create(this, annotSensitivity.annotation().field()), freq, true);
    }

    @Override
    public SearchEmpty search(AnnotatedField field, boolean useCache) {
        return new SearchEmpty(QueryInfo.create(this, field, useCache));
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

    protected void deleteFromForwardIndices(Document d) {
        // subclasses may override
    }

    protected abstract ForwardIndex createForwardIndex(AnnotatedField field);

}
