package nl.inl.blacklab.index;

import java.io.Closeable;
import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;

public class BLIndexWriterProxyLucene implements BLIndexWriterProxy, Closeable {

    private final IndexWriter indexWriter;

    public BLIndexWriterProxyLucene(IndexWriter indexWriter) {
        this.indexWriter = indexWriter;
    }

    @Override
    public void addDocument(BLInputDocument document) throws IOException {
        indexWriter.addDocument(luceneDoc((BLInputDocumentLucene) document));
    }

    private Document luceneDoc(BLInputDocument document) {
        return ((BLInputDocumentLucene)document).getDocument();
    }

    @Override
    public void close() throws IOException {
        indexWriter.close();
    }

    @Override
    public void commit() throws IOException {
        indexWriter.commit();
    }

    @Override
    public void rollback() throws IOException {
        indexWriter.rollback();
    }

    @Override
    public boolean isOpen() {
        return indexWriter.isOpen();
    }

    public IndexWriter getWriter() {
        return indexWriter;
    }

    @Override
    public void deleteDocuments(Query q) throws IOException {
        indexWriter.deleteDocuments(q);
    }

    @Override
    public long updateDocument(Term term, BLInputDocument document) throws IOException {
        return indexWriter.updateDocument(term, luceneDoc(document));
    }

    @Override
    public int getNumberOfDocs() {
        return indexWriter.getDocStats().numDocs;
    }
}
