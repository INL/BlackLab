package nl.inl.blacklab.search;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.text.Collator;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.requestlogging.SearchLogger;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFields;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.searches.SearchEmpty;
import nl.inl.util.VersionFile;
import nl.inl.util.XmlHighlighter.UnbalancedTagsStrategy;

public interface BlackLabIndex extends Closeable {

    // Static [factory] methods
    //---------------------------------------------------------------
    
    /**
     * Does the specified directory contain a BlackLab index?
     * 
     * @param indexDir the directory
     * @return true if it's a BlackLab index, false if not.
     */
    static boolean isIndex(File indexDir) {
        try {
            if (VersionFile.exists(indexDir)) {
                VersionFile vf = VersionFile.read(indexDir);
                String version = vf.getVersion();
                if (vf.getType().equals("blacklab") && (version.equals("1") || version.equals("2")))
                    return true;
            }
            return false;
        } catch (FileNotFoundException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Open an index for reading ("search mode").
     * 
     * @param blackLab our BlackLab instance
     * @param indexDir the index directory
     * @param settings default search settings
     * @return index object
     * @throw IndexTooOld if the index format is no longer supported
     * @throws ErrorOpeningIndex on any error
     */
    static BlackLabIndex open(BlackLab blackLab, File indexDir) throws ErrorOpeningIndex {
        return new BlackLabIndexImpl(blackLab, indexDir, false, false, (File) null);
    }

    /**
     * Open an index for reading ("search mode").
     *
     * @param indexDir the index directory
     * @return index object
     * @throw IndexTooOld if the index format is no longer supported
     * @throws ErrorOpeningIndex on any error
     * @deprecated use static BlackLab.openIndex() or instantiate BlackLab and call open()
     */
    @Deprecated
    static BlackLabIndex open(File indexDir) throws ErrorOpeningIndex {
        return BlackLab.openIndex(indexDir);
    }

    ContextSize DEFAULT_CONTEXT_SIZE = ContextSize.get(5);

    // Basic stuff, low-level access to index
    //---------------------------------------------------------------
    
    @Override
    boolean equals(Object obj);
    
    @Override
    int hashCode();
    
    /**
     * Finalize the Searcher object. This closes the IndexSearcher and (depending on
     * the constructor used) may also close the index reader.
     */
    @Override
    void close();

    /**
     * Is this a newly created, empty index?
     * 
     * @return true if it is, false if not
     */
    boolean isEmpty();

    /**
     * Is the document id in range, and not a deleted document?
     * @param docId document id to check
     * @return true if it is an existing document
     */
    boolean docExists(int docId);

    /**
     * Get a BlackLab document.
     * 
     * @param docId document id
     * @return document
     */
    Doc doc(int docId);

    /**
     * Perform a task on each (non-deleted) Lucene Document.
     * 
     * @param task task to perform
     */
    void forEachDocument(DocTask task);


    // Search the index
    //---------------------------------------------------------------------------
    
    BLSpanQuery createSpanQuery(QueryInfo queryInfo, TextPattern pattern, Query filter) throws RegexpTooLarge;

    /**
     * Find hits for a pattern in a field.
     *
     * @param query the pattern to find
     * @param settings search settings, or null for default
     * @return the hits found
     * @throws WildcardTermTooBroad if a wildcard or regular expression term
     *             is overly broad
     */
    default Hits find(BLSpanQuery query, SearchSettings settings) throws WildcardTermTooBroad {
        return find(query, settings, null);
    }

    /**
     * Find hits for a pattern in a field.
     * 
     * @param query the pattern to find
     * @param settings search settings, or null for default
     * @param searchLogger where to log details about query execution
     * @return the hits found
     * @throws WildcardTermTooBroad if a wildcard or regular expression term
     *             is overly broad
     */
    Hits find(BLSpanQuery query, SearchSettings settings, SearchLogger searchLogger) throws WildcardTermTooBroad;

    /**
     * Find hits for a pattern in a field.
     * 
     * @param queryInfo information about the query: field, logger
     * @param pattern the pattern to find
     * @param filter determines which documents to search
     * @param settings search settings, or null for default
     *
     * @return the hits found
     * @throws WildcardTermTooBroad if a wildcard or regular expression term
     *             is overly broad
     * @throws RegexpTooLarge 
     */
    default Hits find(QueryInfo queryInfo, TextPattern pattern, Query filter, SearchSettings settings) throws WildcardTermTooBroad, RegexpTooLarge {
        BLSpanQuery spanQuery = pattern.translate(defaultExecutionContext(queryInfo.field()));
        if (filter != null)
            spanQuery = new SpanQueryFiltered(spanQuery, filter);
        return find(spanQuery, settings, queryInfo.searchLogger());
    }

    /**
     * Find hits for a pattern in a field.
     *
     * @param pattern the pattern to find
     * @param field field to find pattern in
     * @param filter determines which documents to search
     * @param settings search settings, or null for default
     *
     * @return the hits found
     * @throws WildcardTermTooBroad if a wildcard or regular expression term
     *             is overly broad
     * @throws RegexpTooLarge 
     * @deprecated use version that takes a QueryInfo
     */
    @Deprecated
    default Hits find(TextPattern pattern, AnnotatedField field, Query filter) throws WildcardTermTooBroad, RegexpTooLarge {
        return find(QueryInfo.create(this, field), pattern, filter, searchSettings());
    }

    /**
     * Find hits for a pattern in a field.
     *
     * @param pattern the pattern to find
     * @param field field to find pattern in
     * @param filter determines which documents to search
     * @param settings search settings, or null for default
     *
     * @return the hits found
     * @throws WildcardTermTooBroad if a wildcard or regular expression term
     *             is overly broad
     * @throws RegexpTooLarge
     * @deprecated use version that takes a QueryInfo
     */
    @Deprecated
    default Hits find(TextPattern pattern, Query filter) throws WildcardTermTooBroad, RegexpTooLarge {
        return find(QueryInfo.create(this), pattern, filter, searchSettings());
    }

    /**
     * Perform a document query only (no hits)
     * 
     * @param documentFilterQuery the document-level query
     * @return the matching documents
     */
    default DocResults queryDocuments(Query documentFilterQuery) {
        return queryDocuments(documentFilterQuery, (SearchLogger)null);
    }

    /**
     * Perform a document query only (no hits)
     * 
     * @param documentFilterQuery the document-level query
     * @param searchLogger where to log details about query execution
     * @return the matching documents
     */
    DocResults queryDocuments(Query documentFilterQuery, SearchLogger searchLogger);

    /**
     * Determine the term frequencies for an annotation sensitivity.
     * 
     * @param annotSensitivity the annation + sensitivity indexing we want the term frequency for
     * @param filterQuery document filter, or null for all documents
     * @return term frequencies
     */
    TermFrequencyList termFrequencies(AnnotationSensitivity annotSensitivity, Query filterQuery);

    /**
     * Explain how a TextPattern is converted to a SpanQuery and rewritten to an
     * optimized version to be executed by Lucene.
     * 
     * @param queryInfo query info, such as the field to search 
     * @param pattern the pattern to explain
     * @param filter filter query, or null for none
     * @param field which field to find the pattern in
     * @return the explanation
     * @throws WildcardTermTooBroad if a wildcard or regular expression term
     *             is overly broad
     * @throws RegexpTooLarge 
     */
    default QueryExplanation explain(QueryInfo queryInfo, TextPattern pattern, Query filter) throws WildcardTermTooBroad, RegexpTooLarge {
        return explain(createSpanQuery(queryInfo.withIndex(this), pattern, filter));
    }

    /**
     * Explain how a SpanQuery is rewritten to an optimized version to be executed
     * by Lucene.
     *
     * @param query the query to explain
     * @return the explanation
     * @throws WildcardTermTooBroad if a wildcard or regular expression term
     *             is overly broad
     */
    default QueryExplanation explain(BLSpanQuery query) throws WildcardTermTooBroad {
        return explain(query, null);
    }


    /**
     * Explain how a SpanQuery is rewritten to an optimized version to be executed
     * by Lucene.
     *
     * @param query the query to explain
     * @param searchLogger where to log details about query optimization
     * @return the explanation
     * @throws WildcardTermTooBroad if a wildcard or regular expression term
     *             is overly broad
     */
    QueryExplanation explain(BLSpanQuery query, SearchLogger searchLogger) throws WildcardTermTooBroad;
    
    /**
     * Start building a Search. 
     * 
     * @param field field to search
     * @param useCache whether to use the cache or bypass it
     * @param searchLogger where to log details about how the search was executed, or null to skip this logging
     * @return empty search object
     */
    SearchEmpty search(AnnotatedField field, boolean useCache, SearchLogger searchLogger);

    /**
     * Start building a Search. 
     * 
     * @param field field to search
     * @param useCache whether to use the cache or bypass it
     * @return empty search object
     */
    default SearchEmpty search(AnnotatedField field, boolean useCache) {
        return search(field, useCache, null);
    }

    /**
     * Start building a Search. 
     * 
     * @param field field to search
     * @return empty search object
     */
    default SearchEmpty search(AnnotatedField field) {
        return search(field, true);
    }
    
    /**
     * Start building a Search. 
     * 
     * @param field field to search
     * @return empty search object
     */
    default SearchEmpty search() {
        return search(mainAnnotatedField());
    }

    
    // Access the different modules of the index
    //---------------------------------------------------------------------------
    
    /**
     * Get the Lucene index reader we're using.
     *
     * @return the Lucene index reader
     */
    IndexReader reader();

    IndexSearcher searcher();

    /**
     * Get the content accessor for a field.
     * 
     * @param field the field
     * @return the content accessor, or null if there is no content accessor for this field
     */
    ContentAccessor contentAccessor(Field field);

    /**
     * Tries to get the ForwardIndex object for the specified fieldname.
     *
     * Looks for an already-opened forward index first. If none is found, and if
     * we're in "create index" mode, may create a new forward index. Otherwise,
     * looks for an existing forward index and opens that.
     *
     * @param annotation the annotation for which we want the forward index
     * @return the ForwardIndex if found/created
     * @throws BlackLabRuntimeException if the annotation has no forward index
     */
    AnnotationForwardIndex annotationForwardIndex(Annotation annotation);

    /**
     * Get forward index for the specified annotated field.
     * 
     * @param field field to get forward index for
     * @return forward index
     */
    ForwardIndex forwardIndex(AnnotatedField field);


    
    // Information about the index
    //---------------------------------------------------------------------------
    
    /**
     * Get the index name.
     * 
     * Usually the name of the directory the index is in.
     * 
     * @return index name
     */
    String name();

    /**
     * Get the index directory.
     * 
     * @return index directory
     */
    File indexDirectory();

    /**
     * Get information about the structure of the BlackLab index.
     *
     * @return the structure object
     */
    IndexMetadata metadata();

    /**
     * Get a field (either an annotated or a metadata field).
     * 
     * @param fieldName name of the field
     * @return the field
     */
    default Field field(String fieldName) {
        Field field = annotatedField(fieldName);
        if (field == null)
            field = metadataField(fieldName);
        return field;
    }

    default AnnotatedFields annotatedFields() {
        return metadata().annotatedFields();
    }

    default AnnotatedField annotatedField(String fieldName) {
        return metadata().annotatedField(fieldName);
    }

    default AnnotatedField mainAnnotatedField() {
        return metadata().mainAnnotatedField();
    }
    
    default MetadataFields metadataFields() {
        return metadata().metadataFields();
    }
    
    default MetadataField metadataField(String fieldName) {
        return metadata().metadataField(fieldName);
    }

    
    // Get settings
    //---------------------------------------------------------------------------

    /**
     * The default settings for all new Hits objects.
     *
     * You may change these settings; this will affect all new Hits objects.
     *
     * @return settings object
     */
    SearchSettings searchSettings();

    /**
     * How do we fix well-formedness for snippets of XML?
     * 
     * @return the setting: either adding or removing unbalanced tags
     */
    UnbalancedTagsStrategy defaultUnbalancedTagsStrategy();

    MatchSensitivity defaultMatchSensitivity();

    /**
     * Get the default initial query execution context.
     *
     * @param field field to search
     * @return query execution context
     */
    QueryExecutionContext defaultExecutionContext(AnnotatedField field);

    /**
     * Get the collator being used for sorting.
     *
     * @return the collator
     */
    Collator collator();

    /**
     * Get the analyzer for indexing and searching.
     * 
     * @return the analyzer
     */
    Analyzer analyzer();
    
    /**
     * Get the default context size.
     * @return default context size
     */
    ContextSize defaultContextSize();

    /**
     * Are we running in index mode?
     * @return true if we are, false if not
     */
    boolean indexMode();


    // Methods that mutate settings
    //---------------------------------------------------------------------------

    /**
     * Set how to fix well-formedness for snippets of XML.
     * 
     * @param strategy the setting: either adding or removing unbalanced tags
     */
    void setDefaultUnbalancedTagsStrategy(UnbalancedTagsStrategy strategy);

    /**
     * Set the collator used for sorting.
     *
     * The default collator is for English.
     *
     * @param collator the collator
     */
    void setCollator(Collator collator);

    /**
     * Set the default sensitivity for queries.
     * @param m default match sensitivity
     */
    void setDefaultMatchSensitivity(MatchSensitivity m);

    /**
     * Set the maximum number of hits to process/count.
     * @param settings desired settings
     */
    void setSearchSettings(SearchSettings settings);
    
    /**
     * Set the default context size.
     * @param size default context size
     */
    void setDefaultContextSize(ContextSize size);

    /**
     * Set the object BlackLab should use as cache.
     * 
     * BlackLab will notify the cache of search results and will ask
     * the cache for results before executing a search.
     * 
     * It is up to the application to implement an effective cache, deciding
     * whether to cache a result and ensuring the cache doesn't grow too large.
     * 
     * @param cache cache object to use
     */
    void setCache(SearchCache cache);

    SearchCache cache();

    /**
     * Get the BlackLab instance that created us.
     * @return BlackLab instance
     */
    BlackLab blackLab();

}
