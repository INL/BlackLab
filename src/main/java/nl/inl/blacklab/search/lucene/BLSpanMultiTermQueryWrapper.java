package nl.inl.blacklab.search.lucene;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * BL-specific version to use BLSpanTermQuery instead of SpanTermQuery,
 * and changed getField() to return base name of complex field.
 *
 * Changes are marked with "// BL"
 */

import java.io.IOException;
import java.util.Map;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoringRewrite;
import org.apache.lucene.search.TopTermsRewrite;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;

/**
 * Wraps any {@link MultiTermQuery} as a {@link SpanQuery},
 * so it can be nested within other SpanQuery classes.
 * <p>
 * The query is rewritten by default to a {@link BLSpanOrQuery} containing
 * the expanded terms, but this can be customized.
 * <p>
 * Example:
 * <blockquote><pre class="prettyprint">
 * {@code
 * WildcardQuery wildcard = new WildcardQuery(new Term("field", "bro?n"));
 * SpanQuery spanWildcard = new BLSpanMultiTermQueryWrapper<WildcardQuery>(wildcard);
 * // do something with spanWildcard, such as use it in a SpanFirstQuery
 * }
 * </pre></blockquote>
 */
@SuppressWarnings("javadoc") // BL: ignore missing javadoc tags
public class BLSpanMultiTermQueryWrapper<Q extends MultiTermQuery> extends SpanQuery {
  protected final Q query;

  /**
   * Create a new BLSpanMultiTermQueryWrapper.
   *
   * @param query Query to wrap.
   * <p>
   * NOTE: This will call {@link MultiTermQuery#setRewriteMethod(MultiTermQuery.RewriteMethod)}
   * on the wrapped <code>query</code>, changing its rewrite method to a suitable one for spans.
   * Be sure to not change the rewrite method on the wrapped query afterwards! Doing so will
   * throw {@link UnsupportedOperationException} on rewriting this query!
   */
  @SuppressWarnings({"rawtypes"}) // BL: removed "unchecked", not necessary
  public BLSpanMultiTermQueryWrapper(Q query) {
    this.query = query;

    MultiTermQuery.RewriteMethod method = query.getRewriteMethod();
    if (method instanceof TopTermsRewrite) {
      final int pqsize = ((TopTermsRewrite) method).getSize();
      setRewriteMethod(new TopTermsSpanBooleanQueryRewrite(pqsize));
    } else {
      setRewriteMethod(SCORING_SPAN_QUERY_REWRITE);
    }
  }

  /**
   * Expert: returns the rewriteMethod
   */
  public final SpanRewriteMethod getRewriteMethod() {
    final MultiTermQuery.RewriteMethod m = query.getRewriteMethod();
    if (!(m instanceof SpanRewriteMethod))
      throw new UnsupportedOperationException("You can only use BLSpanMultiTermQueryWrapper with a suitable SpanRewriteMethod.");
    return (SpanRewriteMethod) m;
  }

  /**
   * Expert: sets the rewrite method. This only makes sense
   * to be a span rewrite method.
   */
  public final void setRewriteMethod(SpanRewriteMethod rewriteMethod) {
    query.setRewriteMethod(rewriteMethod);
  }

  @Override
  public Spans getSpans(AtomicReaderContext context, Bits acceptDocs, Map<Term,TermContext> termContexts) throws IOException {
    throw new UnsupportedOperationException("Query should have been rewritten");
  }

  @Override
  public String getField() {
	  // BL: return only base name of complex field!
    return ComplexFieldUtil.getBaseName(query.getField());
  }

  @Override
  public String toString(String field) {
    StringBuilder builder = new StringBuilder();
    builder.append("BLSpanMultiTermQueryWrapper(");
    builder.append(query.toString(field));
    builder.append(")");
    return builder.toString();
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    final Query q = query.rewrite(reader);
    if (!(q instanceof SpanQuery))
      throw new UnsupportedOperationException("You can only use BLSpanMultiTermQueryWrapper with a suitable SpanRewriteMethod.");
    return q;
  }

  @Override
  public int hashCode() {
    return 31 * query.hashCode();
  }

  @Override
  @SuppressWarnings({"rawtypes"}) // BL: removed "unchecked", not necessary
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    final BLSpanMultiTermQueryWrapper other = (BLSpanMultiTermQueryWrapper) obj;
    return query.equals(other.query);
  }

  /** Abstract class that defines how the query is rewritten. */
  public static abstract class SpanRewriteMethod extends MultiTermQuery.RewriteMethod {
    @Override
    public abstract SpanQuery rewrite(IndexReader reader, MultiTermQuery query) throws IOException;
  }

  // BL: in the two following SpanRewriteMethods, SpanOrQuery was replaced with BLSpanOrQuery.

  /**
   * A rewrite method that first translates each term into a SpanTermQuery in a
   * {@link Occur#SHOULD} clause in a BooleanQuery, and keeps the
   * scores as computed by the query.
   *
   * @see #setRewriteMethod
   */
  public final static SpanRewriteMethod SCORING_SPAN_QUERY_REWRITE = new SpanRewriteMethod() {
    private final ScoringRewrite<BLSpanOrQuery> delegate = new ScoringRewrite<BLSpanOrQuery>() {
      @Override
      protected BLSpanOrQuery getTopLevelQuery() {
        return new BLSpanOrQuery();
      }

      @Override
      protected void checkMaxClauseCount(int count) {
        // we accept all terms as BLSpanOrQuery has no limits
      }

      @Override
      protected void addClause(BLSpanOrQuery topLevel, Term term, int docCount, float boost, TermContext states) {
        // TODO: would be nice to not lose term-state here.
        // we could add a hack option to BLSpanOrQuery, but the hack would only work if this is the top-level Span
        // (if you put this thing in another span query, it would extractTerms/double-seek anyway)
		final BLSpanTermQuery q = new BLSpanTermQuery(term); // BL was: SpanTermQuery
        q.setBoost(boost);
        topLevel.addClause(q);
      }
    };

    @Override
    public SpanQuery rewrite(IndexReader reader, MultiTermQuery query) throws IOException {
      BLSpanOrQuery rewritten = delegate.rewrite(reader, query);
      if (rewritten.getField() == null)
    	  rewritten.setField(query.getField()); // rewritten to or query without clauses
      return rewritten;
    }
  };

  /**
   * A rewrite method that first translates each term into a SpanTermQuery in a
   * {@link Occur#SHOULD} clause in a BooleanQuery, and keeps the
   * scores as computed by the query.
   *
   * <p>
   * This rewrite method only uses the top scoring terms so it will not overflow
   * the boolean max clause count.
   *
   * @see #setRewriteMethod
   */
  public static final class TopTermsSpanBooleanQueryRewrite extends SpanRewriteMethod  {
    private final TopTermsRewrite<BLSpanOrQuery> delegate;

    /**
     * Create a TopTermsSpanBooleanQueryRewrite for
     * at most <code>size</code> terms.
     */
    public TopTermsSpanBooleanQueryRewrite(int size) {
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
        protected void addClause(BLSpanOrQuery topLevel, Term term, int docFreq, float boost, TermContext states) {
          final BLSpanTermQuery q = new BLSpanTermQuery(term); // BL was: SpanTermQuery
          q.setBoost(boost);
          topLevel.addClause(q);
        }
      };
    }

    /** return the maximum priority queue size */
    public int getSize() {
      return delegate.getSize();
    }

    @Override
    public SpanQuery rewrite(IndexReader reader, MultiTermQuery query) throws IOException {
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
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      final TopTermsSpanBooleanQueryRewrite other = (TopTermsSpanBooleanQueryRewrite) obj;
      return delegate.equals(other.delegate);
    }

  }

}
