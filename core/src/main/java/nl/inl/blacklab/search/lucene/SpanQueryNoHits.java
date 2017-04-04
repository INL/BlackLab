package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;

/**
 * A query matching nothing.
 */
public class SpanQueryNoHits extends BLSpanQuery {

	private String luceneField;

	public SpanQueryNoHits(String luceneField) {
		this.luceneField = luceneField;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof SpanQueryNoHits) {
			SpanQueryNoHits tp = ((SpanQueryNoHits) obj);
			return luceneField.equals(tp.luceneField);
		}
		return false;
	}

	@Override
	public BLSpanWeight createWeight(final IndexSearcher searcher, boolean needsScores) throws IOException {
		return new BLSpanWeight(SpanQueryNoHits.this, searcher, null) {
			@Override
			public void extractTerms(Set<Term> terms) {
				// No terms
			}

			@Override
			public void extractTermContexts(Map<Term, TermContext> contexts) {
				// No terms
			}

			@Override
			public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
				return null; // no hits
			}
		};
	}

	@Override
	public String toString(String field) {
		return "NOTOKENS()";
	}

	@Override
	public String getRealField() {
		return luceneField;
	}

	@Override
	public int hashCode() {
		return luceneField.hashCode();
	}

	@Override
	public BLSpanQuery inverted() {
		return new SpanQueryAnyToken(1, 1, luceneField);
	}

	@Override
	public boolean hitsAllSameLength() {
		return true;
	}

	@Override
	public int hitsLengthMin() {
		return 1;
	}

	@Override
	public int hitsLengthMax() {
		return 1;
	}

	@Override
	public boolean hitsEndPointSorted() {
		return hitsAllSameLength();
	}

	@Override
	public boolean hitsStartPointSorted() {
		return true;
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return true;
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return true;
	}

	@Override
	public boolean hitsAreUnique() {
		return true;
	}

	@Override
	public boolean canMakeNfa() {
		return false;
	}

	@Override
	public long reverseMatchingCost(IndexReader reader) {
		return 0; // no hits, no cost
	}
}
