package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
import nl.inl.blacklab.search.Hit;

/**
 * Ensure hits from a SpanQuery are sorted by start- or endpoint
 * (within document), and optionally eliminate duplicate hits.
 */
public class SpanQuerySorted extends BLSpanQuery {
	private BLSpanQuery src;

	boolean sortByEndpoint;

	boolean eliminateDuplicates;

	public SpanQuerySorted(BLSpanQuery src, boolean sortByEndpoint, boolean eliminateDuplicates) {
		this.src = src;
		this.sortByEndpoint = sortByEndpoint;
		this.eliminateDuplicates = eliminateDuplicates;
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		BLSpanQuery rewritten = src.rewrite(reader);
		if (rewritten != src) {
			return new SpanQuerySorted(rewritten, sortByEndpoint, eliminateDuplicates);
		}
		return this;
	}

	@Override
	public boolean matchesEmptySequence() {
		return src.matchesEmptySequence();
	}

	@Override
	public BLSpanQuery noEmpty() {
		return new SpanQuerySorted(src.noEmpty(), sortByEndpoint, eliminateDuplicates);
	}

	@Override
	public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		BLSpanWeight weight = src.createWeight(searcher, needsScores);
		return new SpanWeightSorted(weight, searcher, needsScores ? getTermContexts(weight) : null);
	}

	public class SpanWeightSorted extends BLSpanWeight {

		final BLSpanWeight weight;

		public SpanWeightSorted(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQuerySorted.this, searcher, terms);
			this.weight = weight;
		}

		@Override
		public void extractTerms(Set<Term> terms) {
			weight.extractTerms(terms);
		}

		@Override
		public void extractTermContexts(Map<Term, TermContext> contexts) {
			weight.extractTermContexts(contexts);
		}

		@Override
		public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
			BLSpans srcSpans = weight.getSpans(context, requiredPostings);
			if (srcSpans == null)
				return null;
			Comparator<Hit> comparator = sortByEndpoint ? PerDocumentSortedSpans.cmpEndPoint : PerDocumentSortedSpans.cmpStartPoint;
			return new PerDocumentSortedSpans(srcSpans, comparator, eliminateDuplicates);
		}
	}

	@Override
	public String toString(String field) {
		return "SORT(" + src + ", " + (sortByEndpoint ? "END" : "START") + ", " + eliminateDuplicates + ")";
	}

	@Override
	public String getField() {
		return src.getField();
	}

	@Override
	public String getRealField() {
		return src.getRealField();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o instanceof SpanQuerySorted) {
			final SpanQuerySorted that = (SpanQuerySorted) o;
			return src.equals(that.src) && sortByEndpoint == that.sortByEndpoint && eliminateDuplicates == that.eliminateDuplicates;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return src.hashCode() ^ 0x98764038 ^ (sortByEndpoint ? 31 : 0) ^ (eliminateDuplicates ? 37 : 0);
	}

	@Override
	public boolean hitsAllSameLength() {
		return src.hitsAllSameLength();
	}

	@Override
	public int hitsLengthMin() {
		return src.hitsLengthMin();
	}

	@Override
	public int hitsLengthMax() {
		return src.hitsLengthMax();
	}

	@Override
	public boolean hitsStartPointSorted() {
		return true;
	}

	@Override
	public boolean hitsEndPointSorted() {
		return src.hitsEndPointSorted();
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return src.hitsHaveUniqueStart();
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return src.hitsHaveUniqueEnd();
	}

	@Override
	public boolean hitsAreUnique() {
		return true;
	}

	@Override
	public long estimatedNumberOfHits(IndexReader reader) {
		return src.estimatedNumberOfHits(reader);
	}
}
