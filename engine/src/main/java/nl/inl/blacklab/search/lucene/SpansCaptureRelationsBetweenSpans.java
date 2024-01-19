package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.lucene.search.spans.FilterSpans;

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
class SpansCaptureRelationsBetweenSpans extends BLFilterSpans<BLSpans> {

    public static class Target {

        private final BLSpans relations;

        /** Match info name for the list of captured relations */
        private final String captureAs;

        /** Group index of captureAs */
        private int captureAsIndex = -1;

        /** Span the relation targets must be inside of (or null if we don't care) */
        private final SpansInBucketsPerDocument target;

        public Target(BLSpans relations, BLSpans target, String captureAs) {
            this.relations = relations;
            this.captureAs = captureAs;
            this.target = target == null ? null : new SpansInBucketsPerDocument(target);
        }

        void setContext(HitQueryContext context) {
            captureAsIndex = context.registerMatchInfo(captureAs);
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
                    ", captureAsIndex=" + captureAsIndex +
                    ", target=" + target +
                    '}';
        }
    }

    private final List<Target> targets;

    /** Our hit query context */
    private HitQueryContext context;

    /** Match info for current hit */
    private MatchInfo[] matchInfo;

    /** List of relations captured for current hit */
    private List<RelationInfo> capturedRelations = new ArrayList<>();

    /**
     * Construct a SpansCaptureRelationsWithinSpan.
     *
     * @param source span the relation sources must be inside of
     * @param targets targets of the relations we're capturing
     */
    public SpansCaptureRelationsBetweenSpans(BLSpans source, List<Target> targets) {
        super(source);
        this.targets = targets;
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        // Prepare matchInfo so we can add captured relations to it
        if (matchInfo == null) {
            matchInfo = new MatchInfo[context.getMatchInfoNames().size()];
        } else {
            Arrays.fill(matchInfo, null);
        }
        candidate.getMatchInfo(matchInfo);

        // Find current source span
        int sourceStart = startPosition();
        int sourceEnd = endPosition();

        for (Target target: targets) {
            // Capture all relations with source inside this span.
            capturedRelations.clear();
            int docId = target.relations.docID();
            if (docId < candidate.docID())
                docId = target.relations.advance(candidate.docID());
            if (docId == candidate.docID()) {
                if (target.relations.startPosition() < sourceStart)
                    target.relations.advanceStartPosition(sourceStart);
                while (target.relations.startPosition() < sourceEnd) {
                    if (target.relations.endPosition() < sourceEnd)
                        capturedRelations.add(target.relations.getRelationInfo().copy());
                    target.relations.nextStartPosition();
                }
            }

            // Find the smallest target span that covers the most of these captured relations.
            int targetDocId = target.target.docID();
            if (targetDocId < candidate.docID())
                targetDocId = target.target.advance(candidate.docID());
            if (targetDocId == candidate.docID()) {
                // Target positioned in doc. Find best matching hit.
                int targetIndex = -1;
                int targetSpanLength = Integer.MAX_VALUE;
                int targetRelationsCovered = 0;
                for (int i = 0; i < target.target.bucketSize(); i++) {
                    // Check if this is a better target match than we had before.
                    int targetStart = target.target.startPosition(i);
                    int targetEnd = target.target.endPosition(i);
                    int relationsCovered = (int) capturedRelations.stream()
                            .filter(r -> r.getTargetStart() >= targetStart && r.getTargetEnd() <= targetEnd)
                            .count();
                    int length = targetEnd - targetStart;
                    if (relationsCovered > targetRelationsCovered
                            || relationsCovered == targetRelationsCovered && length < targetSpanLength) {
                        targetIndex = i;
                        targetSpanLength = length;
                        targetRelationsCovered = relationsCovered;
                    }
                }
                if (targetRelationsCovered == 0) {
                    // A valid hit must have at least one matching relation in each target.
                    return FilterSpans.AcceptStatus.NO;
                }
                // Only keep the relations that match the target span we found.
                int finalTargetIndex = targetIndex;
                capturedRelations.removeIf(r -> r.getTargetStart() < target.target.startPosition(finalTargetIndex)
                        || r.getTargetEnd() > target.target.endPosition(finalTargetIndex));
                capturedRelations.sort(RelationInfo::compareTo);
                matchInfo[target.captureAsIndex] = RelationListInfo.create(capturedRelations, getOverriddenField());
            } else {
                // Target document has no matches. No relations to capture.
                matchInfo[target.captureAsIndex] = RelationListInfo.create(Collections.emptyList(), getOverriddenField());
            }
        }

        return FilterSpans.AcceptStatus.YES;
    }

    @Override
    public String toString() {
        return "==>(" + in + ", " + targets + ")";
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        super.passHitQueryContextToClauses(context);
        this.context = context;
        for (Target target: targets)
            target.setContext(context);
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        // We've already gathered matchInfo in accept(); just copy it over
        int n = Math.min(matchInfo.length, this.matchInfo.length);
        for (int i = 0; i < n; i++) {
            if (this.matchInfo[i] != null)
                matchInfo[i] = this.matchInfo[i];
        }
    }

    @Override
    public boolean hasMatchInfo() {
        return true;
    }
}