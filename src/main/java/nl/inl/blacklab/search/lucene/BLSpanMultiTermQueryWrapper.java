package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;

/**
 * Subclasses SpanMultiTermQueryWrapper so it correctly produces
 * BLSpanOrQuery or BLSpanTermQuery.
 * @param <Q> the type of query we're wrapping
 */
public class BLSpanMultiTermQueryWrapper<Q extends MultiTermQuery> extends
		SpanMultiTermQueryWrapper<Q> {

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
		Query q = query.rewrite(reader);
		if (!(q instanceof SpanQuery))
			throw new UnsupportedOperationException(
					"You can only use BLSpanMultiTermQueryWrapper with a suitable SpanRewriteMethod.");
		if (q instanceof SpanOrQuery) {
			q = BLSpanOrQuery.from((SpanOrQuery) q);
		} else if (q instanceof SpanTermQuery) {
			q = BLSpanTermQuery.from((SpanTermQuery) q);
		} else {
			throw new RuntimeException("SpanMultiTermQueryWrapper returned a " + q.getClass().getName() + ", cannot BL-ify");
		}
		return q;
	}

}
