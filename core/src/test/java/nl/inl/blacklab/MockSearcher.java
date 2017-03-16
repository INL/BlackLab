package nl.inl.blacklab;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.LockObtainFailedException;
import org.mockito.Mockito;

import nl.inl.blacklab.externalstorage.ContentStore;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.Searcher;

public class MockSearcher extends Searcher {

	public MockSearcher() {
		mainContentsFieldName = Searcher.DEFAULT_CONTENTS_FIELD_NAME;
		hitsSettings().setContextSize(5);

		// Register ourselves in the mapping from IndexReader to Searcher,
		// so we can find the corresponding Searcher object from within Lucene code
		searcherFromIndexReader.put(null, this);
	}

	@Override
	public boolean isEmpty() {
		//
		return false;
	}

	@Override
	public void rollback() {
		//

	}

	@Override
	public Document document(int doc) {
		//
		return null;
	}

	@Override
	public boolean isDeleted(int doc) {
		//
		return false;
	}

	@Override
	public int maxDoc() {
		//
		return 0;
	}

	@Override
	public void getCharacterOffsets(int doc, String fieldName, int[] startsOfWords, int[] endsOfWords, boolean fillInDefaultsIfNotFound) {
		throw new UnsupportedOperationException();
	}

	@Override
	public IndexReader getIndexReader() {
		return null;
	}

	@Override
	public ForwardIndex openForwardIndex(String fieldPropName) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ContentStore openContentStore(File indexXmlDir, boolean create) {
		return null;
	}

	@Override
	public QueryExecutionContext getDefaultExecutionContext(String fieldName) {
		return QueryExecutionContext.getSimple(this, fieldName);
	}

	@Override
	public String getIndexName() {
		return null;
	}

	@Override
	public IndexWriter openIndexWriter(File indexDir, boolean create, Analyzer useAnalyzer)
			throws IOException, CorruptIndexException, LockObtainFailedException {
		return null;
	}

	@Override
	public IndexWriter getWriter() {
		return null;
	}

	@Override
	public File getIndexDirectory() {
		return null;
	}

	@Override
	public void delete(Query q) {
		//
	}

	@Override
	public DocResults queryDocuments(Query documentFilterQuery) {
		return null;
	}

	@Override
	public List<String> getFieldTerms(String fieldName, int maxResults) {
		return null;
	}

	@Override
	public IndexSearcher getIndexSearcher() {
		IndexSearcher searcher = Mockito.mock(IndexSearcher.class);
		Mockito.when(searcher.getSimilarity(Mockito.anyBoolean())).thenReturn(new BM25Similarity());
		return searcher;
	}

	public void setForwardIndex(String fieldPropName, ForwardIndex forwardIndex) {
		addForwardIndex(fieldPropName, forwardIndex);
	}

	@Override
	protected ContentStore openContentStore(String fieldName) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<Integer> docIdSet() {
		return null;
	}

}
