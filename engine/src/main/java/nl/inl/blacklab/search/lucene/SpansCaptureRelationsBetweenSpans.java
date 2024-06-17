package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.lucene.search.spans.FilterSpans;

/**
 * Captures all relations between the hits from two clauses.
 *
 * This is used to capture cross-field (alignment) relations in a parallel corpus.
 *
 * FIXME ? right now, subsequent spans from the source spans may not overlap!
 *   If they do overlap, some relations may be skipped over.
 *   We should cache (some) relations from the source span so we can be sure we return all
 *   of them, even if the source spans overlap. Use SpansInBuckets or maybe a rewindable
 *   Spans view on top of that class? See @@@ comment below for a possible solution.
 */
class SpansCaptureRelationsBetweenSpans extends BLFilterSpans<BLSpans> {

    public static class Target {

        private final BLSpans relations;

        /** Match info name for the list of captured relations */
        private final String captureRelationsAs;

        /** Group index of captureAs */
        private int captureRelationsIndex = -1;

        /** Span the relation targets must be inside of (or null if there are no hits or we don't care; in the latter
         * case, {@link #hasTargetRestrictions} will be false) */
        private final SpansInBucketsPerDocument target;

        /** If false, there are no target restrictions, so we don't need to check */
        private final boolean hasTargetRestrictions;

        /** If target == null, we may still want to capture the relation targets.
         *  E.g. <code>(...some source query...) ==> A:[]*</code>
         *  In that case, this gives the capture name for that.
         *  (if target is not null, any desired capture operation is included in that,
         *   so we don't need it here) */
        private final String captureTargetAs;

        /** Group index of captureTargetAs */
        private int captureTargetAsIndex = -1;

        /** If target == null and captureTargetAs is set, this gives the target field for capture. */
        private final String targetField;

        public Target(BLSpans relations, BLSpans target, boolean hasTargetRestrictions, String captureRelationsAs, String captureTargetAs, String targetField) {
            this.relations = relations;
            this.captureRelationsAs = captureRelationsAs;
            this.target = target == null ? null : new SpansInBucketsPerDocument(target);
            this.hasTargetRestrictions = hasTargetRestrictions;
            this.captureTargetAs = captureTargetAs;
            this.targetField = targetField;
            assert captureTargetAs != null && !captureTargetAs.isEmpty();
        }

        void setContext(HitQueryContext context) {
            relations.setHitQueryContext(context);
            captureRelationsIndex = context.registerMatchInfo(captureRelationsAs, MatchInfo.Type.LIST_OF_RELATIONS);

            HitQueryContext targetContext = context.withField(targetField);
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
            return captureRelationsIndex == target1.captureRelationsIndex && hasTargetRestrictions == target1.hasTargetRestrictions
                    && captureTargetAsIndex == target1.captureTargetAsIndex && Objects.equals(relations,
                    target1.relations) && Objects.equals(captureRelationsAs, target1.captureRelationsAs) && Objects.equals(
                    target, target1.target) && Objects.equals(captureTargetAs, target1.captureTargetAs)
                    && Objects.equals(targetField, target1.targetField);
        }

        @Override
        public int hashCode() {
            return Objects.hash(relations, captureRelationsAs, captureRelationsIndex, target, hasTargetRestrictions, captureTargetAs,
                    captureTargetAsIndex, targetField);
        }

        @Override
        public String toString() {
            return "Target{" +
                    "relations=" + relations +
                    ", captureAs='" + captureRelationsAs + '\'' +
                    ", captureTargetAs='" + captureTargetAs + '\'' +
                    ", target=" + target +
                    ", hasTargetRestrictions=" + hasTargetRestrictions +
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

    /** Start of current (source) hit (covers all sources of captured relations) */
    private int adjustedStart;

    /** End of current (source) hit (covers all sources of captured relations) */
    private int adjustedEnd;

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
    public int startPosition() {
        if (atFirstInCurrentDoc)
            return -1;
        if (startPos == -1 || startPos == NO_MORE_POSITIONS)
            return startPos;
        return adjustedStart;
    }

    @Override
    public int endPosition() {
        if (atFirstInCurrentDoc)
            return -1;
        if (startPos == -1 || startPos == NO_MORE_POSITIONS)
            return startPos;
        return adjustedEnd;
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
        int sourceStart = candidate.startPosition();
        int sourceEnd = candidate.endPosition();

        // Our final (source) span will cover all captured relations.
        adjustedStart = sourceStart;
        adjustedEnd = sourceEnd;

        for (Target target: targets) {

            // Capture all relations with source overlapping this span.
            capturedRelations.clear();
            int targetPosMin = Integer.MAX_VALUE;
            int targetPosMax = Integer.MIN_VALUE;
            int docId = target.relations.docID();
            if (docId < candidate.docID())
                docId = target.relations.advance(candidate.docID());
            if (docId == candidate.docID()) {

                // @@@ make rewindable Spans view on top of SpansInBucketsPerDocument for this?
                //           (otherwise we might miss relations if the source spans overlap)
                //
                // // Rewind relations if necessary
                // if (target.relations.endPosition() > sourceStart)
                //     target.relations.rewindStartPosition(sourceStart);

                // Advance relations such that the relation source end position is after the
                // current start position (of the query source), i.e. they may overlap.
                while (target.relations.endPosition() <= sourceStart) {
                    if (target.relations.nextStartPosition() == NO_MORE_POSITIONS)
                        break;
                }
                while (target.relations.startPosition() < sourceEnd) {
                    // Source of this relation overlaps our source hit.
                    RelationInfo relInfo = target.relations.getRelationInfo().copy();
                    capturedRelations.add(relInfo);
                    // Keep track of the min and max target positions so we can quickly reject targets below.
                    targetPosMin = Math.min(targetPosMin, relInfo.getTargetStart());
                    targetPosMax = Math.max(targetPosMax, relInfo.getTargetEnd());

                    target.relations.nextStartPosition();
                }
            }

            if (capturedRelations.isEmpty()) {
                // If no relations match, there is no match.
                return FilterSpans.AcceptStatus.NO;
            }
            if (target.hasTargetRestrictions && target.target == null) {
                // There were target restrictions, but no hits (in this index segment); no match
                return FilterSpans.AcceptStatus.NO;
            }

            if (!target.hasTargetRestrictions) {
                // No target span specified (or e.g. A:[]* ); just accept the relations we captured.
                matchInfo[target.captureRelationsIndex] = RelationListInfo.create(capturedRelations, getOverriddenField());

                // Capture target span
                matchInfo[target.captureTargetAsIndex] = SpanInfo.create(targetPosMin, targetPosMax,
                        target.targetField);

                updateSourceStartEndWithCapturedRelations(); // update start/end to cover all captured relations
                continue;
            }

            // Find the smallest target span that overlaps the highest number of the relations we just captured.
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
                    // Find out which relations overlap this target span, so we can pick the best target span.
                    int relationsCovered = (int) capturedRelations.stream()
                            .filter(r -> r.getTargetEnd() > targetStart && r.getTargetStart() < targetEnd)
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
                // Only keep the relations that overlap the target span we found.
                int finalTargetIndex = targetIndex;
                capturedRelations.removeIf(r -> r.getTargetEnd() <= target.target.startPosition(finalTargetIndex)
                        || r.getTargetStart() >= target.target.endPosition(finalTargetIndex));
                capturedRelations.sort(RelationInfo::compareTo);
                matchInfo[target.captureRelationsIndex] = RelationListInfo.create(capturedRelations, getOverriddenField());
                matchInfo[target.captureTargetAsIndex] = SpanInfo.create(target.target.startPosition(finalTargetIndex),
                        target.target.endPosition(finalTargetIndex), target.targetField);
                target.target.getMatchInfo(finalTargetIndex, matchInfo); // also perform captures on the target

                updateSourceStartEndWithCapturedRelations(); // update start/end to cover all captured relations
            } else {
                // Target document has no matches. Reject this hit.
                return FilterSpans.AcceptStatus.NO;
            }
        }

        return FilterSpans.AcceptStatus.YES;
    }

    private void updateSourceStartEndWithCapturedRelations() {
        // Our final (source) span will cover all captured relations, so that
        // e.g. "the" =sentence-alignment=>nl "de" will have the aligned sentences as hits, not just single words.
        capturedRelations.forEach(r -> {
            if (r.getSourceStart() < adjustedStart)
                adjustedStart = r.getSourceStart();
            if (r.getSourceEnd() > adjustedEnd)
                adjustedEnd = r.getSourceEnd();
        });
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
