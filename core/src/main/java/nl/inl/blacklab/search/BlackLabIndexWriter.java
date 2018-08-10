package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.LockObtainFailedException;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.indexers.config.TextDirection;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;

public interface BlackLabIndexWriter extends BlackLabIndex {

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * Note that in index mode, searching operations may not take the latest changes
     * into account. It is wisest to only use index mode for indexing, then close
     * the Searcher and create a regular one for searching.
     *
     * @param indexDir the index directory
     * @param createNewIndex if true, create a new index even if one existed there
     * @return the searcher in index mode
     * @throws ErrorOpeningIndex if the index could not be opened
     */
    static BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex) throws ErrorOpeningIndex {
        return new BlackLabIndexImpl(indexDir, true, createNewIndex, (File) null);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * Note that in index mode, searching operations may not take the latest changes
     * into account. It is wisest to only use index mode for indexing, then close
     * the Searcher and create a regular one for searching.
     *
     * @param indexDir the index directory
     * @param createNewIndex if true, create a new index even if one existed there
     * @param indexTemplateFile JSON template to use for index structure / metadata
     * @return the searcher in index mode
     * @throws ErrorOpeningIndex if index couldn't be opened
     */
    static BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex, File indexTemplateFile)
            throws ErrorOpeningIndex {
        return new BlackLabIndexImpl(indexDir, true, createNewIndex, indexTemplateFile);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * Note that in index mode, searching operations may not take the latest changes
     * into account. It is wisest to only use index mode for indexing, then close
     * the Searcher and create a regular one for searching.
     *
     * @param indexDir the index directory
     * @param createNewIndex if true, create a new index even if one existed there
     * @param config input format config to use as template for index structure /
     *            metadata (if creating new index)
     * @return the searcher in index mode
     * @throws ErrorOpeningIndex if the index couldn't be opened 
     */
    static BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex, ConfigInputFormat config)
            throws ErrorOpeningIndex {
        return new BlackLabIndexImpl(indexDir, true, createNewIndex, config);
    }

    /**
     * Create an empty index.
     *
     * @param indexDir where to create the index
     * @return a Searcher for the new index, in index mode
     * @throws ErrorOpeningIndex if the index couldn't be opened 
     */
    static BlackLabIndexWriter create(File indexDir) throws ErrorOpeningIndex {
        return create(indexDir, null, null, null, false, TextDirection.LEFT_TO_RIGHT);
    }

    /**
     * Create an empty index.
     *
     * @param indexDir where to create the index
     * @param displayName the display name for the new index, or null to assign one
     *            automatically (based on the directory name)
     * @return a Searcher for the new index, in index mode
     * @throws ErrorOpeningIndex if the index couldn't be opened 
     */
    static BlackLabIndex create(File indexDir, String displayName) throws ErrorOpeningIndex {
        return create(indexDir, null, displayName, null, false, TextDirection.LEFT_TO_RIGHT);
    }

    /**
     * Create an empty index.
     *
     * @param indexDir where to create the index
     * @param config format configuration for this index; used to base the index
     *            metadata on
     * @param displayName the display name for the new index, or null to assign one
     *            automatically (based on the directory name)
     * @param contentViewable is viewing of the document contents allowed?
     * @param textDirection text direction for this corpus
     * @param formatIdentifier a format identifier to store as the document format,
     *            or null for none. See {@link DocumentFormats} class.
     * @return a Searcher for the new index, in index mode
     * @throws ErrorOpeningIndex if the index couldn't be opened 
     */
    static BlackLabIndexWriter create(File indexDir, ConfigInputFormat config, String displayName,
            String formatIdentifier, boolean contentViewable, TextDirection textDirection) throws ErrorOpeningIndex {
        BlackLabIndexWriter rv = openForWriting(indexDir, true, config);
        IndexMetadataWriter indexMetadata = rv.metadataWriter();
        if (!StringUtils.isEmpty(displayName))
            indexMetadata.setDisplayName(displayName);
        if (config != null && config.getName() != null)
            indexMetadata.setDocumentFormat(config.getName());
        else if (!StringUtils.isEmpty(formatIdentifier)) {
            indexMetadata.setDocumentFormat(formatIdentifier);
        }
        indexMetadata.setContentViewable(contentViewable);
        if (textDirection != null)
            indexMetadata.setTextDirection(textDirection);
        indexMetadata.save();
        return rv;
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
    IndexMetadataWriter metadataWriter();

    IndexWriter openIndexWriter(File indexDir, boolean create, Analyzer useAnalyzer)
            throws IOException, CorruptIndexException, LockObtainFailedException;

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

    Annotation getOrCreateAnnotation(AnnotatedField field, String annotName);

}
