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
 * Captures all relations between the hits from two clauses.
 *
 * This is used to capture cross-field relations in a parallel corpus.
 *
 * @@@ PROBLEM: right now, subsequent spans from the source spans may not overlap!
 *   If they do overlap, some relations may be skipped over.
 *   We should cache some relations from the source span so we can be sure we return all
 *   of them, even if the source spans overlap.
 */
public class SpanQueryCaptureRelationsBetweenSpans extends BLSpanQueryAbstract {

    /** Match info name for the list of captured relations */
    private final String captureRelsAs;

    /**
     * Capture all matching relations occurring within a captured span.
     *
     * The query might be something like <code>'dog' within &lt;s/&gt;</code>.
     * In that case, captureRelsInside would be "s" (name of the capture containing the
     * sentence spans, automatically assigned).
     *
     * @param queryInfo query info
     * @param relationFieldName field where relations where indexed
     * @param source span the relation sources must be inside of
     * @param target span the relation targets must be inside of (or null if we don't care)
     * @param captureRelsAs name to capture the list of relations as
     * @param relationType type of relation to capture (regex)
     */
    public SpanQueryCaptureRelationsBetweenSpans(QueryInfo queryInfo, String relationFieldName,
            BLSpanQuery source, BLSpanQuery target, String captureRelsAs, String relationType) {
        super(source, getRelationsQuery(queryInfo, relationFieldName, relationType), target);
        this.captureRelsAs = captureRelsAs;
        this.guarantees = source.guarantees();
    }

    private static SpanQueryRelations getRelationsQuery(QueryInfo queryInfo, String relFieldName, String relType) {
        // Note that we use span mode source, because that's what we'll primarily be filtering on.
        // Once we find a relation matching the current source span, we'll check if target matches as well.
        return new SpanQueryRelations(queryInfo, relFieldName, relType,
                Collections.emptyMap(), SpanQueryRelations.Direction.BOTH_DIRECTIONS,
                RelationInfo.SpanMode.SOURCE, "", null);
    }

    /**
     * Capture all matching relations occurring within a captured span.
     * The query might be something like <code>'dog' within &lt;s/&gt;</code>.
     * In that case, captureRelsInside would be "s" (name of the capture containing the
     * sentence spans, automatically assigned).
     *
     * @param source        span the relation sources must be inside of
     * @param relations     relations to capture
     * @param target        span the relation targets must be inside of (or null if we don't care)
     * @param captureRelsAs name to capture the list of relations as
     */
    public SpanQueryCaptureRelationsBetweenSpans(BLSpanQuery source, BLSpanQuery relations, BLSpanQuery target,
            String captureRelsAs) {
        super(source, relations, target);
        this.captureRelsAs = captureRelsAs;
        this.guarantees = source.guarantees();
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        List<BLSpanQuery> rewritten = rewriteClauses(reader);
        return rewritten == null ? this : new SpanQueryCaptureRelationsBetweenSpans(rewritten.get(0),
                rewritten.get(1), rewritten.get(2), captureRelsAs);
    }

    @Override
    public boolean matchesEmptySequence() {
        return clauses.get(0).matchesEmptySequence();
    }

    @Override
    public BLSpanQuery noEmpty() {
        if (!matchesEmptySequence())
            return this;
        return new SpanQueryCaptureRelationsBetweenSpans(clauses.get(0).noEmpty(), clauses.get(1).noEmpty(),
                clauses.get(2).noEmpty(), captureRelsAs);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        BLSpanWeight weight = clauses.get(0).createWeight(searcher, scoreMode, boost);
        SpanQueryRelations.Weight relationsWeight = ((SpanQueryRelations)clauses.get(1)).createWeight(searcher, scoreMode, boost);
        BLSpanWeight targetWeight = clauses.get(2).createWeight(searcher, scoreMode, boost);
        return new Weight(weight, relationsWeight, targetWeight, searcher, scoreMode.needsScores() ? getTermStates(weight) : null, boost);
    }

    public BLSpanQuery getClause() {
        return clauses.get(0);
    }

    class Weight extends BLSpanWeight {

        final BLSpanWeight sourceWeight;

        private final BLSpanWeight relationsWeight;

        final BLSpanWeight targetWeight;

        public Weight(BLSpanWeight sourceWeight, SpanQueryRelations.Weight relationsWeight, BLSpanWeight targetWeight,
                IndexSearcher searcher, Map<Term, TermStates> terms, float boost)
                throws IOException {
            super(SpanQueryCaptureRelationsBetweenSpans.this, searcher, terms, boost);
            this.sourceWeight = sourceWeight;
            this.relationsWeight = relationsWeight;
            this.targetWeight = targetWeight;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            sourceWeight.extractTerms(terms);
            relationsWeight.extractTerms(terms);
            targetWeight.extractTerms(terms);
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return sourceWeight.isCacheable(ctx) && relationsWeight.isCacheable(ctx) && targetWeight.isCacheable(ctx);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            sourceWeight.extractTermStates(contexts);
            relationsWeight.extractTermStates(contexts);
            targetWeight.extractTermStates(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans spans = sourceWeight.getSpans(context, requiredPostings);
            if (spans == null)
                return null;
            BLSpans relations = relationsWeight.getSpans(context, requiredPostings);
            BLSpans targetSpans = targetWeight == null ? null : targetWeight.getSpans(context, requiredPostings);
            return new SpansCaptureRelationsBetweenSpans(spans, relations, targetSpans, captureRelsAs);
        }

    }

    @Override
    public String toString(String field) {
        return "==>(" + clausesToString(field) + ", " + captureRelsAs + ")";
    }

    @Override
    public boolean canInternalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        return true;
    }

    @Override
    public BLSpanQuery internalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        return new SpanQueryCaptureRelationsBetweenSpans(
                SpanQuerySequence.sequenceInternalize(clauses.get(0), clause, onTheRight),
                clauses.get(1), clauses.get(2), captureRelsAs);
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return clauses.stream().mapToLong(clause -> clause.reverseMatchingCost(reader)).sum();
    }

    @Override
    public int forwardMatchingCost() {
        return clauses.stream().mapToInt(BLSpanQuery::forwardMatchingCost).sum();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        SpanQueryCaptureRelationsBetweenSpans that = (SpanQueryCaptureRelationsBetweenSpans) o;
        return Objects.equals(captureRelsAs, that.captureRelsAs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), captureRelsAs);
    }
}
