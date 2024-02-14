package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
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
import org.apache.lucene.search.spans.SpanWeight;

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

    /** Combination of relation and target queries */
    public static class Target {

        public static Target get(QueryInfo queryInfo, String relationFieldName, BLSpanQuery target, String targetField,
                String captureRelsAs, String relationType) {
            return new Target(getRelationsQuery(queryInfo, relationFieldName, relationType), target, targetField,
                    captureRelsAs);
        }

        private static SpanQueryRelations getRelationsQuery(QueryInfo queryInfo, String relationFieldName, String relationType) {
            // Note that we use span mode source, because that's what we'll primarily be filtering on.
            // Once we find a relation matching the current source span, we'll check if target matches as well.
            SpanQueryRelations relations = new SpanQueryRelations(queryInfo, relationFieldName, relationType,
                    Collections.emptyMap(), SpanQueryRelations.Direction.BOTH_DIRECTIONS,
                    RelationInfo.SpanMode.SOURCE, "", null);
            return relations;
        }

        private final BLSpanQuery relations;

        /** Match info name for the list of captured relations */
        private final String captureAs;

        /** Span the relation targets must be inside of (or null if we don't care) */
        private final BLSpanQuery target;

        private final String targetField;

        private Target(BLSpanQuery relations, BLSpanQuery target, String targetField, String captureAs) {
            this.relations = relations;
            this.target = target;
            this.targetField = targetField;
            this.captureAs = captureAs;
        }

        public static List<Target> rewriteTargets(List<Target> targets, IndexReader reader) throws IOException {
            boolean anyRewritten = false;
            List<Target> newTargets = new ArrayList<>(targets.size());
            for (Target target: targets) {
                Target newTarget = target.rewrite(reader);
                if (newTarget != target)
                    anyRewritten = true;
                newTargets.add(newTarget);
            }
            return anyRewritten ? newTargets : targets;
        }

        public static List<TargetWeight> createWeightTargets(List<Target> targets, IndexSearcher searcher, ScoreMode scoreMode, float boost)
                throws IOException {
            List<TargetWeight> targetWeights = new ArrayList<>(targets.size());
            for (Target target: targets) {
                TargetWeight targetWeight = target.createWeight(searcher, scoreMode, boost);
                targetWeights.add(targetWeight);
            }
            return targetWeights;
        }

        private TargetWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
            BLSpanWeight relationsWeight = relations.createWeight(searcher, scoreMode, boost);
            BLSpanWeight targetWeight = null;
            String captureTargetAs = null;
            //String targetField = null;
            if (target instanceof SpanQueryCaptureGroup &&
                    BLSpanQuery.isAnyNGram(((SpanQueryCaptureGroup)target).getClause())) {
                // Special case: target is e.g. A:[]*. Don't actually search for all n-grams, just ignore
                // target while matching relations and capture the relation targets as A.
                captureTargetAs = ((SpanQueryCaptureGroup)target).getCaptureName();
                //targetField = target.getField(); // so we can set the field properly when we capture target
            } else {
                targetWeight = target == null || BLSpanQuery.isAnyNGram(target) ? null : target.createWeight(searcher, scoreMode, boost);
            }
            return new TargetWeight(relationsWeight, targetWeight, captureAs, captureTargetAs, targetField);
        }

        private Target rewrite(IndexReader reader) throws IOException {
            BLSpanQuery newRelations = relations.rewrite(reader);
            BLSpanQuery newTarget = target == null ? null : target.rewrite(reader);
            if (newRelations != relations || newTarget != target) {
                return new Target(newRelations, newTarget, targetField, captureAs);
            }
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Target target1 = (Target) o;
            return Objects.equals(relations, target1.relations) && Objects.equals(captureAs,
                    target1.captureAs) && Objects.equals(target, target1.target);
        }

        @Override
        public int hashCode() {
            return Objects.hash(relations, captureAs, target);
        }

        @Override
        public String toString() {
            return "Target{" +
                    "relations=" + relations +
                    ", captureAs='" + captureAs + '\'' +
                    ", target=" + target +
                    '}';
        }
    }

    /** Combination of relation and target weights */
    static class TargetWeight {

        private final BLSpanWeight relations;

        /** Match info name for the list of captured relations */
        private final String captureAs;

        /** Match info name for the target span (if target == null, and if desired) */
        private final String captureTargetAs;

        /** Target field for capture. */
        private final String targetField;

        /** Span the relation targets must be inside of (or null if we don't care) */
        private final BLSpanWeight target;

        public TargetWeight(BLSpanWeight relations, BLSpanWeight target, String captureAs, String captureTargetAs,
                String targetField) {
            this.relations = relations;
            this.captureAs = captureAs;
            this.captureTargetAs = captureTargetAs;
            this.targetField = targetField;
            this.target = target;
        }

        public static void extractTermsFromTargets(List<TargetWeight> targets, Set<Term> terms) {
            for (TargetWeight target: targets) {
                target.extractTerms(terms);
            }
        }

        public static boolean isCacheableTargets(List<TargetWeight> targets, LeafReaderContext ctx) {
            for (TargetWeight target: targets) {
                if (!target.isCacheable(ctx))
                    return false;
            }
            return true;
        }

        public static void extractTermStatesFromTargets(List<TargetWeight> targets, Map<Term, TermStates> contexts) {
            for (TargetWeight target: targets) {
                target.extractTermStates(contexts);
            }
        }

        public static List<SpansCaptureRelationsBetweenSpans.Target> getSpansTargets(List<TargetWeight> targets,
                LeafReaderContext context, SpanWeight.Postings requiredPostings)
                throws IOException {
            List<SpansCaptureRelationsBetweenSpans.Target> targetSpanses = new ArrayList<>(targets.size());
            for (TargetWeight target: targets) {
                SpansCaptureRelationsBetweenSpans.Target spansTarget = target.getSpans(context, requiredPostings);
                targetSpanses.add(spansTarget); // if null, query has no hits at all; we'll check this later
            }
            return targetSpanses;
        }

        private SpansCaptureRelationsBetweenSpans.Target getSpans(LeafReaderContext context,
                SpanWeight.Postings requiredPostings) throws IOException {
            BLSpans relationsSpans = relations.getSpans(context, requiredPostings);
            if (relationsSpans == null)
                return null;
            BLSpans targetSpans = target == null ? null : target.getSpans(context, requiredPostings);
            return new SpansCaptureRelationsBetweenSpans.Target(relationsSpans, targetSpans, captureAs,
                    captureTargetAs, targetField);
        }

        private void extractTermStates(Map<Term, TermStates> contexts) {
            relations.extractTermStates(contexts);
            if (target != null)
                target.extractTermStates(contexts);
        }

        private boolean isCacheable(LeafReaderContext ctx) {
            return relations.isCacheable(ctx) && (target == null || target.isCacheable(ctx));
        }

        private void extractTerms(Set<Term> terms) {
            relations.extractTerms(terms);
            if (target != null)
                target.extractTerms(terms);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            TargetWeight that = (TargetWeight) o;
            return Objects.equals(relations, that.relations) && Objects.equals(captureAs, that.captureAs)
                    && Objects.equals(captureTargetAs, that.captureTargetAs)
                    && Objects.equals(target, that.target);
        }

        @Override
        public int hashCode() {
            return Objects.hash(relations, captureAs, captureTargetAs, target);
        }
    }

    private final List<Target> targets;

    /**
     * Capture all matching relations occurring within a captured span.
     * The query might be something like <code>'dog' within &lt;s/&gt;</code>.
     * In that case, captureRelsInside would be "s" (name of the capture containing the
     * sentence spans, automatically assigned).
     *
     * @param source        span the relation sources must be inside of
     * @param targets       spans the relation targets must be inside of (or null if we don't care)
     */
    public SpanQueryCaptureRelationsBetweenSpans(BLSpanQuery source, List<Target> targets) {
        super(source);
        this.targets = targets;
        this.guarantees = source.guarantees();
    }

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
            BLSpanQuery source, BLSpanQuery target, String targetField, String captureRelsAs, String relationType) {
        this(source, List.of(Target.get(queryInfo, relationFieldName, target, targetField, captureRelsAs,
                relationType)));
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
            String targetField, String captureRelsAs) {
        this(source, List.of(new Target(relations, target, targetField, captureRelsAs)));
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        List<BLSpanQuery> rewritten = rewriteClauses(reader);
        List<Target> newTargets = Target.rewriteTargets(targets, reader);
        if (rewritten == null && newTargets == targets)
            return this; // nothing to rewrite
        if (rewritten == null)
            rewritten = clauses;
        return new SpanQueryCaptureRelationsBetweenSpans(rewritten.get(0), newTargets);
    }

    @Override
    public boolean matchesEmptySequence() {
        return clauses.get(0).matchesEmptySequence();
    }

    @Override
    public BLSpanQuery noEmpty() {
        if (!matchesEmptySequence())
            return this;
        return new SpanQueryCaptureRelationsBetweenSpans(clauses.get(0).noEmpty(), targets);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        BLSpanWeight weight = clauses.get(0).createWeight(searcher, scoreMode, boost);
        List<TargetWeight> targetWeights = Target.createWeightTargets(targets, searcher, scoreMode, boost);
        return new Weight(weight, targetWeights, searcher, scoreMode.needsScores() ? getTermStates(weight) : null, boost);
    }

    public BLSpanQuery getClause() {
        return clauses.get(0);
    }

    class Weight extends BLSpanWeight {

        final BLSpanWeight sourceWeight;

        private final List<TargetWeight> targets;

        public Weight(BLSpanWeight sourceWeight, List<TargetWeight> targets,
                IndexSearcher searcher, Map<Term, TermStates> terms, float boost)
                throws IOException {
            super(SpanQueryCaptureRelationsBetweenSpans.this, searcher, terms, boost);
            this.sourceWeight = sourceWeight;
            this.targets = targets;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            sourceWeight.extractTerms(terms);
            TargetWeight.extractTermsFromTargets(targets, terms);
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return sourceWeight.isCacheable(ctx) && TargetWeight.isCacheableTargets(targets, ctx);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            sourceWeight.extractTermStates(contexts);
            TargetWeight.extractTermStatesFromTargets(targets, contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans spans = sourceWeight.getSpans(context, requiredPostings);
            if (spans == null)
                return null;
            List<SpansCaptureRelationsBetweenSpans.Target> targetSpans =
                    TargetWeight.getSpansTargets(targets, context, requiredPostings);
            if (targetSpans.stream().anyMatch(Objects::isNull))
                return null; // one of the targets has no hits at all
            return new SpansCaptureRelationsBetweenSpans(spans, targetSpans);
        }

    }

    @Override
    public String toString(String field) {
        return "==>(" + clausesToString(field) + ", " + targets + ")";
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
        return Objects.equals(targets, that.targets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), targets);
    }
}
