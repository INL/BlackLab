package nl.inl.blacklab.mocks;

import java.util.Iterator;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.CapturedGroups;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.HitsAbstract;
import nl.inl.blacklab.search.results.MaxStats;
import nl.inl.blacklab.search.results.QueryInfo;

public class MockHits extends HitsAbstract {

    private int[] doc;
    private int[] start;
    private int[] end;

    private int numberOfDocs;

    public MockHits(BlackLabIndex index, AnnotatedField field, int[] doc, int[] start, int[] end) {
        super(QueryInfo.create(index, field));
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

    public MockHits(BlackLabIndex index, AnnotatedField field) {
        this(index, field, new int[0], new int[0], new int[0]);
    }

    public MockHits(MockHits o) {
        this(o.index(), o.field(), o.doc.clone(), o.start.clone(), o.end.clone());
    }

    @Override
    public MockHits copy() {
        return new MockHits(index(), field(), doc, start, end);
    }

    @Override
    public Hits sortedBy(HitProperty sortProp, boolean reverseSort) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hitsProcessedAtLeast(int lowerBound) {
        return hitsProcessedTotal() >= lowerBound;
    }

    @Override
    public int hitsProcessedTotal() {
        return start.length;
    }

    @Override
    public int hitsCountedTotal() {
        return hitsProcessedTotal();
    }

    @Override
    public int docsProcessedTotal() {
        return numberOfDocs;
    }

    @Override
    public int docsCountedTotal() {
        return docsProcessedTotal();
    }

    @Override
    public int hitsCountedSoFar() {
        return hitsProcessedTotal();
    }

    @Override
    public int hitsProcessedSoFar() {
        return hitsProcessedTotal();
    }

    @Override
    public int docsCountedSoFar() {
        return docsProcessedTotal();
    }

    @Override
    public int docsProcessedSoFar() {
        return docsProcessedTotal();
    }

    @Override
    public boolean doneProcessingAndCounting() {
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
    public TermFrequencyList collocations(int contextSize, Annotation propName, QueryExecutionContext ctx, boolean sort) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasCapturedGroups() {
        return false;
    }

    @Override
    public MockHits getHitsInDoc(int docid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "MockHits#" + resultsObjId();
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

    @Override
    public Iterable<Hit> originalOrder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CapturedGroups capturedGroups() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Hits sortedBy(HitProperty sortProp) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected int indexOf(Hit hit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MaxStats maxStats() {
        return MaxStats.NOT_EXCEEDED;
    }

}
