package nl.inl.blacklab.search;

import java.io.IOException;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.index.BLIndexObjectFactory;
import nl.inl.blacklab.index.BLIndexWriterProxy;
import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;

public interface BlackLabIndexWriter extends BlackLabIndex {

    /**
     * Return factory object for creating input documents, getting field types, etc.
     *
     * This exists to support indexing both directly to Lucene and inside Solr.
     *
     * @return index object factory
     */
    BLIndexObjectFactory indexObjectFactory();

    static void setMetadataDocumentFormatIfMissing(BlackLabIndexWriter indexWriter, String formatIdentifier) {
        String defaultFormatIdentifier = indexWriter.metadata().documentFormat();
        if (defaultFormatIdentifier == null || defaultFormatIdentifier.isEmpty()) {
            // indexTemplateFile didn't provide a default formatIdentifier,
            // overwrite it with our provided formatIdentifier
            indexWriter.metadata().setDocumentFormat(formatIdentifier);
        }
    }

    /**
     * Call this to roll back any changes made to the index this session. Calling
     * close() will automatically commit any changes. If you call this method, then
     * call close(), no changes will be committed.
     */
    void rollback();

    /**
     * Get information about the structure of the BlackLab index.
     *
     * @return the structure object
     */
    @Override
    IndexMetadataWriter metadata();

    BLIndexWriterProxy writer();

    /**
     * Deletes documents matching a query from the BlackLab index.
     *
     * This deletes the documents from the Lucene index, the forward indices and the
     * content store(s).
     * 
     * @param q the query
     */
    void delete(Query q);

    /**
     * Is the indexer still open?
     * 
     * It can be closed unexpectedly if e.g. the GC overhead limit is exceeded.
     * If that happened, we should stop indexing. 
     * 
     * @return true if the indexer was closed, false if not
     */
    boolean isOpen();

    /**
     * Add a document to the index.
     *
     * @param document document to add
     */
    default void addDocument(BLInputDocument document) throws IOException {
        // If we're using the integrated index format, we must make sure
        // the metadata is frozen as soon as we start adding documents.
        metadata().freezeBeforeIndexing();

        writer().addDocument(document);
    }

    /**
     * Update a document in the index.
     *
     * @param term term query to find the previous version for deletion
     * @param document new version of the document
     */
    default void updateDocument(Term term, BLInputDocument document) throws IOException {
        // If we're using the integrated index format, we must make sure
        // the metadata is frozen as soon as we start adding documents.
        metadata().freezeBeforeIndexing();

        writer().updateDocument(term, document);
    }

    /**
     * Should TokenStream payloads contain information about primary/secondary token values?
     *
     * These are indicators used to decide which value is the primary value that should be
     * stored in the forward index so it can be used for concordances, sort, grouping, etc.
     *
     * Secondary values are not stored in the forward index. This might be synonyms or stemmed
     * values.
     *
     * The indicator in the payload (if one was added, which we try to avoid if possible) should be
     * skipped when using payloads.
     *
     * Used by the integrated index format.
     *
     * @return whether or not TokenStream payloads should include primary value indicators
     */
    boolean needsPrimaryValuePayloads();
}
