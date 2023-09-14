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

    /** Name of match info to capture relations from, e.g. "s" to capture relations inside the sentence captured as "s".
     */
    final String captureRelsInside;

    /** Match info name for the list of captured relations */
    final String captureRelsAs;

    /**
     * Capture all matching relations occurring within a captured span.
     *
     * The query might be something like <code>'dog' within &lt;s/&gt;</code>.
     * In that case, captureRelsInside would be "s" (name of the capture containing the
     * sentence spans, automatically assigned).
     *
     * @param queryInfo query info
     * @param relationFieldName field where relations where indexed
     * @param query query capturing the span to capture relations from
     * @param captureRelsInside name of the match info span to capture relations from
     * @param captureRelsAs name to capture the list of relations as
     * @param relationType type of relation to capture (regex)
     */
    public SpanQueryCaptureRelationsWithinSpan(QueryInfo queryInfo, String relationFieldName, BLSpanQuery query,
            String captureRelsInside, String captureRelsAs, String relationType) {
        super(query, new SpanQueryRelations(queryInfo, relationFieldName, relationType, Collections.emptyMap(),
                SpanQueryRelations.Direction.BOTH_DIRECTIONS, RelationInfo.SpanMode.FULL_SPAN, ""));
        this.captureRelsInside = captureRelsInside;
        this.captureRelsAs = captureRelsAs;
        this.guarantees = query.guarantees();
    }


    /**
     * Capture all matching relations occurring within a captured span.
     *
     * The query might be something like <code>'dog' within &lt;s/&gt;</code>.
     * In that case, captureRelsInside would be "s" (name of the capture containing the
     * sentence spans, automatically assigned).
     *
     * @param query query capturing the span to capture relations from
     * @param relations relations to capture
     * @param captureRelsInside name of the match info span to capture relations from
     * @param captureRelsAs name to capture the list of relations as
     */
    public SpanQueryCaptureRelationsWithinSpan(BLSpanQuery query, BLSpanQuery relations, String captureRelsInside,
            String captureRelsAs) {
        super(query, relations);
        this.captureRelsInside = captureRelsInside;
        this.captureRelsAs = captureRelsAs;
        this.guarantees = query.guarantees();
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        List<BLSpanQuery> rewritten = rewriteClauses(reader);
        return rewritten == null ? this : new SpanQueryCaptureRelationsWithinSpan(rewritten.get(0), rewritten.get(1),
                captureRelsInside, captureRelsAs);
    }

    @Override
    public boolean matchesEmptySequence() {
        return clauses.get(0).matchesEmptySequence();
    }

    @Override
    public BLSpanQuery noEmpty() {
        if (!matchesEmptySequence())
            return this;
        return new SpanQueryCaptureRelationsWithinSpan(clauses.get(0).noEmpty(), clauses.get(1).noEmpty(),
                captureRelsInside, captureRelsAs);
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
            return new SpansCaptureRelationsWithinSpan(spans, relations, captureRelsInside,
                    captureRelsAs);
        }

    }

    @Override
    public String toString(String field) {
        return "RCAP(" + clausesToString(field) + ", " + captureRelsInside + ", " + captureRelsAs
                + ")";
    }

    @Override
    public boolean canInternalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        return true;
    }

    @Override
    public BLSpanQuery internalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        return new SpanQueryCaptureRelationsWithinSpan(
                SpanQuerySequence.sequenceInternalize(clauses.get(0), clause, onTheRight),
                clauses.get(1), captureRelsInside, captureRelsAs);
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
        return Objects.equals(captureRelsInside, that.captureRelsInside) && Objects.equals(
                captureRelsAs, that.captureRelsAs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), captureRelsInside, captureRelsAs);
    }
}
