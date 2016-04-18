package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoringRewrite;
import org.apache.lucene.search.TopTermsRewrite;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;

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
		MultiTermQuery.RewriteMethod method = query.getRewriteMethod();
		if (method instanceof TopTermsSpanBooleanQueryRewrite) {
			final int pqsize = ((TopTermsSpanBooleanQueryRewrite) method)
					.getSize();
			setRewriteMethod(new BLTopTermsSpanBooleanQueryRewrite(pqsize));
		} else {
			setRewriteMethod(BL_SCORING_SPAN_QUERY_REWRITE);
		}
	}

	/**
	 * A rewrite method that first translates each term into a SpanTermQuery in a
	 * Occur#SHOULD clause in a BooleanQuery, and keeps the
	 * scores as computed by the query.
	 *
	 * @see #setRewriteMethod
	 */
	public final static SpanRewriteMethod BL_SCORING_SPAN_QUERY_REWRITE = new SpanRewriteMethod() {
		private final ScoringRewrite<BLSpanOrQuery> delegate = new ScoringRewrite<BLSpanOrQuery>() {
			@Override
			protected BLSpanOrQuery getTopLevelQuery() {
				return new BLSpanOrQuery();
			}

			@Override
			protected void checkMaxClauseCount(int count) {
				// we accept all terms as SpanOrQuery has no limits
			}

			@Override
			protected void addClause(BLSpanOrQuery topLevel, Term term,
					int docCount, float boost, TermContext states) {
				// TODO: would be nice to not lose term-state here.
				// we could add a hack option to SpanOrQuery, but the hack would only work if this is the top-level Span
				// (if you put this thing in another span query, it would extractTerms/double-seek anyway)
				final BLSpanTermQuery q = new BLSpanTermQuery(term);
				q.setBoost(boost);
				topLevel.addClause(q);
			}
		};

		@Override
		public SpanQuery rewrite(IndexReader reader, MultiTermQuery query)
				throws IOException {
			BLSpanOrQuery rewritten = delegate.rewrite(reader, query);
			if (rewritten.getField() == null)
		    	  rewritten.setField(query.getField()); // rewritten to or query without clauses
			return rewritten;
		}
	};

	/**
	 * A rewrite method that first translates each term into a SpanTermQuery in a
	 * Occur#SHOULD clause in a BooleanQuery, and keeps the
	 * scores as computed by the query.
	 *
	 * <p>
	 * This rewrite method only uses the top scoring terms so it will not overflow
	 * the boolean max clause count.
	 *
	 * @see #setRewriteMethod
	 */
	public static final class BLTopTermsSpanBooleanQueryRewrite
			extends SpanRewriteMethod {

		private final TopTermsRewrite<BLSpanOrQuery> delegate;

		/**
		 * Create a TopTermsSpanBooleanQueryRewrite for
		 * at most <code>size</code> terms.
		 * @param size the maximum priority queue size
		 */
		public BLTopTermsSpanBooleanQueryRewrite(int size) {
			delegate = new TopTermsRewrite<BLSpanOrQuery>(size) {
				@Override
				protected int getMaxSize() {
					return Integer.MAX_VALUE;
				}

				@Override
				protected BLSpanOrQuery getTopLevelQuery() {
					return new BLSpanOrQuery();
				}

				@Override
				protected void addClause(BLSpanOrQuery topLevel, Term term,
						int docFreq, float boost, TermContext states) {
					final BLSpanTermQuery q = new BLSpanTermQuery(term);
					q.setBoost(boost);
					topLevel.addClause(q);
				}
			};
		}

		/** return the maximum priority queue size
		 * @return the size */
		public int getSize() {
			return delegate.getSize();
		}

		@Override
		public SpanQuery rewrite(IndexReader reader, MultiTermQuery query)
				throws IOException {
			BLSpanOrQuery rewritten = delegate.rewrite(reader, query);
			if (rewritten.getField() == null)
		    	  rewritten.setField(query.getField()); // rewritten to or query without clauses
			return rewritten;
		}

		@Override
		public int hashCode() {
			return 31 * delegate.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			final BLTopTermsSpanBooleanQueryRewrite other = (BLTopTermsSpanBooleanQueryRewrite) obj;
			return delegate.equals(other.delegate);
		}

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
			throw new RuntimeException("SpanMultiTermQueryWrapper returned a "
					+ q.getClass().getName() + ", cannot BL-ify");
		}
		return q;
	}

}
