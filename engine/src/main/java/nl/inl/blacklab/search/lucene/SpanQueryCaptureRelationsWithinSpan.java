package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Captures all relations within a match info span.
 *
 * Note that the match info spans we're trying to capture relations in
 * may not overlap! If they do overlap, some relations may be skipped over.
 */
public class SpanQueryCaptureRelationsWithinSpan extends BLSpanQueryAbstract {

    /** Spans to capture relations from */
    final String toCapture;

    /** Match info name for the captured relations */
    final String captureAs;

    public SpanQueryCaptureRelationsWithinSpan(QueryInfo queryInfo, String relationFieldName, BLSpanQuery query, String toCapture, String captureAs, String relationType) {
        super(query, new SpanQueryRelations(queryInfo, relationFieldName, relationType, Collections.emptyMap(), SpanQueryRelations.Direction.BOTH_DIRECTIONS, RelationInfo.SpanMode.FULL_SPAN));
        this.toCapture = toCapture;
        this.captureAs = captureAs;
        this.guarantees = query.guarantees();
    }

    public SpanQueryCaptureRelationsWithinSpan(BLSpanQuery query, BLSpanQuery relations, String toCapture, String captureAs) {
        super(query, relations);
        this.toCapture = toCapture;
        this.captureAs = captureAs;
        this.guarantees = query.guarantees();
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        List<BLSpanQuery> rewritten = rewriteClauses(reader);
        return rewritten == null ? this : new SpanQueryCaptureRelationsWithinSpan(rewritten.get(0), rewritten.get(1), toCapture, captureAs);
    }

    @Override
    public boolean matchesEmptySequence() {
        return clauses.get(0).matchesEmptySequence();
    }

    @Override
    public BLSpanQuery noEmpty() {
        if (!matchesEmptySequence())
            return this;
        return new SpanQueryCaptureRelationsWithinSpan(clauses.get(0).noEmpty(), clauses.get(1).noEmpty(), toCapture, captureAs);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        BLSpanWeight weight = clauses.get(0).createWeight(searcher, scoreMode, boost);
        SpanQueryRelations.Weight relationsWeight = ((SpanQueryRelations)clauses.get(1)).createWeight(searcher, scoreMode, boost);
        return new Weight(weight, relationsWeight, searcher, scoreMode.needsScores() ? getTermStates(weight) : null, boost);
    }

    public BLSpanQuery getClause() {
        return clauses.get(0);
    }

    class Weight extends BLSpanWeight {

        final BLSpanWeight weight;

        private final SpanQueryRelations.Weight relationsWeight;

        public Weight(BLSpanWeight weight, SpanQueryRelations.Weight relationsWeight, IndexSearcher searcher, Map<Term, TermStates> terms, float boost)
                throws IOException {
            super(SpanQueryCaptureRelationsWithinSpan.this, searcher, terms, boost);
            this.weight = weight;
            this.relationsWeight = relationsWeight;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            weight.extractTerms(terms);
            relationsWeight.extractTerms(terms);
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return weight.isCacheable(ctx) && relationsWeight.isCacheable(ctx);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            weight.extractTermStates(contexts);
            relationsWeight.extractTermStates(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans spans = weight.getSpans(context, requiredPostings);
            if (spans == null)
                return null;
            SpansRelations relations = relationsWeight.getSpans(context, requiredPostings);
            return new SpansCaptureRelationsWithinSpan(spans, relations, toCapture, captureAs);
        }

    }

    @Override
    public String toString(String field) {
        return "RCAP(" + clausesToString(field) + ", " + toCapture + ", " + captureAs + ")";
    }

    @Override
    public boolean canInternalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        return true;
    }

    @Override
    public BLSpanQuery internalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        return new SpanQueryCaptureRelationsWithinSpan(
                SpanQuerySequence.sequenceInternalize(clauses.get(0), clause, onTheRight),
                clauses.get(1), toCapture, captureAs);
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return clauses.get(0).reverseMatchingCost(reader) + clauses.get(1).reverseMatchingCost(reader);
    }

    @Override
    public int forwardMatchingCost() {
        return clauses.get(0).forwardMatchingCost() + clauses.get(1).forwardMatchingCost();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        SpanQueryCaptureRelationsWithinSpan that = (SpanQueryCaptureRelationsWithinSpan) o;
        return Objects.equals(toCapture, that.toCapture) && Objects.equals(captureAs, that.captureAs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), toCapture, captureAs);
    }
}
