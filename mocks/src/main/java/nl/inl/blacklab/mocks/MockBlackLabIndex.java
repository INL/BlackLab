package nl.inl.blacklab.mocks;

import nl.inl.blacklab.analysis.BLStandardAnalyzer;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.search.*;
import nl.inl.blacklab.search.indexmetadata.*;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.*;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.searches.SearchCacheDummy;
import nl.inl.blacklab.searches.SearchEmpty;
import nl.inl.util.XmlHighlighter.UnbalancedTagsStrategy;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import java.io.File;
import java.text.Collator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MockBlackLabIndex implements BlackLabIndex {

    private IndexMetadata indexMetadata;

    private SearchSettings searchSettings;

    private Map<Annotation, AnnotationForwardIndex> forwardIndices = new HashMap<>();

    private Analyzer analyzer;

    private IndexSearcher searcher;

    private SearchCache cache = new SearchCacheDummy();

    private BlackLabEngine blackLab;

    public MockBlackLabIndex() {
        super();
        indexMetadata = new MockIndexMetadata();
        analyzer = new BLStandardAnalyzer();
        searchSettings = SearchSettings.defaults();

        // Register ourselves in the mapping from IndexReader to BlackLabIndex,
        // so we can find the corresponding BlackLabIndex object from within Lucene code
        blackLab = BlackLab.implicitInstance();
        blackLab.registerSearcher(null, this);
    }
    
    public QueryInfo createDefaultQueryInfo() {
        return QueryInfo.create(this, mainAnnotatedField());
    }

    @Override
    public void close() {
        blackLab.removeSearcher(this);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public Doc doc(int docId) {
        return new DocImpl(this, docId);
    }

    @Override
    public boolean docExists(int doc) {
        return true;
    }

    @Override
    public IndexReader reader() {
        return null; // used by Hits
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
    public File indexDirectory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DocResults queryDocuments(Query documentFilterQuery) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IndexSearcher searcher() {
        return searcher;
    }
    
    public void setIndexSearcher(IndexSearcher searcher) {
        this.searcher = searcher;
    }

    public void setForwardIndex(Annotation fieldPropName, AnnotationForwardIndex forwardIndex) {
        forwardIndices.put(fieldPropName, forwardIndex);
    }

    @Override
    public SearchSettings searchSettings() {
        return searchSettings;
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
    public void forEachDocument(DocTask task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Hits find(BLSpanQuery query, SearchSettings settings) throws TooManyClauses {
        throw new UnsupportedOperationException();
    }

    @Override
    public QueryExplanation explain(BLSpanQuery query) throws TooManyClauses {
        throw new UnsupportedOperationException();
    }

    @Override
    public ContentAccessor contentAccessor(Field field) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AnnotationForwardIndex annotationForwardIndex(Annotation annotation) {
        return forwardIndices.get(annotation);
    }

    @Override
    public ForwardIndex forwardIndex(AnnotatedField field) {
        return null;
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
    public void setSearchSettings(SearchSettings settings) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDefaultContextSize(ContextSize size) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ContextSize defaultContextSize() {
        return ContextSize.get(5);
    }

    @Override
    public boolean indexMode() {
        return false;
    }

    @Override
    public TermFrequencyList termFrequencies(AnnotationSensitivity annotSensitivity, Query filterQuery, Set<String> terms) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SearchEmpty search(AnnotatedField field, boolean useCache) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void setCache(SearchCache cache) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SearchCache cache() {
        return cache;
    }

//    @Override
//    public BLSpanQuery createSpanQuery(QueryInfo queryInfo, TextPattern pattern, Query filter) throws RegexpTooLarge {
//        throw new UnsupportedOperationException();
//    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "()";
    }
    
    @Override
    public BlackLabEngine blackLab() {
        return blackLab;
    }
}
