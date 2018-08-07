package nl.inl.blacklab;

import java.io.File;
import java.io.IOException;
import java.text.Collator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.store.LockObtainFailedException;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import nl.inl.blacklab.analysis.BLStandardAnalyzer;
import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexRegistry;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.DocContentsFromForwardIndex;
import nl.inl.blacklab.search.LuceneDocTask;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.QueryExplanation;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MockIndexMetadata;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.HitsSettings;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.util.XmlHighlighter;
import nl.inl.util.XmlHighlighter.UnbalancedTagsStrategy;

public class MockSearcher implements BlackLabIndex {

    private IndexMetadata indexMetadata;

    private HitsSettings hitsSettings;

    private Map<Annotation, ForwardIndex> forwardIndices = new HashMap<>();

    private Analyzer analyzer;

    public MockSearcher() {
        super();
        indexMetadata = new MockIndexMetadata();
        hitsSettings = new HitsSettings(this);
        analyzer = new BLStandardAnalyzer();
        hitsSettings().setContextSize(5);

        // Register ourselves in the mapping from IndexReader to Searcher,
        // so we can find the corresponding Searcher object from within Lucene code
        BlackLabIndexRegistry.registerSearcher(null, this);
    }

    @Override
    public void close() {
        BlackLabIndexRegistry.removeSearcher(this);
    }

    @Override
    public boolean isEmpty() {
        return false;
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
    public boolean isDeleted(int doc) {
        return false;
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
    public IndexReader reader() {
        return null; // used by HitsImpl
    }

    @Override
    public QueryExecutionContext defaultExecutionContext(AnnotatedField field) {
        return QueryExecutionContext.simple(this, field);
    }

    @Override
    public String name() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IndexWriter openIndexWriter(File indexDir, boolean create, Analyzer useAnalyzer)
            throws IOException, CorruptIndexException, LockObtainFailedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public IndexWriter writer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public File indexDirectory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete(Query q) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DocResults queryDocuments(Query documentFilterQuery) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IndexSearcher searcher() {
        IndexSearcher searcher = Mockito.mock(IndexSearcher.class);
        Mockito.when(searcher.getSimilarity(ArgumentMatchers.anyBoolean())).thenReturn(new BM25Similarity());
        return searcher;
    }

    public void setForwardIndex(Annotation fieldPropName, ForwardIndex forwardIndex) {
        forwardIndices.put(fieldPropName, forwardIndex);
    }

    @Override
    public Set<Integer> docIdSet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public HitsSettings hitsSettings() {
        return hitsSettings;
    }

    @Override
    public UnbalancedTagsStrategy defaultUnbalancedTagsStrategy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDefaultUnbalancedTagsStrategy(UnbalancedTagsStrategy strategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCollator(Collator collator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collator collator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IndexMetadata metadata() {
        return indexMetadata;
    }

    @Override
    public IndexMetadataWriter metadataWriter() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachDocument(LuceneDocTask task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BLSpanQuery createSpanQuery(TextPattern pattern, AnnotatedField field, Query filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Hits find(SpanQuery query, AnnotatedField field) throws TooManyClauses {
        throw new UnsupportedOperationException();
    }

    @Override
    public Hits find(BLSpanQuery query) throws TooManyClauses {
        throw new UnsupportedOperationException();
    }

    @Override
    public Hits find(TextPattern pattern, AnnotatedField field, Query filter) throws TooManyClauses {
        throw new UnsupportedOperationException();
    }

    @Override
    public QueryExplanation explain(BLSpanQuery query) throws TooManyClauses {
        throw new UnsupportedOperationException();
    }

    @Override
    public DocContentsFromForwardIndex getContentFromForwardIndex(int docId, AnnotatedField field, int startAtWord,
            int endAtWord) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getContentByCharPos(int docId, String fieldName, int startAtChar, int endAtChar) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getContent(int docId, String fieldName, int startAtWord, int endAtWord) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getContent(Document d, String fieldName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String highlightContent(int docId, String fieldName, Hits hits, int startAtWord, int endAtWord) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ContentStore contentStore(String fieldName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ForwardIndex forwardIndex(Annotation annotation) {
        return forwardIndices.get(annotation);
    }

    @Override
    public List<Concordance> makeConcordancesFromContentStore(int doc, String fieldName, int[] startsOfWords,
            int[] endsOfWords, XmlHighlighter hl) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MatchSensitivity defaultMatchSensitivity() {
        return MatchSensitivity.CASE_INSENSITIVE;
    }

    @Override
    public void setDefaultMatchSensitivity(MatchSensitivity m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Analyzer analyzer() {
        return analyzer;
    }

    @Override
    public Map<Annotation, ForwardIndex> forwardIndices() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canDoNfaMatching() {
        return false;
    }

    @Override
    public Annotation getOrCreateAnnotation(AnnotatedField field, String annotName) {
        throw new UnsupportedOperationException();
    }

}
