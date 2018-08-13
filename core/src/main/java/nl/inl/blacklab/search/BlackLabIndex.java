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
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.MaxSettings;
import nl.inl.blacklab.search.textpattern.TextPattern;
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
     * @param indexDir the index directory
     * @return the searcher
     * @throw IndexTooOld if the index format is no longer supported
     * @throws ErrorOpeningIndex on any error
     */
    static BlackLabIndex open(File indexDir) throws ErrorOpeningIndex {
        return new BlackLabIndexImpl(indexDir, false, false, (File) null);
    }

    /**
     * Open an index for reading ("search mode").
     *
     * @param indexDir the index directory
     * @param settings default search settings
     * @return the searcher
     * @throw IndexTooOld if the index format is no longer supported
     * @throws ErrorOpeningIndex on any error
     */
    static BlackLabIndex open(File indexDir, MaxSettings settings) throws ErrorOpeningIndex {
        return new BlackLabIndexImpl(indexDir, false, false, (File) null, settings);
    }

    ContextSize DEFAULT_CONTEXT_SIZE = ContextSize.get(5);

    // Basic stuff, low-level access to index
    //---------------------------------------------------------------
    
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
    
    BLSpanQuery createSpanQuery(TextPattern pattern, AnnotatedField field, Query filter) throws RegexpTooLarge;

    /**
     * Find hits for a pattern in a field.
     *
     * @param query the pattern to find
     * @param settings search settings, or null for default
     * @return the hits found
     * @throws WildcardTermTooBroad if a wildcard or regular expression term
     *             is overly broad
     */
    Hits find(BLSpanQuery query, MaxSettings settings) throws WildcardTermTooBroad;

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
     */
    Hits find(TextPattern pattern, AnnotatedField field, Query filter, MaxSettings settings)
            throws WildcardTermTooBroad, RegexpTooLarge;

    /**
     * Perform a document query only (no hits)
     * 
     * @param documentFilterQuery the document-level query
     * @return the matching documents
     */
    DocResults queryDocuments(Query documentFilterQuery);

    /**
     * Explain how a TextPattern is converted to a SpanQuery and rewritten to an
     * optimized version to be executed by Lucene.
     *
     * @param pattern the pattern to explain
     * @param field which field to find the pattern in
     * @return the explanation
     * @throws WildcardTermTooBroad if a wildcard or regular expression term
     *             is overly broad
     * @throws RegexpTooLarge 
     */
    default QueryExplanation explain(TextPattern pattern, AnnotatedField field) throws WildcardTermTooBroad, RegexpTooLarge {
        return explain(createSpanQuery(pattern, field, null));
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
    QueryExplanation explain(BLSpanQuery query) throws WildcardTermTooBroad;

    
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
    AnnotationForwardIndex forwardIndex(Annotation annotation);

    
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

    default AnnotatedField annotatedField(String fieldName) {
        return metadata().annotatedField(fieldName);
    }

    default AnnotatedField mainAnnotatedField() {
        return metadata().mainAnnotatedField();
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
    MaxSettings maxSettings();

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

    void setDefaultMatchSensitivity(MatchSensitivity m);

    void setMaxSettings(MaxSettings settings);
    
    @Override
    boolean equals(Object obj);
    
    @Override
    int hashCode();

    void setDefaultContextSize(ContextSize size);

    ContextSize defaultContextSize();

    boolean indexMode();

}
