package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.LockObtainFailedException;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;

public interface BlackLabIndexWriter extends BlackLabIndex {

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
     * @deprecated use metadata() instead
     */
    @Deprecated
    IndexMetadataWriter metadataWriter();

    /**
     * Get information about the structure of the BlackLab index.
     *
     * @return the structure object
     */
    @Override
    IndexMetadataWriter metadata();
    
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

    /**
     * Get existing annotation or create new one.
     * 
     * @param field field for which this annotation is
     * @param annotName annotation name
     * @return annotation
     */
    Annotation getOrCreateAnnotation(AnnotatedField field, String annotName);

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
