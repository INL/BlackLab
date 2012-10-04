/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
/**
 *
 */
package nl.inl.blacklab.search.lucene;

///**
// * Uses BlackLabSpanTermQuery objects instead of the default Lucene ones because we need to override
// * getField() to only return the base field name, not the complete field name with the property.
// */
//public class BLSpanRegexQuery extends BLSpanMultiTermQueryWrapper<RegexQuery> implements
//		RegexQueryCapable {
//
//	// Make sure rewriting created BlackLabSpanTermQuery instances!
//
//	/**
//	 * A rewrite method that first translates each term into a SpanTermQuery in a
//	 * {@link Occur#SHOULD} clause in a BooleanQuery, and keeps the scores as computed by the query.
//	 *
//	 * @see #setRewriteMethod
//	 */
//	public final static SpanRewriteMethod BL_SCORING_SPAN_QUERY_REWRITE = new SpanRewriteMethod() {
//		private final ScoringRewrite<SpanOrQuery> delegate = new ScoringRewrite<SpanOrQuery>() {
//			@Override
//			protected SpanOrQuery getTopLevelQuery() {
//				return new SpanOrQuery();
//			}
//
//			@Override
//			protected void addClause(SpanOrQuery topLevel, Term term, float boost) {
//				final SpanTermQuery q = new BLSpanTermQuery(term);
//				q.setBoost(boost);
//				topLevel.addClause(q);
//			}
//		};
//
//		@Override
//		public SpanQuery rewrite(IndexReader reader, MultiTermQuery query) throws IOException {
//			return delegate.rewrite(reader, query);
//		}
//
//		// Make sure we are still a singleton even after deserializing
//		protected Object readResolve() {
//			return BL_SCORING_SPAN_QUERY_REWRITE;
//		}
//	};
//
//	// Make sure rewriting created BlackLabSpanTermQuery instances!
//
//	/**
//	 * A rewrite method that first translates each term into a SpanTermQuery in a
//	 * {@link Occur#SHOULD} clause in a BooleanQuery, and keeps the scores as computed by the query.
//	 *
//	 * <p>
//	 * This rewrite method only uses the top scoring terms so it will not overflow the boolean max
//	 * clause count.
//	 *
//	 * @see #setRewriteMethod
//	 */
//	public static final class BLTopTermsSpanBooleanQueryRewrite extends SpanRewriteMethod {
//		private final TopTermsRewrite<SpanOrQuery> delegate;
//
//		/**
//		 * Create a TopTermsSpanBooleanQueryRewrite for at most <code>size</code> terms.
//		 */
//		public BLTopTermsSpanBooleanQueryRewrite(int size) {
//			delegate = new TopTermsRewrite<SpanOrQuery>(size) {
//				@Override
//				protected int getMaxSize() {
//					return Integer.MAX_VALUE;
//				}
//
//				@Override
//				protected SpanOrQuery getTopLevelQuery() {
//					return new SpanOrQuery();
//				}
//
//				@Override
//				protected void addClause(SpanOrQuery topLevel, Term term, float boost) {
//					final SpanTermQuery q = new BLSpanTermQuery(term);
//					q.setBoost(boost);
//					topLevel.addClause(q);
//				}
//			};
//		}
//
//		/** return the maximum priority queue size */
//		public int getSize() {
//			return delegate.getSize();
//		}
//
//		@Override
//		public SpanQuery rewrite(IndexReader reader, MultiTermQuery query) throws IOException {
//			return delegate.rewrite(reader, query);
//		}
//
//		@Override
//		public int hashCode() {
//			return 31 * delegate.hashCode();
//		}
//
//		@Override
//		public boolean equals(Object obj) {
//			if (this == obj)
//				return true;
//			if (obj == null)
//				return false;
//			if (getClass() != obj.getClass())
//				return false;
//			final BLTopTermsSpanBooleanQueryRewrite other = (BLTopTermsSpanBooleanQueryRewrite) obj;
//			return delegate.equals(other.delegate);
//		}
//
//	}
//
//	//private final RegexCapabilities regexImpl = new JavaUtilRegexCapabilities();
//
//	public BLSpanRegexQuery(Term term) {
//		super(new RegexQuery(term));
//
//		// Make sure rewriting created BlackLabSpanTermQuery instances!
//		MultiTermQuery.RewriteMethod method = getRewriteMethod();
//		if (method instanceof TopTermsSpanBooleanQueryRewrite) {
//			final int pqsize = ((TopTermsSpanBooleanQueryRewrite) method).getSize();
//			setRewriteMethod(new BLTopTermsSpanBooleanQueryRewrite(pqsize));
//		} else {
//			setRewriteMethod(BL_SCORING_SPAN_QUERY_REWRITE);
//		}
//
//	}
//
//	public Term getTerm() {
//		return query.getTerm();
//	}
//
//	/**
//	 * Overriding getField to return only part of the field name. The part before the double
//	 * underscore will be returned. This is necessary because BlackLab uses the field property in a
//	 * somewhat different manner: on top of a simply 'content' field there may also be
//	 * 'content__headword' and 'content__pos'.
//	 *
//	 * This makes it possible to use regexqueries with different fieldnames in the same AND or OR
//	 * query. To ensure Lucence does not object to this, we need to generalise the field name to the
//	 * common descriptor - which is the part before the double underscore.
//	 *
//	 * @return String field
//	 */
//	@Override
//	public String getField() {
//		return ComplexFieldUtil.getBaseName(getTerm().field());
//	}
//
//	@Override
//	public void setRegexImplementation(RegexCapabilities impl) {
//		query.setRegexImplementation(impl);
//	}
//
//	@Override
//	public RegexCapabilities getRegexImplementation() {
//		return query.getRegexImplementation();
//	}
//}

