package nl.inl.blacklab.index;

import java.io.IOException;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;

/**
 * Proxy for an IndexWriter object.
 *
 * This is necessary because in Solr mode, we don't directly write to the
 * IndexWriter; the proxy implementation will simply collect any document(s)
 * to be added, and they will eventually be handed over to Solr to be processed.
 */
public interface BLIndexWriterProxy {
    void addDocument(BLInputDocument document) throws IOException;

    void close() throws IOException;

    void commit() throws IOException;

    void rollback() throws IOException;

    boolean isOpen();

    void deleteDocuments(Query q) throws IOException;

    long updateDocument(Term term, BLInputDocument document) throws IOException;

    /** Return number of documents modified (add/remove/update) so far */
    int getNumberOfDocs();
}
