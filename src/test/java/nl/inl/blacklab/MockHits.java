package nl.inl.blacklab;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.util.ThreadPriority.Level;

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
	public void setPriorityLevel(Level level) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Level getPriorityLevel() {
		throw new UnsupportedOperationException();
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
	public int getContextSize() {
		return 0;
	}

	@Override
	public void setContextSize(int contextSize) {
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
	public void sort(HitProperty sortProp) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void sort(HitProperty sortProp, boolean reverseSort) {
		throw new UnsupportedOperationException();
	}

	@Override
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
	public Iterator<Hit> getIterator(boolean originalOrder) {
		return new Iterator<Hit>() {
			int i = -1;

			@Override
			public boolean hasNext() {
				return i + 1 < size();
			}

			@Override
			public Hit next() {
				i++;
				return get(i);
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}

		};
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
	public Kwic getKwic(Hit h) {
		throw new UnsupportedOperationException();
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
	public Concordance getConcordance(Hit h) {
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
	public void clearConcordances() {
		// NOP
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
	public List<String> getCapturedGroupNames() {
		return null;
	}

	@Override
	public Map<String, Span> getCapturedGroupMap(Hit hit) {
		return null;
	}

	@Override
	public String getConcordanceFieldName() {
		return null;
	}

	@Override
	public void setConcordanceField(String concordanceFieldName) {
		// NOP
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
	public int getMaxHitsToRetrieve() {
		return -1;
	}

	@Override
	public void setMaxHitsToRetrieve(int n) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getMaxHitsToCount() {
		return -1;
	}

	@Override
	public void setMaxHitsToCount(int n) {
		throw new UnsupportedOperationException();
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
	public void setForwardIndexConcordanceParameters(String wordFI, String punctFI, Collection<String> attrFI) {
		// NOP
	}

	@Override
	public ConcordanceType getConcordanceType() {
		return null;
	}

	@Override
	public void setConcordanceType(ConcordanceType type) {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void setMaxHitsCounted(boolean maxHitsCounted) {
		// NOP
	}

	@Override
	protected void setMaxHitsRetrieved(boolean maxHitsRetrieved) {
		// NOP
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
	public HitQueryContext getHitQueryContext() {
		return null;
	}

	@Override
	protected void setHitQueryContext(HitQueryContext hitQueryContext) {
		// NOP
	}

}
