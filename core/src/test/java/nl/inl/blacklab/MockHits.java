package nl.inl.blacklab;

import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.grouping.HitProperty;

public class MockHits extends Hits {

	private int[] doc;
	private int[] start;
	private int[] end;

	private int numberOfDocs;

	public MockHits(Searcher searcher, int[] doc, int[] start, int[] end) {
		super(searcher);
		this.doc = doc;
		this.start = start;
		this.end = end;
		countDocs();
	}

	private void countDocs() {
		numberOfDocs = 0;
		int prevDoc = -1;
		for (int d: doc) {
			if (d != prevDoc) {
				numberOfDocs++;
				prevDoc = d;
			}
		}
	}

	public MockHits(Searcher searcher) {
		this(searcher, new int[0], new int[0], new int[0]);
	}

	public MockHits(MockHits o) {
		this(o.getSearcher(), o.doc.clone(), o.start.clone(), o.end.clone());
	}

	@Override
	public Hits copy() {
		return new MockHits(searcher, doc, start, end);
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
	@Deprecated
	public void sort(HitProperty sortProp, boolean reverseSort, boolean sensitive) {
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

	@SuppressWarnings("deprecation")
	@Override
	public Iterable<Hit> hitsInOriginalOrder() {
		return getHits();
	}

	@Override
	public Hit getByOriginalOrder(int i) {
		return get(i);
	}

	@Override
	public Hit get(int i) {
		return new Hit(doc[i], start[i], end[i]);
	}

	@Override
	public Kwic getKwic(String fieldName, Hit hit, int contextSize) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Kwic getKwic(Hit h, int contextSize) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Concordance getConcordance(String fieldName, Hit hit, int contextSize) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Concordance getConcordance(Hit h, int contextSize) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void findContext(List<String> fieldProps) {
		throw new UnsupportedOperationException();
	}

	@Override
	public TermFrequencyList getCollocations(String propName, QueryExecutionContext ctx) {
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
	public List<String> getContextFieldPropName() {
		return null;
	}

	@Override
	public void setContextField(List<String> contextField) {
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
	public void setMaxHitsCounted(boolean maxHitsCounted) {
		// NOP
	}

	@Override
	protected void setMaxHitsRetrieved(boolean maxHitsRetrieved) {
		// NOP
	}

}
