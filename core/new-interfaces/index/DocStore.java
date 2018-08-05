package nl.inl.blacklab.interfaces.index;

import java.io.Closeable;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * Base interface for {@link ContentStore} and {@link ForwardIndex}.
 * 
 * Note that right now, these unfortunately still have their own
 * ids. These will eventually disappear as we integrate the forward
 * index and content store into the Lucene index.
 * 
 * You can mostly avoid using them by calling {@link Doc#forwardIndexDoc(nl.inl.blacklab.interfaces.struct.AnnotatedField)},
 * and {@link Doc#contentStoreDoc(nl.inl.blacklab.interfaces.struct.Field)}, which fetch documents based on the Lucene id,
 * automatically performing any necessary id translations.
 *
 * @param <T> document type
 */
public interface DocStore<T> extends Closeable, Iterable<T> {
    /**
     * Clear the entire content store.
     */
    void clear();

    /**
     * Create a new document to be stored.
     * 
     * Fill the document using methods on the document type,
     * then call {@link #store(T)} to store it.
     * 
     * The reason for this create() method is that large documents
     * may be partially stored while they are being filled (e.g.
     * {@link ContentStore} does this), so we need to know about the document
     * while it's being filled.
     * 
     * @return a new, empty document
     */
    T create();
    
    /**
     * Store a document created with {@link #create()}.
     * 
     * @param doc document to store
     * @return document store id
     */
    int store(T doc);
    
    /**
     * Iterate over documents
     * 
     * @return documents iterator
     */
    @Override
    Iterator<T> iterator();
    
    /**
     * Stream all documents
     * 
     * @return stream of documents
     */
    Stream<T> stream();

    /**
     * Get a document in the store.
     * 
     * @param id the document's store id
     * @return document handle
     */
    T doc(int id);

    /**
     * @return the number of documents 
     */
    int numberOfDocuments();

}
