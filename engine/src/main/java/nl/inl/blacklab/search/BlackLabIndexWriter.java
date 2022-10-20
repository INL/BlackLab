package nl.inl.blacklab.search;

import java.io.IOException;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.index.BLDocumentFactory;
import nl.inl.blacklab.index.BLIndexWriterProxy;
import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;

public interface BlackLabIndexWriter extends BlackLabIndex {

    BLDocumentFactory documentFactory();

    static void setMetadataDocumentFormatIfMissing(BlackLabIndexWriter indexWriter, String formatIdentifier) {
        String defaultFormatIdentifier = indexWriter.metadata().documentFormat();
        if (defaultFormatIdentifier == null || defaultFormatIdentifier.isEmpty()) {
            // indexTemplateFile didn't provide a default formatIdentifier,
            // overwrite it with our provided formatIdentifier
            indexWriter.metadata().setDocumentFormat(formatIdentifier);
            indexWriter.metadata().save();
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

}
