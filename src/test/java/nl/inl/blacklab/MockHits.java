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

	public MockHits(int[] doc, int[] start, int[] end) {
		this.doc = doc;
		this.start = start;
		this.end = end;
	}

	public MockHits(MockHits mockHits) {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void setPriorityLevel(Level level) {
		//

	}

	@Override
	public Level getPriorityLevel() {
		//
		return null;
	}

	@Override
	public void copySettingsFrom(Hits copyFrom) {
		//

	}

	@Override
	public int getContextSize() {
		//
		return 0;
	}

	@Override
	public void setContextSize(int contextSize) {
		//

	}

	@Override
	public boolean maxHitsRetrieved() {
		//
		return false;
	}

	@Override
	public boolean maxHitsCounted() {
		//
		return false;
	}

	@Override
	public void sort(HitProperty sortProp) {
		//

	}

	@Override
	public void sort(HitProperty sortProp, boolean reverseSort) {
		//

	}

	@Override
	public void sort(HitProperty sortProp, boolean reverseSort, boolean sensitive) {
		//

	}

	@Override
	public Hits sortedBy(HitProperty sortProp, boolean reverseSort, boolean sensitive) {
		Hits hits = new MockHits(this);
		sortProp = sortProp.copyWithHits(hits);
		hits.sort(sortProp, reverseSort, sensitive);
		return hits;
	}

	@Override
	public boolean sizeAtLeast(int lowerBound) {
		//
		return false;
	}

	@Override
	public int size() {
		//
		return start.length;
	}

	@Override
	public int totalSize() {
		//
		return size();
	}

	@Override
	public int numberOfDocs() {
		//
		return 0;
	}

	@Override
	public int totalNumberOfDocs() {
		//
		return 0;
	}

	@Override
	public int countSoFarHitsCounted() {
		//
		return size();
	}

	@Override
	public int countSoFarHitsRetrieved() {
		//
		return size();
	}

	@Override
	public int countSoFarDocsCounted() {
		//
		return 0;
	}

	@Override
	public int countSoFarDocsRetrieved() {
		//
		return 0;
	}

	@Override
	public boolean doneFetchingHits() {
		//
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public Iterable<Hit> hitsInOriginalOrder() {
		return getHits();
	}

	@Override
	public Iterator<Hit> getIterator(boolean originalOrder) {
		return iterator();
	}

	@Override
	public Iterator<Hit> iterator() {
		//
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
		//
		return get(i);
	}

	@Override
	public Hit get(int i) {
		//
		return new Hit(doc[i], start[i], end[i]);
	}

	@Override
	public Concordance getConcordance(Hit h) {
		//
		return null;
	}

	@Override
	public Kwic getKwic(Hit h) {
		//
		return null;
	}

	@Override
	public Concordance getConcordance(String fieldName, Hit hit, int contextSize) {
		//
		return null;
	}

	@Override
	public Kwic getKwic(String fieldName, Hit hit, int contextSize) {
		//
		return null;
	}

	@Override
	public Concordance getConcordance(Hit h, int contextSize) {
		//
		return null;
	}

	@Override
	public Kwic getKwic(Hit h, int contextSize) {
		//
		return null;
	}

	@Override
	public void findContext(List<String> fieldProps) {
		//

	}

	@Override
	public void clearConcordances() {
		//

	}

	@Override
	public TermFrequencyList getCollocations() {
		//
		return null;
	}

	@Override
	public TermFrequencyList getCollocations(String propName, QueryExecutionContext ctx) {
		//
		return null;
	}

	@Override
	public boolean hasCapturedGroups() {
		//
		return false;
	}

	@Override
	public Span[] getCapturedGroups(Hit hit) {
		//
		return null;
	}

	@Override
	public List<String> getCapturedGroupNames() {
		//
		return null;
	}

	@Override
	public Map<String, Span> getCapturedGroupMap(Hit hit) {
		//
		return null;
	}

	@Override
	public Searcher getSearcher() {
		//
		return null;
	}

	@Override
	public String getConcordanceFieldName() {
		//
		return null;
	}

	@Override
	public void setConcordanceField(String concordanceFieldName) {
		//

	}

	@Override
	public List<String> getContextFieldPropName() {
		//
		return null;
	}

	@Override
	public void setContextField(List<String> contextField) {
		//

	}

	@Override
	public int getMaxHitsToRetrieve() {
		//
		return 0;
	}

	@Override
	public void setMaxHitsToRetrieve(int n) {
		//

	}

	@Override
	public int getMaxHitsToCount() {
		//
		return 0;
	}

	@Override
	public void setMaxHitsToCount(int n) {
		//

	}

	@Override
	public Hits getHitsInDoc(int docid) {
		//
		return null;
	}

	@Override
	public int[] getHitContext(int hitNumber) {
		//
		return null;
	}

	@Override
	public void setForwardIndexConcordanceParameters(String wordFI, String punctFI, Collection<String> attrFI) {
		//

	}

	@Override
	public ConcordanceType getConcordanceType() {
		//
		return null;
	}

	@Override
	public void setConcordanceType(ConcordanceType type) {
		//

	}

	@Override
	protected void setConcFIs(String concWordFI, String concPunctFI, Collection<String> concAttrFI) {
		//

	}

	@Override
	protected void setMaxHitsCounted(boolean maxHitsCounted) {
		//

	}

	@Override
	protected void setMaxHitsRetrieved(boolean maxHitsRetrieved) {
		//

	}

	@Override
	public String getConcWordFI() {
		//
		return null;
	}

	@Override
	public String getConcPunctFI() {
		//
		return null;
	}

	@Override
	public Collection<String> getConcAttrFI() {
		//
		return null;
	}

	@Override
	public HitQueryContext getHitQueryContext() {
		//
		return null;
	}

	@Override
	protected void setHitQueryContext(HitQueryContext hitQueryContext) {
		//

	}

}
