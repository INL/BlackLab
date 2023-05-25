package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * Filters hits from a producer query based on the hit positions of a filter
 * query. This allows us to do several things, such as: * find hits from the
 * producer that contain one or more hits from the filter * find hits from the
 * producer are contained by hit(s) from the filter * find hits from the
 * producer that start at the same position as a hit from the filter * find hits
 * from the producer that end at the same position as a hit from the filter
 */
public class SpanQueryPositionFilter extends BLSpanQueryAbstract {

    public static SpanGuarantees createGuarantees(SpanGuarantees producer) {
        return new SpanGuaranteesAdapter(producer) {
            @Override
            public boolean hitsStartPointSorted() {
                return true;
            }
        };
    }

    /** Filter operation to apply */
    final SpanQueryPositionFilter.Operation op;

    /** Return producer spans that DON'T match the filter instead? */
    final boolean invert;

    /** How to adjust the left edge of the producer hits while matching */
    int leftAdjust;

    /** How to adjust the right edge of the producer hits while matching */
    int rightAdjust;

    /**
     * Produce hits that match filter hits.
     *
     * @param producer hits we may be interested in
     * @param filter how we determine what producer hits we're interested in
     * @param op operation used to determine what producer hits we're interested in
     *            (containing, within, startsat, endsat)
     * @param invert produce hits that don't match filter instead?
     */
    public SpanQueryPositionFilter(BLSpanQuery producer, BLSpanQuery filter, SpanQueryPositionFilter.Operation op,
            boolean invert) {
        this(producer, filter, op, invert, 0, 0);
    }

    /**
     * Produce hits that match filter hits.
     *
     * @param producer hits we may be interested in
     * @param filter how we determine what producer hits we're interested in
     * @param op operation used to determine what producer hits we're interested in
     *            (containing, within, startsat, endsat)
     * @param invert produce hits that don't match filter instead?
     * @param leftAdjust how to adjust the left edge of the producer hits while
     *            matching
     * @param rightAdjust how to adjust the right edge of the producer hits while
     *            matching
     */
    public SpanQueryPositionFilter(BLSpanQuery producer, BLSpanQuery filter, SpanQueryPositionFilter.Operation op,
            boolean invert, int leftAdjust, int rightAdjust) {
        super(producer, filter);
        this.op = op;
        this.invert = invert;
        this.leftAdjust = leftAdjust;
        this.rightAdjust = rightAdjust;
        this.guarantees = createGuarantees(producer.guarantees());
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery producer = clauses.get(0).rewrite(reader);
        BLSpanQuery filter = clauses.get(1).rewrite(reader);

        if (!invert && op != SpanQueryPositionFilter.Operation.STARTS_AT
                && op != SpanQueryPositionFilter.Operation.ENDS_AT &&
                producer instanceof SpanQueryAnyToken) {
            // We're filtering "all n-grams of length min-max".
            // Use the special optimized SpanQueryFilterNGrams.
            SpanQueryAnyToken tp = (SpanQueryAnyToken) producer;
            return new SpanQueryFilterNGrams(filter, op, tp.guarantees().hitsLengthMin(),
                    tp.guarantees().hitsLengthMax(), leftAdjust, rightAdjust);
        }

        if (producer != clauses.get(0) || filter != clauses.get(1)) {
            SpanQueryPositionFilter result = new SpanQueryPositionFilter(producer, filter, op, invert);
            result.leftAdjust = leftAdjust;
            result.rightAdjust = rightAdjust;
            return result;
        }
        return this;
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        BLSpanWeight prodWeight = clauses.get(0).createWeight(searcher, scoreMode, boost);
        BLSpanWeight filterWeight = clauses.get(1).createWeight(searcher, scoreMode, boost);
        Map<Term, TermStates> contexts = scoreMode.needsScores() ? getTermStates(prodWeight, filterWeight) : null;
        return new SpanWeightPositionFilter(prodWeight, filterWeight, searcher, contexts, boost);
    }

    class SpanWeightPositionFilter extends BLSpanWeight {

        final BLSpanWeight prodWeight, filterWeight;

        public SpanWeightPositionFilter(BLSpanWeight prodWeight, BLSpanWeight filterWeight, IndexSearcher searcher,
                Map<Term, TermStates> terms, float boost) throws IOException {
            super(SpanQueryPositionFilter.this, searcher, terms, boost);
            this.prodWeight = prodWeight;
            this.filterWeight = filterWeight;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            prodWeight.extractTerms(terms);
            filterWeight.extractTerms(terms);
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return prodWeight.isCacheable(ctx) && filterWeight.isCacheable(ctx);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            prodWeight.extractTermStates(contexts);
            filterWeight.extractTermStates(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans spansProd = prodWeight.getSpans(context, requiredPostings);
            if (spansProd == null)
                return null;
            BLSpans spansFilter = filterWeight.getSpans(context, requiredPostings);
            if (spansFilter == null) {
                // No filter hits. If it's a positive filter, that means no producer hits can match.
                // If it's a negative filter, all producer hits match.
                return invert ? spansProd : null;
            }
            return new SpansPositionFilter(spansProd, spansFilter, op, invert, leftAdjust, rightAdjust);
        }
    }

    /** The different positional operations */
    public enum Operation {

        /** Producer hit contains filter hit */
        CONTAINING,

        /** Producer hit contained in filter hit */
        WITHIN,

        /** Producer hit starts at filter hit */
        STARTS_AT,

        /** Producer hit ends at filter hit */
        ENDS_AT,

        /** Producer hit exactly matches filter hit */
        MATCHES,

        /** Producer hit contains filter hit, at its end */
        CONTAINING_AT_START,

        /** Producer hit contains filter hit, at its start */
        CONTAINING_AT_END

    }

    @Override
    public String toString(String field) {
        String not = invert ? "NOT" : "";
        String adj = (leftAdjust != 0 || rightAdjust != 0 ? ", " + leftAdjust + ", " + rightAdjust : "");
        return "POSFILTER(" + clausesToString(field) + ", " + not + op + adj + ")";
    }

    public SpanQueryPositionFilter copy() {
        return new SpanQueryPositionFilter(clauses.get(0), clauses.get(1), op, invert, leftAdjust, rightAdjust);
    }

    /**
     * Adjust the left edge of the producer hits for matching only.
     *
     * That is, the original producer hit is returned, not the adjusted one.
     *
     * @param delta how to adjust the edge
     */
    public void adjustLeft(int delta) {
        leftAdjust += delta;
    }

    /**
     * Adjust the right edge of the producer hits for matching only.
     *
     * That is, the original producer hit is returned, not the adjusted one.
     *
     * @param delta how to adjust the edge
     */
    public void adjustRight(int delta) {
        rightAdjust += delta;
    }

    @Override
    public boolean matchesEmptySequence() {
        return clauses.get(0).matchesEmptySequence();
    }

    @Override
    public BLSpanQuery noEmpty() {
        return new SpanQueryPositionFilter(clauses.get(0).noEmpty(), clauses.get(1), op, invert, leftAdjust,
                rightAdjust);
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return clauses.get(0).reverseMatchingCost(reader);
    }

    @Override
    public int forwardMatchingCost() {
        return clauses.get(0).forwardMatchingCost();
    }

    @Override
    public boolean canInternalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        return clause.guarantees().hitsAllSameLength();
    }

    @Override
    public BLSpanQuery internalizeNeighbour(BLSpanQuery clause, boolean addToRight) {
        if (!clause.guarantees().hitsAllSameLength())
            throw new BlackLabRuntimeException("Trying to internalize non-constant-length clause: " + clause);
        // Create a new position filter query with a constant-length clause added to our producer.
        // leftAdjust and rightAdjust are updated according to the clause's length, so it is not
        // actually filtered.
        BLSpanQuery producer = clauses.get(0);
        SpanQuerySequence seq = SpanQuerySequence.sequenceInternalize(producer, clause, addToRight);
        if (addToRight)
            return new SpanQueryPositionFilter(seq, clauses.get(1), op, invert, leftAdjust,
                    rightAdjust - clause.guarantees().hitsLengthMin());
        return new SpanQueryPositionFilter(seq, clauses.get(1), op, invert,
                leftAdjust + clause.guarantees().hitsLengthMin(), rightAdjust);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (invert ? 1231 : 1237);
        result = prime * result + leftAdjust;
        result = prime * result + ((op == null) ? 0 : op.hashCode());
        result = prime * result + rightAdjust;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        SpanQueryPositionFilter other = (SpanQueryPositionFilter) obj;
        if (invert != other.invert)
            return false;
        if (leftAdjust != other.leftAdjust)
            return false;
        if (op != other.op)
            return false;
        if (rightAdjust != other.rightAdjust)
            return false;
        return true;
    }
}
