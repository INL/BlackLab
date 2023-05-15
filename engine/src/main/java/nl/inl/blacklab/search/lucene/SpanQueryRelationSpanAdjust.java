package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Adjust relation spans to match source, target, or entire relation.
 */
public class SpanQueryRelationSpanAdjust extends BLSpanQuery {

    public static SpanGuarantees createGuarantees(SpanGuarantees clause, MatchInfo.SpanMode mode) {
        // NOTE: we don't know the direction here, so choose the one that gives the fewest guarantees
        //   (maybe SpanGuarantees could have a guarantee to help with this? Or maybe not worth it just for relations)
        SpanQueryRelations.Direction direction = SpanQueryRelations.Direction.BOTH_DIRECTIONS;
        return SpanQueryRelations.createGuarantees(clause, direction, mode);
    }

    private final BLSpanQuery clause;

    private final MatchInfo.SpanMode mode;

    public SpanQueryRelationSpanAdjust(BLSpanQuery clause, MatchInfo.SpanMode mode) {
        super(clause.queryInfo);
        this.clause = clause;
        this.mode = mode;

        this.guarantees = createGuarantees(clause.guarantees(), mode);
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery rewritten = clause.rewrite(reader);
        if (rewritten == clause)
            return this;
        return new SpanQueryRelationSpanAdjust(rewritten, mode);
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getField())) {
            clause.visit(visitor.getSubVisitor(Occur.MUST, this));
        }
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        BLSpanWeight weight = clause.createWeight(searcher, scoreMode, boost);
        return new Weight(weight, searcher, scoreMode.needsScores() ? getTermStates(weight) : null, boost);
    }

    class Weight extends BLSpanWeight {

        final BLSpanWeight weight;

        public Weight(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermStates> terms, float boost)
                throws IOException {
            super(SpanQueryRelationSpanAdjust.this, searcher, terms, boost);
            this.weight = weight;
        }

        @Override
        @Deprecated
        public void extractTerms(Set<Term> terms) {
            weight.extractTerms(terms);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            weight.extractTermStates(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans spans = weight.getSpans(context, requiredPostings);
            if (spans == null)
                return null;
            return new SpansRelationSpanAdjust(spans, mode);
        }
    }

    @Override
    public String toString(String field) {
        return "RSPAN(" + clause + ", " + mode + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SpanQueryRelationSpanAdjust that = (SpanQueryRelationSpanAdjust) o;
        return clause.equals(that.clause) && mode == that.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(clause, mode);
    }

    /**
     * Returns the name of the search field. In the case of a annotated field, the
     * clauses may actually query different annotations of the same annotated field
     * (e.g. "description" and "description__pos"). That's why only the prefix is
     * returned.
     *
     * @return name of the search field
     */
    @Override
    public String getField() {
        return clause.getField();
    }

    @Override
    public String getRealField() {
        return clause.getRealField();
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return clause.reverseMatchingCost(reader);
    }

    @Override
    public int forwardMatchingCost() {
        return clause.forwardMatchingCost();
    }

    @Override
    public void setQueryInfo(QueryInfo queryInfo) {
        super.setQueryInfo(queryInfo);
        clause.setQueryInfo(queryInfo);
    }
}
