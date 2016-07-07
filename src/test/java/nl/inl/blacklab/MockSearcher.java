package nl.inl.blacklab;

import java.io.File;
import java.io.IOException;
import java.text.Collator;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.store.LockObtainFailedException;

import nl.inl.blacklab.externalstorage.ContentStore;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.highlight.XmlHighlighter;
import nl.inl.blacklab.highlight.XmlHighlighter.UnbalancedTagsStrategy;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.DocContentsFromForwardIndex;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.indexstructure.IndexStructure;

public class MockSearcher extends Searcher {

	private ForwardIndex forwardIndex;

	@Override
	public UnbalancedTagsStrategy getDefaultUnbalancedTagsStrategy() {
		return null;
	}

	@Override
	public void setDefaultUnbalancedTagsStrategy(UnbalancedTagsStrategy strategy) {
		//
	}

	@Override
	public ConcordanceType getDefaultConcordanceType() {
		return null;
	}

	@Override
	public void setDefaultConcordanceType(ConcordanceType type) {
		//
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
	public void close() {
		//

	}

	@Override
	public IndexStructure getIndexStructure() {
		//
		return null;
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
	public SpanQuery filterDocuments(SpanQuery query, Filter filter) {
		//
		return null;
	}

	@Override
	public SpanQuery createSpanQuery(TextPattern pattern, String fieldName, Filter filter) {
		//
		return null;
	}

	@Override
	public SpanQuery createSpanQuery(TextPattern pattern, Filter filter) {
		//
		return null;
	}

	@Override
	public SpanQuery createSpanQuery(TextPattern pattern, String fieldName) {
		//
		return null;
	}

	@Override
	public SpanQuery createSpanQuery(TextPattern pattern) {
		//
		return null;
	}

	@Override
	public Hits find(SpanQuery query, String fieldNameConc) throws TooManyClauses {
		//
		return null;
	}

	@Override
	public Hits find(SpanQuery query) throws TooManyClauses {
		//
		return null;
	}

	@Override
	public Hits find(TextPattern pattern, String fieldName, Filter filter) throws TooManyClauses {
		//
		return null;
	}

	@Override
	public Hits find(TextPattern pattern, Filter filter) throws TooManyClauses {
		//
		return null;
	}

	@Override
	public Hits find(TextPattern pattern, String fieldName) throws TooManyClauses {
		//
		return null;
	}

	@Override
	public Hits find(TextPattern pattern) throws TooManyClauses {
		//
		return null;
	}

	@Override
	public Scorer findDocScores(Query q) {
		//
		return null;
	}

	@Override
	public TopDocs findTopDocs(Query q, int n) {
		//
		return null;
	}

	@Override
	public void getCharacterOffsets(int doc, String fieldName, int[] startsOfWords, int[] endsOfWords, boolean fillInDefaultsIfNotFound) {
		//

	}

	@Override
	public DocContentsFromForwardIndex getContentFromForwardIndex(int docId, String fieldName, int startAtWord, int endAtWord) {
		//
		return null;
	}

	@Override
	public String getContent(int docId, String fieldName, int startAtWord, int endAtWord) {
		//
		return null;
	}

	@Override
	public String getContentByCharPos(int docId, String fieldName, int startAtChar, int endAtChar) {
		//
		return null;
	}

	@Override
	public String getContent(int docId, String fieldName) {
		//
		return null;
	}

	@Override
	public String getContent(int docId) {
		//
		return null;
	}

	@Override
	public DirectoryReader getIndexReader() {
		//
		return null;
	}

	@Override
	public String highlightContent(int docId, String fieldName, Hits hits, int startAtWord, int endAtWord) {
		//
		return null;
	}

	@Override
	public String highlightContent(int docId, String fieldName, Hits hits) {
		//
		return null;
	}

	@Override
	public String highlightContent(int docId, Hits hits) {
		//
		return null;
	}

	@Override
	public ContentStore getContentStore(String fieldName) {
		return null;
	}

	@Override
	public void setCollator(Collator collator) {
		//
	}

	@Override
	public Collator getCollator() {
		return null;
	}

	@Override
	public ForwardIndex getForwardIndex(String fieldPropName) {
		return forwardIndex;
	}

	@Override
	public List<Concordance> makeConcordancesFromContentStore(int doc, String fieldName, int[] startsOfWords, int[] endsOfWords,
			XmlHighlighter hl) {
		return null;
	}

	@Override
	public void setConcordanceXmlProperties(String wordFI, String punctFI, Collection<String> attrFI) {
		//
	}

	@Override
	public int getDefaultContextSize() {
		return 0;
	}

	@Override
	public void setDefaultContextSize(int defaultContextSize) {
		//
	}

	@Override
	public ContentStore openContentStore(File indexXmlDir, boolean create) {
		return null;
	}

	@Override
	public ContentStore openContentStore(File indexXmlDir) {
		return null;
	}

	@Override
	public Terms getTerms(String fieldPropName) {
		return null;
	}

	@Override
	public Terms getTerms() {
		return null;
	}

	@Override
	public String getContentsFieldMainPropName() {
		return null;
	}

	@Override
	public boolean isDefaultSearchCaseSensitive() {
		return false;
	}

	@Override
	public boolean isDefaultSearchDiacriticsSensitive() {
		return false;
	}

	@Override
	public void setDefaultSearchSensitive(boolean b) {
		//
	}

	@Override
	public void setDefaultSearchSensitive(boolean caseSensitive, boolean diacriticsSensitive) {
		//
	}

	@Override
	public QueryExecutionContext getDefaultExecutionContext(String fieldName) {
		return null;
	}

	@Override
	public QueryExecutionContext getDefaultExecutionContext() {
		return null;
	}

	@Override
	public String getIndexName() {
		return null;
	}

	@Override
	public IndexWriter openIndexWriter(File indexDir, boolean create, Analyzer analyzer)
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
	public Analyzer getAnalyzer() {
		return null;
	}

	@Override
	public DocResults queryDocuments(Query documentFilterQuery) {
		return null;
	}

	@Override
	public Map<String, Integer> termFrequencies(Query documentFilterQuery, String fieldName, String propName, String altName) {
		return null;
	}

	@Override
	public void collectDocuments(Query query, Collector collector) {
		//
	}

	@Override
	public List<String> getFieldTerms(String fieldName, int maxResults) {
		return null;
	}

	@Override
	public String getMainContentsFieldName() {
		return null;
	}

	@Override
	public String getConcWordFI() {
		return null;
	}

	@Override
	public String getConcPunctFI() {
		return null;
	}

	@Override
	public Collection<String> getConcAttrFI() {
		return null;
	}

	@Override
	public Map<String, ForwardIndex> getForwardIndices() {
		return null;
	}

	@Override
	public IndexSearcher getIndexSearcher() {
		return null;
	}

	public void setForwardIndex(MockForwardIndex forwardIndex) {
		this.forwardIndex = forwardIndex;
	}

}
