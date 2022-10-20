package nl.inl.blacklab.index;

import java.io.IOException;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;

public interface BLIndexWriterProxy {
    void addDocument(BLInputDocument document) throws IOException;

    void close() throws IOException;

    void commit() throws IOException;

    void rollback() throws IOException;

    boolean isOpen();

    void deleteDocuments(Query q) throws IOException;

    long updateDocument(Term term, BLInputDocument document) throws IOException;

    int getNumberOfDocs();
}
