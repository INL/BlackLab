package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanQuery;

/**
 * Subclasses SpanMultiTermQueryWrapper so it correctly produces
 * BLSpanOrQuery or BLSpanTermQuery.
 * @param <Q> the type of query we're wrapping
 */
public class BLSpanMultiTermQueryWrapper<Q extends MultiTermQuery>
		extends BLSpanQuery {

	SpanMultiTermQueryWrapper<Q> query;

	public BLSpanMultiTermQueryWrapper(Q query) {
		this.query = new SpanMultiTermQueryWrapper<Q>(query);
	}

	@Override
	public String toString(String field) {
		return "SPANWRAP(" + query.getWrappedQuery() + ")";
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		Query q = query.rewrite(reader);
		if (!(q instanceof SpanQuery))
			throw new UnsupportedOperationException(
					"You can only use BLSpanMultiTermQueryWrapper with a suitable SpanRewriteMethod.");
		BLSpanQuery result = BLSpanQuery.wrap((SpanQuery) q);
		if (result.getField() == null) {
			if (result instanceof BLSpanOrQuery) {
				((BLSpanOrQuery) result).setField(getField());
			} else {
				throw new RuntimeException("BLSpanMultiTermQueryWrapper rewritten to " +
						result.getClass().getSimpleName() + ", getField() == null");
			}
		}
		return result;
	}

	@Override
	public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores)
			throws IOException {
		throw new IllegalArgumentException("Rewrite first!");
	}

	@Override
	public int hashCode() {
		return query.hashCode() ^ 0xB1ACC1AB;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof BLSpanMultiTermQueryWrapper) {
			BLSpanMultiTermQueryWrapper<?> other = (BLSpanMultiTermQueryWrapper<?>)obj;
			return query.equals(other.query);
		}
		return false;
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
		return true;
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
	public long estimatedNumberOfHits(IndexReader reader) {
		return 0; // should be rewritten
	}

	@Override
	public String getRealField() {
		return query.getField();
	}


}
