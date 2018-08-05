package nl.inl.blacklab.interfaces.index;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.CorruptIndexException;

import nl.inl.blacklab.index.IndexListener;
import nl.inl.blacklab.index.complex.ComplexFieldProperty;
import nl.inl.blacklab.interfaces.struct.AnnotatedField;
import nl.inl.blacklab.interfaces.struct.IndexMetadata;

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
    BlackLabIndexWriter writer();
    
    /**
     * Get the index metadata.
     * 
     * @return metadata
     */
    IndexMetadata metadata();
    
    /**
     * Add a Lucene document to the index
     *
     * @param document
     *            the document to add
     * @throws CorruptIndexException
     * @throws IOException
     */
    void add(Document document) throws CorruptIndexException, IOException;

    /**
     * Add a list of tokens to a forward index
     *
     * @param field what forward index to add this to
     * @param prop the property to get values and position increments from
     * @return the id assigned to the content
     */
    public int addToForwardIndex(AnnotatedField field, ComplexFieldProperty prop);

    /**
     * Should we continue indexing or stop?
     *
     * We stop if we've reached the maximum that was set (if any),
     * or if a fatal error has occurred (indicated by terminateIndexing).
     *
     * @return true if we should continue, false if not
     */
    public boolean continueIndexing();

    /**
     * How many more documents should we process?
     *
     * @return the number of documents
     */
    public int docsToDoLeft();

    public File getLinkedFile(String inputFile);
    
    public FieldType getMetadataFieldType(boolean tokenized);

    /**
     * Get our index listener, or create a console reporting listener if none was set yet.
     *
     * Also reports the creation of the Indexer and start of indexing, if it hadn't been reported
     * already.
     *
     * @return the listener
     */
    IndexListener getListener();
    
    /**
     * Get the parameters we would like to be passed to the DocIndexer class.
     *
     * Used by DocIndexer classes to get their parameters.
     * @return the parameters
     */
    public Map<String, String> getIndexerParameters();

    public Optional<Function<String, File>> getLinkedFileResolver();

}
