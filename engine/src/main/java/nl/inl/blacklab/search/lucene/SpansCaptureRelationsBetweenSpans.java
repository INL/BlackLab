package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.spans.FilterSpans;

/**
 * Captures all relations between the hits from two clauses.
 *
 * This is used to capture cross-field relations in a parallel corpus.
 *
 * @@@ PROBLEM: right now, subsequent spans from the source spans may not overlap!
 *   If they do overlap, some relations may be skipped over.
 *   We should cache (some) relations from the source span so we can be sure we return all
 *   of them, even if the source spans overlap. Use SpansInBuckets or maybe a rewindable
 *   Spans view on top of that class?
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

        /** If target == null, we may still want to capture the relation targets.
         *  E.g. <code>(...some source query...) ==> A:[]*</code>
         *  In that case, this gives the capture name for that. */
        private final String captureTargetAs;

        /** Group index of captureTargetAs */
        private int captureTargetAsIndex = -1;

        /** If target == null and captureTargetAs is set, this gives the target field for capture. */
        private final String targetField;

        public Target(BLSpans relations, BLSpans target, String captureAs, String captureTargetAs, String targetField) {
            this.relations = relations;
            this.captureAs = captureAs;
            this.target = target == null ? null : new SpansInBucketsPerDocument(target);
            this.captureTargetAs = captureTargetAs;
            this.targetField = targetField;
            if (target != null && !StringUtils.isEmpty(captureTargetAs))
                throw new IllegalArgumentException("Can't specify captureTargetAs if target is not null");
        }

        void setContext(HitQueryContext context) {
            relations.setHitQueryContext(context);
            captureAsIndex = context.registerMatchInfo(captureAs, MatchInfo.Type.LIST_OF_RELATIONS);

            HitQueryContext targetContext = context.withField(targetField);
            if (!StringUtils.isEmpty(captureTargetAs))
                captureTargetAsIndex = targetContext.registerMatchInfo(captureTargetAs, MatchInfo.Type.SPAN);
            if (target != null)
                target.setHitQueryContext(targetContext);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Target target1 = (Target) o;
            return Objects.equals(relations, target1.relations) && Objects.equals(captureAs,
                    target1.captureAs) && Objects.equals(captureTargetAs,
                    target1.captureTargetAs) && Objects.equals(target, target1.target);
        }

        @Override
        public int hashCode() {
            return Objects.hash(relations, captureAs, captureTargetAs, target);
        }

        @Override
        public String toString() {
            return "Target{" +
                    "relations=" + relations +
                    ", captureAs='" + captureAs + '\'' +
                    ", captureTargetAs='" + captureTargetAs + '\'' +
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
            matchInfo = new MatchInfo[context.numberOfMatchInfos()];
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
            int targetPosMin = Integer.MAX_VALUE;
            int targetPosMax = Integer.MIN_VALUE;
            int docId = target.relations.docID();
            if (docId < candidate.docID())
                docId = target.relations.advance(candidate.docID());
            if (docId == candidate.docID()) {
                // @@@ TODO: make rewindable Spans view on top of SpansInBucketsPerDocument for this?
                //           (otherwise we might miss relations if the source spans overlap)
                // if (target.relations.startPosition() > sourceStart)
                //     target.relations.rewindStartPosition(sourceStart);
                if (target.relations.startPosition() < sourceStart)
                    target.relations.advanceStartPosition(sourceStart);
                while (target.relations.startPosition() < sourceEnd) {
                    if (target.relations.endPosition() <= sourceEnd) {
                        // Source of this relation is inside our source hit.
                        RelationInfo relInfo = target.relations.getRelationInfo().copy();
                        capturedRelations.add(relInfo);
                        // Keep track of the min and max target positions so we can quickly reject targets below.
                        targetPosMin = Math.min(targetPosMin, relInfo.getTargetStart());
                        targetPosMax = Math.max(targetPosMax, relInfo.getTargetEnd());
                    }
                    target.relations.nextStartPosition();
                }
            }

            if (capturedRelations.isEmpty()) {
                // If no relations match, there is no match.
                return FilterSpans.AcceptStatus.NO;
            }

            if (target.target == null) {
                // No target span specified (or e.g. A:[]* ); just accept the relations we captured.
                matchInfo[target.captureAsIndex] = RelationListInfo.create(capturedRelations, getOverriddenField());

                // If target query was e.g. A:[]*, capture [targetPosMin, targetPosmax) into A.
                if (target.captureTargetAsIndex >= 0) {

                    matchInfo[target.captureTargetAsIndex] = SpanInfo.create(targetPosMin, targetPosMax,
                            target.targetField);
                }

                continue;
            }

            // Find the smallest target span that covers the highest number of the relations we just captured.
            int targetDocId = target.target.docID();
            if (targetDocId < candidate.docID()) {
                targetDocId = target.target.advance(candidate.docID());
                target.target.nextBucket();
            }
            if (targetDocId == candidate.docID()) {
                // Target positioned in doc. Find best matching hit.
                int targetIndex = -1;
                int targetSpanLength = Integer.MAX_VALUE;
                int targetRelationsCovered = 0;
                for (int i = 0; i < target.target.bucketSize(); i++) {
                    // Check if this is a better target match than we had before.
                    int targetStart = target.target.startPosition(i);
                    int targetEnd = target.target.endPosition(i);
                    if (targetPosMin > targetEnd || targetPosMax < targetStart) {
                        // The targets of the relations we captured our outside this target span. Reject it.
                        continue;
                    }
                    // There is some overlap between the target span and the relations we captured.
                    // Find out which relations are inside this target span, so we can pick the best target span.
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
                target.target.getMatchInfo(finalTargetIndex, matchInfo); // also perform captures on the target
            } else {
                // Target document has no matches. Reject this hit.
                return FilterSpans.AcceptStatus.NO;
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
