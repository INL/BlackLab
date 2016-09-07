package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanQuery;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;

/**
 * Subclasses SpanMultiTermQueryWrapper so it correctly produces
 * BLSpanOrQuery or BLSpanTermQuery.
 * @param <Q> the type of query we're wrapping
 */
public class BLSpanMultiTermQueryWrapper<Q extends MultiTermQuery>
		extends SpanMultiTermQueryWrapper<Q> {

	public BLSpanMultiTermQueryWrapper(Q query) {
		super(query);
	}

	@Override
	public String getField() {
		// Return only base name of complex field!
		return ComplexFieldUtil.getBaseName(super.getField());
	}

	@Override
	public String toString(String field) {
		return "BL" + super.toString(field);
	}

	@Override
	public Query rewrite(IndexReader reader) throws IOException {
		Query q = super.rewrite(reader);
		if (!(q instanceof SpanQuery))
			throw new UnsupportedOperationException(
					"You can only use BLSpanMultiTermQueryWrapper with a suitable SpanRewriteMethod.");
		SpanQuery result = BLSpansWrapper.blSpanQueryFrom((SpanQuery) q);
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

}
