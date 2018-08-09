package nl.inl.blacklab.mocks;

import java.util.Iterator;
import java.util.Map;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.HitsAbstract;
import nl.inl.blacklab.search.results.HitsImpl;
import nl.inl.blacklab.search.results.HitsSettings;

public class MockHits extends HitsImpl {

    private int[] doc;
    private int[] start;
    private int[] end;

    private int numberOfDocs;

    public MockHits(BlackLabIndex searcher, AnnotatedField field, int[] doc, int[] start, int[] end) {
        super(searcher, field, null);
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

    public MockHits(BlackLabIndex searcher, AnnotatedField field) {
        this(searcher, field, new int[0], new int[0], new int[0]);
    }

    public MockHits(MockHits o) {
        this(o.index(), o.field(), o.doc.clone(), o.start.clone(), o.end.clone());
    }

    @Override
    public MockHits copy(HitsSettings settings) {
        return new MockHits(index(), field(), doc, start, end);
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
    public HitsAbstract sortedBy(HitProperty sortProp, boolean reverseSort) {
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
    public MockHits getHitsInDoc(int docid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "MockHits#" + getHitsObjId();
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
