package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;

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
	public String getField() {
		// Return only base name of complex field!
		return ComplexFieldUtil.getBaseName(query.getField());
	}

	@Override
	public String toString(String field) {
		return "BL" + query.toString(field);
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		Query q = query.rewrite(reader);
		if (!(q instanceof SpanQuery))
			throw new UnsupportedOperationException(
					"You can only use BLSpanMultiTermQueryWrapper with a suitable SpanRewriteMethod.");
		BLSpanQuery result = BLSpansWrapper.blSpanQueryFrom((SpanQuery) q);
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
	public boolean hasConstantLength() {
		return true;
	}

	@Override
	public int getMinLength() {
		return 1;
	}

	@Override
	public int getMaxLength() {
		return 1;
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores)
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

}
