package nl.inl.blacklab.search;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.Collator;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.HitsSettings;
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
            throw new BlackLabException(e);
        }
    }

    /**
     * Open an index for reading ("search mode").
     *
     * @param indexDir the index directory
     * @return the searcher
     * @throws CorruptIndexException
     * @throws IOException
     */
    static BlackLabIndex open(File indexDir) throws CorruptIndexException, IOException {
        return new BlackLabIndexImpl(indexDir, false, false, (File) null);
    }

    /**
     * Open an index for reading ("search mode").
     *
     * @param indexDir the index directory
     * @param settings default search settings
     * @return the searcher
     * @throws CorruptIndexException
     * @throws IOException
     */
    static BlackLabIndex open(File indexDir, HitsSettings settings) throws CorruptIndexException, IOException {
        return new BlackLabIndexImpl(indexDir, false, false, (File) null, settings);
    }

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
     * Checks if a document has been deleted from the index
     * 
     * NOTE: shouldn't normally be necessary, because e.g. docs returned
     * in search results are never deleted
     * 
     * @param doc the document id
     * @return true iff it has been deleted
     */
    @Deprecated
    boolean isDeleted(int doc);

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

    /**
     * Returns one more than the highest document id
     * 
     * NOTE: some document id's may have been deleted, so this is not the number
     * of documents.
     * 
     * @return one more than the highest document id
     */
    @Deprecated
    int maxDoc();


    // Search the index
    //---------------------------------------------------------------------------
    
    BLSpanQuery createSpanQuery(TextPattern pattern, AnnotatedField field, Query filter);

    /**
     * Find hits for a pattern in a field.
     *
     * @param query the pattern to find
     * @param settings search settings, or null for default
     * @return the hits found
     * @throws BooleanQuery.TooManyClauses if a wildcard or regular expression term
     *             is overly broad
     */
    Hits find(BLSpanQuery query, HitsSettings settings) throws BooleanQuery.TooManyClauses;

    /**
     * Find hits for a pattern in a field.
     *
     * @param pattern the pattern to find
     * @param field field to find pattern in
     * @param filter determines which documents to search
     * @param settings search settings, or null for default
     *
     * @return the hits found
     * @throws BooleanQuery.TooManyClauses if a wildcard or regular expression term
     *             is overly broad
     */
    Hits find(TextPattern pattern, AnnotatedField field, Query filter, HitsSettings settings)
            throws BooleanQuery.TooManyClauses;

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
     * @throws BooleanQuery.TooManyClauses if a wildcard or regular expression term
     *             is overly broad
     */
    default QueryExplanation explain(TextPattern pattern, AnnotatedField field) throws BooleanQuery.TooManyClauses {
        return explain(createSpanQuery(pattern, field, null));
    }

    /**
     * Explain how a SpanQuery is rewritten to an optimized version to be executed
     * by Lucene.
     *
     * @param query the query to explain
     * @return the explanation
     * @throws BooleanQuery.TooManyClauses if a wildcard or regular expression term
     *             is overly broad
     */
    QueryExplanation explain(BLSpanQuery query) throws BooleanQuery.TooManyClauses;

    
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
     * @throws BlackLabException if the annotation has no forward index
     */
    ForwardIndex forwardIndex(Annotation annotation);

    
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
            field = metadata().metadataFields().get(fieldName);
        return field;
    }

    default AnnotatedField annotatedField(String fieldName) {
        return metadata().annotatedFields().get(fieldName);
    }

    default AnnotatedField mainAnnotatedField() {
        return metadata().annotatedFields().main();
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
    HitsSettings hitsSettings();

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

    void setHitsSettings(HitsSettings withContextSize);

}
