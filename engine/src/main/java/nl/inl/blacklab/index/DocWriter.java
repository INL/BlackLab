package nl.inl.blacklab.index;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.search.BlackLabIndexWriter;

/**
 * Interface the DocIndexer gets to store documents.
 */
public interface DocWriter {

    /**
     * Get the general index writer object.
     * 
     * Not sure if this method is needed; we probably want to see if we can leave it out.
     *   
     * @return writer
     */
    BlackLabIndexWriter indexWriter();
    
    /**
     * Add a Lucene document to the index
     *
     * @param document
     *            the document to add
     */
    void add(BLInputDocument document) throws IOException;

    /**
     * Should we continue indexing or stop?
     *
     * We stop if we've reached the maximum that was set (if any),
     * or if a fatal error has occurred (indicated by terminateIndexing).
     *
     * @return true if we should continue, false if not
     */
    boolean continueIndexing();

    /**
     * How many more documents should we process?
     *
     * @return the number of documents
     */
    int docsToDoLeft();

    File linkedFile(String inputFile);
    
    BLFieldType metadataFieldType(boolean tokenized);

    /**
     * Get our index listener, or create a console reporting listener if none was set yet.
     *
     * Also reports the creation of the Indexer and start of indexing, if it hadn't been reported
     * already.
     *
     * @return the listener
     */
    IndexListener listener();
    
    /**
     * Get the parameters we would like to be passed to the DocIndexer class.
     *
     * Used by DocIndexer classes to get their parameters.
     * @return the parameters
     */
    Map<String, String> indexerParameters();

    Optional<Function<String, File>> linkedFileResolver();

    /**
     * Get the content store for the specified field.
     * 
     * @param captureContentFieldName field name
     * @return content store
     */
    ContentStore contentStore(String captureContentFieldName);

    /**
     * Add a field with its annotations to the forward index
     * 
     * @param field field to add
     * @param currentDoc Lucene doc, for storing the fiid
     */
    void addToForwardIndex(AnnotatedFieldWriter field, BLInputDocument currentDoc);

}
