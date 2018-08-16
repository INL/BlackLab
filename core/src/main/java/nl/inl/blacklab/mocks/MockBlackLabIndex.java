package nl.inl.blacklab.mocks;

import java.io.File;
import java.text.Collator;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.analysis.BLStandardAnalyzer;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexRegistry;
import nl.inl.blacklab.search.ContentAccessor;
import nl.inl.blacklab.search.Doc;
import nl.inl.blacklab.search.DocImpl;
import nl.inl.blacklab.search.DocTask;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.QueryExplanation;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.MaxSettings;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.util.XmlHighlighter.UnbalancedTagsStrategy;

public class MockBlackLabIndex implements BlackLabIndex {

    private IndexMetadata indexMetadata;

    private MaxSettings maxSettings;

    private Map<Annotation, AnnotationForwardIndex> forwardIndices = new HashMap<>();

    private Analyzer analyzer;

    private IndexSearcher searcher;

    public MockBlackLabIndex() {
        super();
        indexMetadata = new MockIndexMetadata();
        analyzer = new BLStandardAnalyzer();
        maxSettings = MaxSettings.defaults();

        // Register ourselves in the mapping from IndexReader to Searcher,
        // so we can find the corresponding Searcher object from within Lucene code
        BlackLabIndexRegistry.registerSearcher(null, this);
    }
    
    public QueryInfo createDefaultQueryInfo() {
        return QueryInfo.create(this, mainAnnotatedField());
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
    public MaxSettings maxSettings() {
        return maxSettings;
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
    public BLSpanQuery createSpanQuery(TextPattern pattern, AnnotatedField field, Query filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Hits find(BLSpanQuery query, MaxSettings settings) throws TooManyClauses {
        throw new UnsupportedOperationException();
    }

    @Override
    public Hits find(TextPattern pattern, AnnotatedField field, Query filter, MaxSettings settings) throws TooManyClauses {
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
        // TODO Auto-generated method stub
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
    public void setMaxSettings(MaxSettings settings) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDefaultContextSize(ContextSize size) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ContextSize defaultContextSize() {
        return BlackLabIndex.DEFAULT_CONTEXT_SIZE;
    }

    @Override
    public boolean indexMode() {
        return false;
    }

}
