package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.search.spans.TermSpans;

/**
 * Base class for all our own Spans classes.
 * <p>
 * The default implementation is appropriate for Spans classes that return only
 * single-term hits.
 * <p>
 * Note that Spans will iterate through a Lucene index segment in a single thread,
 * therefore Spans and subclasses don't need to be thread-safe.
 */
public abstract class BLSpans extends Spans implements SpanGuaranteeGiver {

    public static final int MAX_UNLIMITED = BLSpanQuery.MAX_UNLIMITED;

    private static BLSpans ensureSortedUnique(BLSpans srcSpans, boolean removeDuplicates) {
        if (srcSpans == null)
            throw new IllegalArgumentException("srcSpans cannot be null");

        // Make sure we don't do any unnecessary work (sort/unique)
        SpanGuarantees g = srcSpans.guarantees();
        removeDuplicates = removeDuplicates && !g.hitsHaveUniqueStartEndAndInfo();
        if (g.hitsStartPointSorted()) {
            // No need to sort again; just remove duplicates if requested
            return removeDuplicates ? new SpansUnique(srcSpans) : srcSpans;
        }
        return new PerDocumentSortedSpans(srcSpans, true, removeDuplicates);
    }


    /**
     * Ensure that given spans are sorted and unique.
     * <p>
     * It is assumed that they are already document-sorted, or at least
     * all hits from one document are contiguous.
     *
     * @param srcSpans         spans that may not be startpoint sorted
     * @return startpoint sorted spans
     */
    public static BLSpans ensureSortedUnique(BLSpans srcSpans) {
        return ensureSortedUnique(srcSpans, true);
    }

    /**
     * Ensure that given spans are startpoint-sorted within documents.
     * <p>
     * It is assumed that they are already document-sorted, or at least
     * all hits from one document are contiguous.
     * <p>
     * Just uses PerDocumentSortedSpans for now, but could be perhaps be
     * optimized to only look at startpoints within the document.
     *
     * @param spans spans that may not be startpoint sorted
     * @return startpoint sorted spans
     */
    public static BLSpans ensureSorted(BLSpans spans) {
        return ensureSortedUnique(spans, false);
    }

    /**
     * Wrap a TermSpans in a BLSpans object.
     *
     * @param clause the clause to wrap
     * @return the wrapped clause
     */
    public static BLSpans wrapTermSpans(TermSpans clause) {
        return new TermSpansWrapper(clause);
    }

    /**
     * For efficiency while matching, this will store the result of hasMatchInfo().
     * Only valid after setHitQueryContext() has been called.
     */
    protected boolean childClausesCaptureMatchInfo = true;

    /** We will delegate our guarantee methods to this. */
    protected SpanGuarantees guarantees;

    public BLSpans() {
        this(null);
    }

    public BLSpans(SpanGuarantees guarantees) {
        this.guarantees = guarantees == null ? SpanGuarantees.NONE : guarantees;
    }

    @Override
    public abstract int nextDoc() throws IOException;

    @Override
    public abstract int advance(int target) throws IOException;

    @Override
    public abstract int nextStartPosition() throws IOException;

    /**
     * Give the BLSpans tree a way to access match info (captured groups etc.),
     * and the classes that capture match info a way to register themselves.
     * <p>
     * Subclasses should override this method, pass the context to their child
     * clauses (if any), and either:
     *
     * <ul>
     *   <li>register the match info they represent with the context (SpansCaptureGroup, SpansRelations do this), OR</li>
     *   <li>store the context so they can later use it to access match info (although this can be problematic
     *       as it may not be available during matching)</li>
     * </ul>
     *
     * As an alternative, they can also only override {@link #passHitQueryContextToClauses(HitQueryContext)}.
     *
     * @param context the hit query context, that e.g. keeps track of captured groups
     */
    public final void setHitQueryContext(HitQueryContext context) {
        childClausesCaptureMatchInfo = hasMatchInfo();
        passHitQueryContextToClauses(context);
    }

    /**
     * Called by setHitQueryContext() to pass the context to child clauses.
     * <p>
     * Subclasses can override this to avoid having to override {@link #setHitQueryContext(HitQueryContext)}.
     *
     * @param context the hit query context, that e.g. keeps track of captured
     *            groups
     */
    protected abstract void passHitQueryContextToClauses(HitQueryContext context);

    /**
     * Get the match infos (captured groups, relations) contained in this
     * BLSpans (sub)tree.
     *
     * @param matchInfo an array the size of the total number of match info in the
     *            query; the current match info for this subtree will be copied here.
     */
    public abstract void getMatchInfo(MatchInfo[] matchInfo);

    /**
     * Does any of our descendants capture match info?
     *
     * This will recursively call this method on all subclauses.
     * Can be used before hit query context is set. After that, it's
     * more efficient to just remember whether any clauses added capture groups.
     *
     * @return true if any of our subclauses capture match info
     */
    public abstract boolean hasMatchInfo();

    /**
     * Advance the start position in the current doc to target or beyond.
     * <p>
     * Always at least advances to the next hit, even if the current start position
     * is already at or beyond the target.
     * <p>
     * <b>CAUTION:</b> if your spans are not start point sorted, this method can
     * not guarantee to skip over all hits that start before the target.
     * Any class that uses this method should be aware of this.
     * {@link BLSpans#ensureSorted(BLSpans)} can be used to ensure
     * that the spans are start point sorted.
     *
     * @param target target start position to advance to
     * @return new start position, or Spans.NO_MORE_POSITIONS if we're done with
     *         this document
     * @throws IOException on error
     */
    public int advanceStartPosition(int target) throws IOException {
        assert target > startPosition();
        // Naive implementations; subclasses may provide a faster version.
        return naiveAdvanceStartPosition(this, target);
    }

    /**
     * Advance the start position by calling nextStartPosition() repeatedly.
     * <p>
     * Useful for twice-derived classes that don't have a more efficient way to advance.
     *
     * @param spans spans to advance
     * @param target target start position to advance to
     * @return new start position, or Spans.NO_MORE_POSITIONS
     */
    public static int naiveAdvanceStartPosition(BLSpans spans, int target) throws IOException {
        int pos;
        do {
            pos = spans.nextStartPosition();
        } while (pos < target); // also covers NO_MORE_POSITIONS
        assert pos != -1;
        return pos;
    }

    @Override
    public long cost() {
        // returns a completely arbitrary constant value, but it's for
        // optimizing scoring and we don't generally use that
        return 100;
    }

    /**
     * Get the "active" relation info for this BLSpans object.
     * <p>
     * A query that finds and combines several relations always has one
     * active relation. This relation is used when we call rspan(),
     * or if we combine the query with another relation query, e.g. using the
     * &amp; operator.
     *
     * @return the relation info, or null if no active relation available
     */
    public abstract RelationInfo getRelationInfo();

    @Override
    public SpanGuarantees guarantees() {
        // (Eventually guarantees may live in a separate object)
        return guarantees;
    }
}
