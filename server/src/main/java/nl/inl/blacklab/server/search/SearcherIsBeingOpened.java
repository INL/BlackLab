package nl.inl.blacklab.server.search;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/** A dummy Searcher placeholder while Searcher is being opened. */
public class SearcherIsBeingOpened extends Searcher {

    private String indexName;

    private File indexDir;

    public SearcherIsBeingOpened(String indexName, File indexDir) {
        super();
        this.indexName = indexName;
        this.indexDir = indexDir;
    }

    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void rollback() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Document document(int doc) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Integer> docIdSet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDeleted(int doc) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int maxDoc() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getCharacterOffsets(int doc, String fieldName, int[] startsOfWords, int[] endsOfWords,
            boolean fillInDefaultsIfNotFound) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IndexReader getIndexReader() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ContentStore openContentStore(String fieldName) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ForwardIndex openForwardIndex(Annotation fieldPropName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public QueryExecutionContext getDefaultExecutionContext(String fieldName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getIndexName() {
        return indexName;
    }

    @Override
    public IndexWriter openIndexWriter(File indexDir, boolean create, Analyzer useAnalyzer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IndexWriter getWriter() {
        throw new UnsupportedOperationException();
    }

    @Override
    public File getIndexDirectory() {
        return indexDir;
    }

    @Override
    public void delete(Query q) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getFieldTerms(String fieldName, int maxResults) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IndexSearcher getIndexSearcher() {
        throw new UnsupportedOperationException();
    }

}
