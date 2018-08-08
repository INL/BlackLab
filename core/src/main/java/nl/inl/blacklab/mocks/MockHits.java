package nl.inl.blacklab.mocks;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;

public class MockHits extends Hits {

    private int[] doc;
    private int[] start;
    private int[] end;

    private int numberOfDocs;

    public MockHits(BlackLabIndex searcher, int[] doc, int[] start, int[] end) {
        super(searcher);
        this.doc = doc;
        this.start = start;
        this.end = end;
        countDocs();
    }

    private void countDocs() {
        numberOfDocs = 0;
        int prevDoc = -1;
        for (int d : doc) {
            if (d != prevDoc) {
                numberOfDocs++;
                prevDoc = d;
            }
        }
    }

    public MockHits(BlackLabIndex searcher) {
        this(searcher, new int[0], new int[0], new int[0]);
    }

    public MockHits(MockHits o) {
        this(o.getSearcher(), o.doc.clone(), o.start.clone(), o.end.clone());
    }

    @Override
    public Hits copy() {
        return new MockHits(index, doc, start, end);
    }

    @Override
    public void copySettingsFrom(Hits copyFrom) {
        // NOP
    }

    @Override
    public boolean maxHitsRetrieved() {
        return false;
    }

    @Override
    public boolean maxHitsCounted() {
        return false;
    }

    @Override
    public Hits sortedBy(HitProperty sortProp, boolean reverseSort) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean sizeAtLeast(int lowerBound) {
        return size() >= lowerBound;
    }

    @Override
    public int size() {
        return start.length;
    }

    @Override
    public int totalSize() {
        return size();
    }

    @Override
    public int numberOfDocs() {
        return numberOfDocs;
    }

    @Override
    public int totalNumberOfDocs() {
        return numberOfDocs();
    }

    @Override
    public int countSoFarHitsCounted() {
        return size();
    }

    @Override
    public int countSoFarHitsRetrieved() {
        return size();
    }

    @Override
    public int countSoFarDocsCounted() {
        return numberOfDocs();
    }

    @Override
    public int countSoFarDocsRetrieved() {
        return numberOfDocs();
    }

    @Override
    public boolean doneFetchingHits() {
        return true;
    }

    @Override
    public Hit getByOriginalOrder(int i) {
        return get(i);
    }

    @Override
    public Hit get(int i) {
        return Hit.create(doc[i], start[i], end[i]);
    }

    @Override
    public Kwic getKwic(AnnotatedField fieldName, Hit hit, int contextSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Kwic getKwic(Hit h, int contextSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Concordance getConcordance(AnnotatedField fieldName, Hit hit, int contextSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Concordance getConcordance(Hit h, int contextSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void findContext(List<Annotation> fieldProps) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TermFrequencyList getCollocations(Annotation propName, QueryExecutionContext ctx, boolean sort) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasCapturedGroups() {
        return false;
    }

    @Override
    public Span[] getCapturedGroups(Hit hit) {
        return null;
    }

    @Override
    public Map<String, Span> getCapturedGroupMap(Hit hit) {
        return null;
    }

    @Override
    public List<Annotation> getContextFieldPropName() {
        return null;
    }

    @Override
    public void setContextField(List<Annotation> contextField) {
        // NOP
    }

    @Override
    public Hits getHitsInDoc(int docid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int[] getHitContext(int hitNumber) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "MockHits#" + hitsObjId;
    }

    @Override
    public Iterator<Hit> iterator() {
        return new Iterator<Hit>() {

            int current = -1;

            @Override
            public boolean hasNext() {
                return current < doc.length - 1;
            }

            @Override
            public Hit next() {
                current++;
                return Hit.create(doc[current], start[current], end[current]);
            }

        };
    }

}
