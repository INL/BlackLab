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
package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.requestlogging.LogLevel;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.Nfa;
import nl.inl.blacklab.search.fimatch.NfaTwoWay;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A required interface for a BlackLab SpanQuery. All our queries must be
 * derived from this so we know they will produce BLSpans (which contains extra
 * methods for optimization).
 */
public abstract class BLSpanQuery extends SpanQuery {

    public static final int MAX_UNLIMITED = Integer.MAX_VALUE;

    public BLSpanQuery(QueryInfo queryInfo) {
        this.queryInfo = queryInfo;
    }
    
    /**
     * Rewrite a SpanQuery after rewrite() to a BLSpanQuery equivalent.
     *
     * This is used for BLSpanOrQuery and BLSpanMultiTermQueryWrapper: we let Lucene
     * rewrite these for us, but the result needs to be BL-ified so we know we'll
     * get BLSpans (which contain extra methods for optimization).
     *
     * @param spanQuery the SpanQuery to BL-ify (if it isn't a BLSpanQuery already)
     * @return resulting BLSpanQuery, or the input query if it was one already
     */
    public static BLSpanQuery wrap(QueryInfo queryInfo, SpanQuery spanQuery) {
        if (spanQuery instanceof BLSpanQuery) {
            // Already BL-derived, no wrapper needed.
            return (BLSpanQuery) spanQuery;
        } else if (spanQuery instanceof SpanOrQuery) {
            // Translate to a BLSpanOrQuery, recursively translating the clauses.
            return BLSpanOrQuery.from(queryInfo, (SpanOrQuery) spanQuery);
        } else if (spanQuery instanceof SpanTermQuery) {
            // Translate to a BLSpanTermQuery.
            return BLSpanTermQuery.from(queryInfo, (SpanTermQuery) spanQuery);
        } else {
            // After rewrite, we shouldn't encounter any other non-BLSpanQuery classes.
            throw new UnsupportedOperationException("Cannot BL-ify " + spanQuery.getClass().getSimpleName());
        }
    }

    /**
     * Add two values for maximum number of repetitions, taking "infinite" into
     * account.
     *
     * -1 or Integer.MAX_VALUE means infinite. Adding infinite to any other value
     * produces infinite again (-1 if either value is -1; otherwise,
     * Integer.MAX_VALUE if either value is Integer.MAX_VALUE).
     *
     * @param a first max. repetitions value
     * @param b first max. repetitions value
     * @return sum of the max. repetitions values
     */
    public static int addMaxValues(int a, int b) {
        if (a < 0 || b < 0)
            throw new BlackLabRuntimeException(
                    "max values cannot be negative (possible use of old -1 == max, now BLSpanQuery.MAX_UNLIMITED)");
        // Is either value infinite?
        if (a == Integer.MAX_VALUE || b == Integer.MAX_VALUE)
            return Integer.MAX_VALUE; // Yes, result is infinite
        // Add regular values
        return a + b;
    }

    static <T extends SpanQuery> String clausesToString(String field, List<T> clauses) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (T clause : clauses) {
            if (b.length() > 0)
                b.append(", ");
            b.append(clause.toString(field));
            n++;
            if (n > 100) {
                b.append("...");
                break;
            }
        }
        return b.toString();
    }

    @SafeVarargs
    static <T extends SpanQuery> String clausesToString(String field, T... clauses) {
        return clausesToString(field, Arrays.asList(clauses));
    }

    public static BLSpanQuery ensureSortedUnique(BLSpanQuery spanQuery) {
        if (spanQuery.hitsStartPointSorted()) {
            if (spanQuery.hitsAreUnique())
                return spanQuery;
            return new SpanQueryUnique(spanQuery);
        }
        return new SpanQuerySorted(spanQuery, false, !spanQuery.hitsAreUnique());
    }

    public static BLSpanQuery ensureSorted(BLSpanQuery spanQuery) {
        if (spanQuery.hitsStartPointSorted()) {
            return spanQuery;
        }
        return new SpanQuerySorted(spanQuery, false, false);
    }

    public static String inf(int max) {
        return max == MAX_UNLIMITED ? "INF" : Integer.toString(max);
    }

    /** Information such as our index, our search logger, etc. */
    protected QueryInfo queryInfo;

    @Override
    public abstract String toString(String field);

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

    /**
     * Called before rewrite() to optimize certain parts of the query before they
     * are rewritten (e.g. match regex terms using NFA instead of OR).
     *
     * For now, only SpanQuerySequence overrides this to make sure certain clause
     * combinations are performed before rewrite().
     *
     * @param reader index reader
     * @return optimized query
     * @throws IOException on error
     */
    public BLSpanQuery optimize(IndexReader reader) throws IOException {
        return this; // by default, don't do any optimization
    }

    @Override
    public abstract BLSpanQuery rewrite(IndexReader reader) throws IOException;

    @Override
    public abstract BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException;

    /**
     * Does this query match the empty sequence?
     *
     * For example, the query [word="cow"]* matches the empty sequence. We need to
     * know this so we can rewrite to the appropriate queries. A query of the form
     * "AB*" would be translated into "A|AB+", so each component of the query
     * actually generates non-empty matches.
     *
     * We default to no because most queries don't match the empty sequence.
     *
     * @return true if this query matches the empty sequence, false otherwise
     */
    public boolean matchesEmptySequence() {
        return false;
    }

    /**
     * Return a version of this clause that cannot match the empty sequence.
     * 
     * @return a version that doesn't match the empty sequence
     */
    BLSpanQuery noEmpty() {
        if (!matchesEmptySequence())
            return this;
        throw new UnsupportedOperationException("noEmpty() must be implemented!");
    }

    /**
     * Return an inverted version of this query.
     *
     * @return the inverted query
     */
    public BLSpanQuery inverted() {
        return new SpanQueryNot(this);
    }

    /**
     * Is it okay to invert this query for optimization?
     *
     * Heuristic used to determine when to optimize a query by inverting one or more
     * of its subqueries.
     *
     * @return true if it is, false if not
     */
    boolean okayToInvertForOptimization() {
        return false;
    }

    /**
     * Is this query only a negative clause, producing all tokens that don't satisfy
     * certain conditions?
     *
     * Used for optimization decisions, i.e. in BLSpanOrQuery.rewrite().
     *
     * @return true if it's negative-only, false if not
     */
    public boolean isSingleTokenNot() {
        return false;
    }

    /**
     * Are all our hits single tokens?
     * 
     * @return true if they are, false if not
     */
    public boolean producesSingleTokens() {
        return hitsAllSameLength() && hitsLengthMin() == 1;
    }

    /**
     * Do our hits have constant length?
     * 
     * @return true if they do, false if not
     */
    public abstract boolean hitsAllSameLength();

    /**
     * How long could our shortest hit be?
     * 
     * @return length of the shortest hit possible
     */
    public abstract int hitsLengthMin();

    /**
     * How long could our longest hit be?
     * 
     * @return length of the longest hit possible, or Integer.MAX_VALUE if unlimited
     */
    public abstract int hitsLengthMax();

    /**
     * When hit B follows hit A, is it guaranteed that B.end &gt;= A.end? Also, if
     * A.end == B.end, is B.start &gt; A.start?
     *
     * @return true if this is guaranteed, false if not
     */
    public abstract boolean hitsEndPointSorted();

    /**
     * When hit B follows hit A, is it guaranteed that B.start &gt;= A.start? Also,
     * if A.start == B.start, is B.end &gt; A.end?
     *
     * @return true if this is guaranteed, false if not
     */
    public abstract boolean hitsStartPointSorted();

    /**
     * Is it guaranteed that no two hits have the same start position?
     * 
     * @return true if this is guaranteed, false if not
     */
    public abstract boolean hitsHaveUniqueStart();

    /**
     * Is it guaranteed that no two hits have the same end position?
     * 
     * @return true if this is guaranteed, false if not
     */
    public abstract boolean hitsHaveUniqueEnd();

    /**
     * Is it guaranteed that no two hits have the same start and end position?
     * 
     * @return true if this is guaranteed, false if not
     */
    public abstract boolean hitsAreUnique();

    /**
     * Is this query a single "any token", e.g. one that matches all individual tokens?
     * @return true if it is, false if not
     */
    public boolean isSingleAnyToken() {
        return false;
    }

    /**
     * Can this query "internalize" the given neighbouring clause?
     *
     * Internalizing means adding the clause to its children, which is often more
     * efficient because we create longer sequences that match fewer hits and may
     * themselves be further optimized. An example is SpanQueryPosFilter, which can
     * be combined with fixed-length neighbouring clauses (updating the
     * SpanQueryPosFilters' left or right adjustment setting to match) to reduce the
     * number of hits that have to be filter.
     *
     * @param clause clause we want to internalize
     * @param onTheRight if true, clause is a right neighbour of this query; if
     *            false, a left neighbour
     * @return true iff clause can be internalized
     */
    public boolean canInternalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        return false;
    }

    /**
     * Internalize the given clause.
     *
     * See canInternalizeNeighbour() for more information.
     *
     * @param clause clause we want to internalize
     * @param onTheRight if true, clause is a right neighbour of this query; if
     *            false, a left neighbour
     * @return new query with clause internalized
     */
    public BLSpanQuery internalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        throw new UnsupportedOperationException(
                "Neighbouring clause " + clause + " can not be internalized by " + this);
    }

    public Nfa getNfa(ForwardIndexAccessor fiAccessor, int direction) {
        throw new UnsupportedOperationException(
                "Cannot create NFA; query should have been rewritten or cannot be matched using forward index");
    }

    public boolean canMakeNfa() {
        return false;
    }

    public NfaTwoWay getNfaTwoWay(ForwardIndexAccessor fiAccessor, int nativeDirection) {
        Nfa nfa = getNfa(fiAccessor, nativeDirection);
        Nfa nfaRev = getNfa(fiAccessor, -nativeDirection);
        return new NfaTwoWay(nfa, nfaRev);
    }

    /**
     * Return an (very rough) indication of how many hits this clause might return.
     *
     * Used to decide what parts of the query to match using the forward index.
     *
     * Based on term frequency, which are combined using simple rules of thumb.
     *
     * Another way to think of this is an indication of how much computation this
     * clause will require when matching using the reverse index.
     *
     * @param reader the index reader
     *
     * @return rough estimation of the number of hits
     */
    public abstract long reverseMatchingCost(IndexReader reader);

    /**
     * Return an (very rough) indication of how expensive finding a match for this
     * query using an NFA would be.
     *
     * Used to decide what parts of the query to match using the forward index.
     *
     * @return rough estimation of the NFA complexity
     */
    public abstract int forwardMatchingCost();

    @Override
    public String getField() {
        // Return only base name of annotated field!
        return AnnotatedFieldNameUtil.getBaseName(getRealField());
    }

    public abstract String getRealField();
    
    public void setQueryInfo(QueryInfo queryInfo) {
        this.queryInfo = queryInfo;
    }
    
    public void log(LogLevel level, String msg) {
        if (queryInfo != null)
            queryInfo.log(level, msg);
    }

}
