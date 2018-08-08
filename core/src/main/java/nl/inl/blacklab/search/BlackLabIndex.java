package nl.inl.blacklab.search;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.Collator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
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
import nl.inl.util.XmlHighlighter;
import nl.inl.util.XmlHighlighter.UnbalancedTagsStrategy;

public interface BlackLabIndex extends Closeable {

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
     * Get the collator being used for sorting.
     *
     * @return the collator
     */
    Collator collator();

    /**
     * Is this a newly created, empty index?
     * 
     * @return true if it is, false if not
     */
    boolean isEmpty();

    /**
     * Finalize the Searcher object. This closes the IndexSearcher and (depending on
     * the constructor used) may also close the index reader.
     */
    @Override
    void close();

    /**
     * Get information about the structure of the BlackLab index.
     *
     * @return the structure object
     */
    IndexMetadata metadata();

    /**
     * Retrieve a Lucene Document object from the index.
     *
     * NOTE: you must check if the document isn't deleted using Search.isDeleted()
     * first! Lucene 4.0+ allows you to retrieve deleted documents, making you
     * responsible for checking whether documents are deleted or not. (This doesn't
     * apply to search results; searches should never produce deleted documents. It
     * does apply when you're e.g. iterating over all documents in the index)
     *
     * @param doc the document id
     * @return the Lucene Document
     * @throws RuntimeException if the document doesn't exist (use maxDoc() and
     *             isDeleted() to check first!)
     */
    Document document(int doc);

    /**
     * Get a set of all (non-deleted) Lucene document ids.
     * 
     * @return set of ids
     */
    Set<Integer> docIdSet();

    /**
     * Perform a task on each (non-deleted) Lucene Document.
     * 
     * @param task task to perform
     */
    void forEachDocument(LuceneDocTask task);

    /**
     * Checks if a document has been deleted from the index
     * 
     * @param doc the document id
     * @return true iff it has been deleted
     */
    boolean isDeleted(int doc);

    /**
     * Returns one more than the highest document id
     * 
     * @return one more than the highest document id
     */
    int maxDoc();

    BLSpanQuery createSpanQuery(TextPattern pattern, AnnotatedField field, Query filter);

    /**
     * Find hits for a pattern in a field.
     *
     * @param query the pattern to find
     * @return the hits found
     * @throws BooleanQuery.TooManyClauses if a wildcard or regular expression term
     *             is overly broad
     */
    Hits find(BLSpanQuery query) throws BooleanQuery.TooManyClauses;

    /**
     * Find hits for a pattern in a field.
     *
     * @param pattern the pattern to find
     * @param field field to find pattern in
     * @param filter determines which documents to search
     *
     * @return the hits found
     * @throws BooleanQuery.TooManyClauses if a wildcard or regular expression term
     *             is overly broad
     */
    Hits find(TextPattern pattern, AnnotatedField field, Query filter)
            throws BooleanQuery.TooManyClauses;

    /**
     * Find hits for a pattern in a field.
     *
     * @param pattern the pattern to find
     * @param filter determines which documents to search
     * 
     * Uses the main annotated field.
     *
     * @return the hits found
     * @throws BooleanQuery.TooManyClauses if a wildcard or regular expression term
     *             is overly broad
     */
    default Hits find(TextPattern pattern, Query filter) {
        return find(pattern, mainAnnotatedField(), filter);
    }

    /**
     * Find hits for a pattern in a field.
     *
     * @param pattern the pattern to find
     * @param field which field to find the pattern in
     *
     * @return the hits found
     * @throws BooleanQuery.TooManyClauses if a wildcard or regular expression term
     *             is overly broad
     */
    default Hits find(TextPattern pattern, AnnotatedField field) throws BooleanQuery.TooManyClauses {
        return find(pattern, field, null);
    }

    /**
     * Find hits for a pattern.
     *
     * @param pattern the pattern to find
     *
     * @return the hits found
     * @throws BooleanQuery.TooManyClauses if a wildcard or regular expression term
     *             is overly broad
     */
    default Hits find(TextPattern pattern) throws BooleanQuery.TooManyClauses {
        return find(pattern, mainAnnotatedField(), null);
    }

    /**
     * Explain how a TextPattern is converted to a SpanQuery and rewritten to an
     * optimized version to be executed by Lucene.
     * 
     * Uses the main annotation field.
     *
     * @param pattern the pattern to explain
     * @return the explanation
     * @throws BooleanQuery.TooManyClauses if a wildcard or regular expression term
     *             is overly broad
     */
    default QueryExplanation explain(TextPattern pattern) throws BooleanQuery.TooManyClauses {
        return explain(pattern, mainAnnotatedField());
    }

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

    /**
     * Get character positions from word positions.
     *
     * Places character positions in the same arrays as the word positions were
     * specified in.
     *
     * @param doc the document from which to find character positions
     * @param field the field from which to find character positions
     * @param startsOfWords word positions for which we want starting character
     *            positions (i.e. the position of the first letter of that word)
     * @param endsOfWords word positions for which we want ending character
     *            positions (i.e. the position of the last letter of that word)
     * @param fillInDefaultsIfNotFound if true, if any illegal word positions are
     *            specified (say, past the end of the document), a sane default
     *            value is chosen (in this case, the last character of the last word
     *            found). Otherwise, throws an exception.
     */
    void getCharacterOffsets(int doc, Field field, int[] startsOfWords, int[] endsOfWords,
            boolean fillInDefaultsIfNotFound);

    /**
     * Get part of the contents of a field from a Lucene Document.
     *
     * This takes into account that some fields are stored externally in content
     * stores instead of in the Lucene index.
     *
     * @param docId the Lucene Document id
     * @param field the field
     * @param startAtChar where to start getting the content (-1 for start of
     *            document, 0 for first char)
     * @param endAtChar where to end getting the content (-1 for end of document)
     * @return the field content
     */
    String getContentByCharPos(int docId, Field field, int startAtChar, int endAtChar);

    /**
     * Get part of the contents of a field from a Lucene Document.
     *
     * This takes into account that some fields are stored externally in content
     * stores instead of in the Lucene index.
     *
     * @param docId the Lucene Document id
     * @param field the field
     * @param startAtWord where to start getting the content (first word returned;
     *            -1 for start of document, 0 for first word)
     * @param endAtWord where to end getting the content (last word returned; -1 for
     *            end of document)
     * @return the field content
     */
    String getContent(int docId, Field field, int startAtWord, int endAtWord);

    /**
     * Get the contents of a field from a Lucene Document.
     *
     * This takes into account that some fields are stored externally in content
     * stores instead of in the Lucene index.
     *
     * @param d the Document
     * @param field the field
     * @return the field content
     */
    String getContent(Document d, Field field);

    /**
     * Get the document contents (original XML).
     *
     * @param d the Document
     * @return the field content
     */
    default String getContent(Document d) {
        return getContent(d, mainAnnotatedField());
    }

    /**
     * Get the contents of a field from a Lucene Document.
     *
     * This takes into account that some fields are stored externally in content
     * stores instead of in the Lucene index.
     *
     * @param docId the Document id
     * @param field the field
     * @return the field content
     */
    default String getContent(int docId, Field field) {
        return getContent(docId, field, -1, -1);
    }

    /**
     * Get the document contents (original XML).
     *
     * @param docId the Document id
     * @return the field content
     */
    default String getContent(int docId) {
        return getContent(docId, mainAnnotatedField(), -1, -1);
    }

    /**
     * Get the Lucene index reader we're using.
     *
     * @return the Lucene index reader
     */
    IndexReader reader();

    /**
     * Highlight part of field content with the specified hits, and make sure it's
     * well-formed.
     *
     * Uses &lt;hl&gt;&lt;/hl&gt; tags to highlight the content.
     *
     * @param docId document to highlight a field from
     * @param field field to highlight
     * @param hits the hits
     * @param startAtWord where to start highlighting (first word returned), or -1
     *            for start of document
     * @param endAtWord where to end highlighting (first word not returned), or -1
     *            for end of document
     * @return the highlighted content
     */
    String highlightContent(int docId, Field field, Hits hits, int startAtWord, int endAtWord);

    /**
     * Highlight field content with the specified hits.
     *
     * Uses &lt;hl&gt;&lt;/hl&gt; tags to highlight the content.
     *
     * @param docId document to highlight a field from
     * @param field field to highlight
     * @param hits the hits
     * @return the highlighted content
     */
    default String highlightContent(int docId, Field field, Hits hits) {
        return highlightContent(docId, field, hits, -1, -1);
    }

    /**
     * Highlight field content with the specified hits.
     *
     * Uses &lt;hl&gt;&lt;/hl&gt; tags to highlight the content.
     *
     * @param docId document to highlight a field from
     * @param hits the hits
     * @return the highlighted content
     */
    default String highlightContent(int docId, Hits hits) {
        return highlightContent(docId, mainAnnotatedField(), hits, -1, -1);
    }

    /**
     * Get the content store for a field.
     *
     * @param field the field
     * @return the content store, or null if there is no content store for this
     *         field
     */
    ContentStore contentStore(Field field);

    /**
     * Tries to get the ForwardIndex object for the specified fieldname.
     *
     * Looks for an already-opened forward index first. If none is found, and if
     * we're in "create index" mode, may create a new forward index. Otherwise,
     * looks for an existing forward index and opens that.
     *
     * @param annotation the annotation for which we want the forward index
     * @return the ForwardIndex if found/created, or null otherwise
     */
    ForwardIndex forwardIndex(Annotation annotation);

    /**
     * Determine the concordance strings for a number of concordances, given the
     * relevant character positions.
     *
     * Every concordance requires four character positions: concordance start and
     * end, and hit start and end. Visualising it ('fox' is the hit word):
     *
     * [A] the quick brown [B] fox [C] jumps over the [D]
     *
     * The startsOfWords array contains the [A] and [B] positions for each
     * concordance. The endsOfWords array contains the [C] and [D] positions for
     * each concordance.
     *
     * @param doc the Lucene document number
     * @param field the field
     * @param startsOfWords the array of starts of words ([A] and [B] positions)
     * @param endsOfWords the array of ends of words ([C] and [D] positions)
     * @param hl
     * @return the list of concordances
     */
    List<Concordance> makeConcordancesFromContentStore(int doc, Field field, int[] startsOfWords,
            int[] endsOfWords, XmlHighlighter hl);

    /**
     * Get the Terms object for the specified field.
     *
     * The Terms object is part of the ForwardIndex module and provides a mapping
     * from term id to term String, and between term id and term sort position. It
     * is used while sorting and grouping hits (by mapping the context term ids to
     * term sort position ids), and later used to display the group name (by mapping
     * the sort position ids back to Strings)
     *
     * @param annotation the annotation for which we want the Terms object
     * @return the Terms object
     * @throws RuntimeException if this field does not have a forward index, and
     *             hence no Terms object.
     */
    default Terms terms(Annotation annotation) {
        ForwardIndex forwardIndex = forwardIndex(annotation);
        if (forwardIndex == null) {
            throw new IllegalArgumentException("Annotation " + annotation + " has no forward index!");
        }
        return forwardIndex.getTerms();
    }

    /**
     * Get the Terms object for the main contents field.
     *
     * The Terms object is part of the ForwardIndex module and provides a mapping
     * from term id to term String, and between term id and term sort position. It
     * is used while sorting and grouping hits (by mapping the context term ids to
     * term sort position ids), and later used to display the group name (by mapping
     * the sort position ids back to Strings)
     *
     * @return the Terms object
     * @throws RuntimeException if this field does not have a forward index, and
     *             hence no Terms object.
     */
    default Terms terms() {
        return forwardIndex(mainAnnotatedField().annotations().main()).getTerms();
    }

    MatchSensitivity defaultMatchSensitivity();

    void setDefaultMatchSensitivity(MatchSensitivity m);

    /**
     * Get the default initial query execution context.
     *
     * @param field field to search
     * @return query execution context
     */
    QueryExecutionContext defaultExecutionContext(AnnotatedField field);

    /**
     * Get the default initial query execution context.
     *
     * Uses the default contents field.
     *
     * @return the query execution context
     */
    default QueryExecutionContext defaultExecutionContext() {
        return defaultExecutionContext(mainAnnotatedField());
    }

    String name();

    File indexDirectory();

    /**
     * Get the analyzer for indexing and searching.
     * 
     * @return the analyzer
     */
    Analyzer analyzer();

    /**
     * Perform a document query only (no hits)
     * 
     * @param documentFilterQuery the document-level query
     * @return the matching documents
     */
    DocResults queryDocuments(Query documentFilterQuery);

    IndexSearcher searcher();

    Map<Annotation, ForwardIndex> forwardIndices();

    boolean canDoNfaMatching();

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
    
}
