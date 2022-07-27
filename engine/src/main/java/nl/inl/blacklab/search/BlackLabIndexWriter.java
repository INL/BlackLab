package nl.inl.blacklab.search;

import java.io.File;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;

public interface BlackLabIndexWriter extends BlackLabIndex {

    /**
     * Create or open an index.
     *
     * @param directory index directory
     * @param create force creating a new index even if one already exists?
     * @param formatIdentifier default document format to use
     * @param indexTemplateFile optional file to use as template for index (legacy)
     * @return the index writer
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    static BlackLabIndexWriter open(File directory, boolean create, String formatIdentifier,
            File indexTemplateFile) throws ErrorOpeningIndex {
        BlackLabIndexWriter indexWriter;
        if (create) {
            if (indexTemplateFile == null) {
                // Create index from format configuration (modern)
                // (or a legacy DocIndexer, but no index template file, so the defaults will be used)
                // No indexTemplateFile, but maybe the formatIdentifier is backed by a ConfigInputFormat (instead of
                // some other DocIndexer implementation)
                // this ConfigInputFormat could then still be used as a minimal template to setup the index
                // (if there's no ConfigInputFormat, that's okay too, a default index template will be used instead)
                ConfigInputFormat format = DocumentFormats.getConfigInputFormat(formatIdentifier);

                // template might still be null, in that case a default will be created
                indexWriter = BlackLab.openForWriting(directory, true, format);
            } else {
                // Create index from index template file (legacy)
                indexWriter = BlackLab.openForWriting(directory, true, indexTemplateFile);
            }
        } else {
            // opening an existing index
            indexWriter = BlackLab.openForWriting(directory, false);
        }
        return indexWriter;
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

    IndexWriter writer();

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

}
