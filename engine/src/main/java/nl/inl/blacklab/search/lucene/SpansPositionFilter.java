package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.search.ConjunctionDISI;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.SpanCollector;

import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter.Operation;

/**
 * Finds hits from a set that contain one or more hits from the second set, or
 * finds hits from a set that are contained by hit(s) from the second set.
 */
class SpansPositionFilter extends BLSpans {
    /** The spans we're (possibly) looking for */
    private final BLSpans producer;

    /** The spans we use to filter the producer spans */
    private final SpansInBucketsPerDocument filter;

    /** What doc is the filter in? */
    private int filterDoc = -1;

    /** What start pos is the producer at? */
    private int producerStart = -1;

    /**
     * Which index in the filter bucket did we use? (needed for getting captured
     * groups)
     */
    private int filterIndex = -1;

    /** What filter operation to use */
    private final Operation op;

    /** How to adjust the left edge of the producer hits while matching */
    private final int leftAdjust;

    /** How to adjust the right edge of the producer hits while matching */
    private final int rightAdjust;

    /**
     * Are we already at the first match in a new document, before
     * nextStartPosition() has been called? Necessary because we have to make sure
     * nextDoc()/advance() actually puts us in a document with at least one match.
     */
    private boolean alreadyAtFirstMatch = false;

    /**
     * If true, produce hits that DON'T match the filter instead.
     */
    private final boolean invert;

    /** Are the filter hits guaranteed to have the same length? */
    private final boolean filterFixedLength;

    /** Approximation for two-phase iterator */
    private final DocIdSetIterator conjunction;

    /**
     * Find hits from producer, filtered by the filter according to the specified op
     * <p>
     * Both producer and filter should be start-point sorted.
     *
     * @param producer the hits we may be interested in
     * @param filter the hits used to filter the producer hits
     * @param filterFixedLength true if the filter hits are all the same length.
     *            Used for optimization.
     * @param op filter operation to use
     * @param invert if true, produce hits that DON'T match the filter instead
     * @param leftAdjust how to adjust the left edge of the producer hits while
     *            matching
     * @param rightAdjust how to adjust the right edge of the producer hits while
     *            matching
     */
    public SpansPositionFilter(BLSpans producer, SpansInBucketsPerDocument filter, boolean filterFixedLength, Operation op,
            boolean invert, int leftAdjust, int rightAdjust) {
        this.producer = producer; // Sort
        this.op = op;
        this.invert = invert;
        this.filter = filter;
        this.filterFixedLength = filterFixedLength;
        this.leftAdjust = leftAdjust;
        this.rightAdjust = rightAdjust;
        if (invert) {
            // Our best approximation is the producer itself (or its two-phase iter approximation)
            TwoPhaseIterator twoPhaseIterator = producer.asTwoPhaseIterator();
            this.conjunction = twoPhaseIterator == null ? producer : twoPhaseIterator.approximation();
        } else {
            // We can use conjunction of the producer and filter (both need to occur in document to produce matches)
            this.conjunction = ConjunctionDISI.intersectIterators(List.of(this.producer, this.filter));
        }
    }

    @Override
    public int docID() {
        return conjunction.docID();
    }

    @Override
    public int endPosition() {
        if (alreadyAtFirstMatch)
            return -1; // nextStartPosition() hasn't been called yet
        return producer.endPosition();
    }

    @Override
    public int nextDoc() throws IOException {
        alreadyAtFirstMatch = false;

        // Advance container
        producerStart = -1;
        if (producer.nextDoc() == NO_MORE_DOCS)
            return NO_MORE_DOCS; // no more containers; we're done.

        // Find first matching producer span from here
        return findDocWithMatch();
    }

    @Override
    public int advance(int target) throws IOException {
        alreadyAtFirstMatch = false;

        // Skip both to doc
        producerStart = -1;
        if (producer.advance(target) == NO_MORE_DOCS)
            return NO_MORE_DOCS;

        // Find first matching producer span from here
        return findDocWithMatch();
    }

    /**
     * Find a producer span (not necessarily in this document) matching with filter,
     * starting from the current producer span.
     *
     * @return docID if found, NO_MORE_DOCS if no such producer span exists (i.e.
     *         we're done)
     */
    private int findDocWithMatch() throws IOException {
        // Find the next "valid" container, if there is one.
        int producerDoc = producer.docID();
        while (producerDoc != NO_MORE_DOCS) {

            // Are filter and producer in the same document?
            while (filterDoc != producerDoc) {
                if (filterDoc < producerDoc) {
                    // No, advance filter to be in the same document as the producer
                    filterDoc = filter.advance(producerDoc);
                    if (filterDoc == NO_MORE_DOCS) {
                        if (!invert) {
                            // Positive filter, but no more filter hits. We're done.
                            return NO_MORE_DOCS;
                        }
                    } else
                        filter.nextBucket();
                } else {
                    if (invert) {
                        // For negative filters, lagging producer spans is ok. This just means
                        // all hits in the current producer doc are matches.
                        break;
                    }
                    // No, advance producer to be in the same document as the producer
                    producerDoc = producer.advance(filterDoc);
                    if (producerDoc == NO_MORE_DOCS)
                        return NO_MORE_DOCS; // No more producer results, we're done.
                }
            }

            // Are there search results in this document?
            if (twoPhaseCurrentDocMatches())
                return producerDoc;

            // No search results found in the current container.
            // Advance to the next container.
            producerDoc = producer.nextDoc();
            producerStart = -1;
        }
        return producerDoc;
    }

    private boolean twoPhaseCurrentDocMatches() throws IOException {
        producerStart = producer.nextStartPosition();
        if (synchronizePos() != NO_MORE_POSITIONS) {
            alreadyAtFirstMatch = true;
            return true;
        }
        return false;
    }

    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        return super.asTwoPhaseIterator();
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (alreadyAtFirstMatch) {
            // We're already at the first match in the doc. Return it.
            alreadyAtFirstMatch = false;
            return producerStart;
        }

        // Are we done yet?
        if (producerStart == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;

        // Find first matching producer span from here
        producerStart = producer.nextStartPosition();
        return synchronizePos();
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        if (alreadyAtFirstMatch) {
            alreadyAtFirstMatch = false;
            if (producerStart >= target)
                return producerStart;
        }

        // Are we done yet?
        if (producerStart == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;

        producerStart = producer.advanceStartPosition(target);

        // Find first matching producer span from here
        return synchronizePos();
    }

    /**
     * Find a producer span matching with filter, starting from the current producer
     * span.
     *
     * @return start position if found, NO_MORE_POSITIONS if no such container
     *         exists (i.e. we're done)
     */
    private int synchronizePos() throws IOException {
        // Find the next "valid" producer spans, if there is one.
        while (producerStart != NO_MORE_POSITIONS) {
            if (invert && filterDoc != producer.docID()) {
                // No filter hits in this doc, so this is definitely a hit.
                return producerStart;
            }

            // We're at the first unchecked producer spans. Does it match our filter?
            boolean invertedMatch = invert; // if looking for non-matches, keep track if there have been any matches.
            int min = 0, max = filter.bucketSize() - 1;
            if (op == Operation.CONTAINING || op == Operation.CONTAINING_AT_START || op == Operation.CONTAINING_AT_END) {
                // Looking for producer hits with a filter hit inside
                // (these three operations adjust min/max in the same way, so to avoid duplication we'll do it here)
                while (min <= max) {
                    int i = (min + max) / 2;
                    if (filterFixedLength && filter.endPosition(i) > producer.endPosition() + rightAdjust) {
                        // Filter end position to the right of producer hit end position.
                        max = i - 1;
                    } else if (filter.startPosition(i) < producerStart + leftAdjust) {
                        // Filter start position to the left of producer hit start position.
                        min = i + 1;
                    } else {
                        // Can't narrow down the edges any further; do linear search from here.
                        break;
                    }
                }
            }
            switch (op) {
            case CONTAINING:
                // Looking for producer hits with a filter hit inside
                for (int i = min; i <= max; i++) {
                    if (filter.startPosition(i) >= producerStart + leftAdjust
                            && filter.endPosition(i) <= producer.endPosition() + rightAdjust) {
                        if (invert) {
                            // This producer hit is no good; on to the next.
                            invertedMatch = false;
                            break;
                        }
                        // Yes, this producer hit contains this filter hit
                        filterIndex = i; // remember for captured groups
                        return producerStart;
                    }
                }
                break;
            case CONTAINING_AT_START:
                // Looking for producer hits with a filter hit inside, at the start
                for (int i = min; i <= max; i++) {
                    if (filter.startPosition(i) == producerStart + leftAdjust
                            && filter.endPosition(i) <= producer.endPosition() + rightAdjust) {
                        if (invert) {
                            // This producer hit is no good; on to the next.
                            invertedMatch = false;
                            break;
                        }
                        // Yes, this producer hit contains this filter hit
                        filterIndex = i; // remember for captured groups
                        return producerStart;
                    }
                }
                break;
            case CONTAINING_AT_END:
                // Looking for producer hits with a filter hit inside, at the end
                for (int i = min; i <= max; i++) {
                    if (filter.startPosition(i) >= producerStart + leftAdjust
                            && filter.endPosition(i) == producer.endPosition() + rightAdjust) {
                        if (invert) {
                            // This producer hit is no good; on to the next.
                            invertedMatch = false;
                            break;
                        }
                        // Yes, this producer hit contains this filter hit
                        filterIndex = i; // remember for captured groups
                        return producerStart;
                    }
                }
                break;
            case WITHIN:
                // Looking for producer hits contained by a filter hit
                while (min <= max) {
                    int i = (min + max) / 2;
                    if (filter.startPosition(i) > producerStart + leftAdjust) {
                        // Filter start position to the right of producer hit start position.
                        max = i - 1;
                    } else if (filterFixedLength && filter.endPosition(i) < producer.endPosition() + rightAdjust) {
                        // Filter end position to the left of producer hit end position.
                        min = i + 1;
                    } else {
                        // Can't narrow down the edges any further; do linear search from here.
                        break;
                    }
                }
                for (int i = min; i <= max; i++) {
                    if (filter.startPosition(i) <= producerStart + leftAdjust
                            && filter.endPosition(i) >= producer.endPosition() + rightAdjust) {
                        if (invert) {
                            // This producer hit is no good; on to the next.
                            invertedMatch = false;
                            break;
                        }
                        // Yes, this producer hit is contained within this filter hit
                        filterIndex = i; // remember for captured groups
                        return producerStart;
                    }
                }
                break;
            case STARTS_AT:
                // Looking for producer hits starting at a filter hit
                while (min <= max) {
                    int i = (min + max) / 2;
                    if (filter.startPosition(i) > producerStart + leftAdjust) {
                        // Filter start position to the right of producer hit start position.
                        max = i - 1;
                    } else if (filter.startPosition(i) < producerStart + leftAdjust) {
                        // Filter start position to the left of producer hit start position.
                        min = i + 1;
                    } else {
                        // Found filter hit at the start of current producer hit.
                        if (invert) {
                            // This producer hit is no good; on to the next.
                            invertedMatch = false;
                            break;
                        }
                        // Yes, this producer hit starts at this filter hit
                        filterIndex = i; // remember for captured groups
                        return producerStart;
                    }
                }
                break;
            case ENDS_AT:
                // Looking for producer hits ending at a filter hit
                if (filterFixedLength) {
                    while (min <= max) {
                        int i = (min + max) / 2;
                        if (filter.endPosition(i) > producer.endPosition() + rightAdjust) {
                            // Filter end position to the right of producer hit end position.
                            max = i - 1;
                        } else if (filter.endPosition(i) < producer.endPosition() + rightAdjust) {
                            // Filter end position to the left of producer hit end position.
                            min = i + 1;
                        } else {
                            // Can't narrow down the edges any further; do linear search from here.
                            break;
                        }
                    }
                }
                for (int i = min; i <= max; i++) {
                    if (filter.endPosition(i) == producer.endPosition() + rightAdjust) {
                        if (invert) {
                            // This producer hit is no good; on to the next.
                            invertedMatch = false;
                            break;
                        }
                        // Yes, this producer hit ends at this filter hit
                        filterIndex = i; // remember for captured groups
                        return producerStart;
                    }
                }
                break;
            case MATCHES:
                // Looking for producer hits exactly matching a filter hit
                while (min <= max) {
                    int i = (min + max) / 2;
                    if (filter.startPosition(i) < producerStart + leftAdjust
                            || filterFixedLength && filter.endPosition(i) < producer.endPosition() + rightAdjust) {
                        min = i + 1;
                    } else if (filter.startPosition(i) > producerStart + leftAdjust
                            || filterFixedLength && filter.endPosition(i) > producer.endPosition() + rightAdjust) {
                        max = i - 1;
                    } else {
                        // Can't narrow down the edges any further; do linear search from here.
                        break;
                    }
                }
                for (int i = min; i <= max; i++) {
                    if (filter.startPosition(i) == producerStart + leftAdjust
                            && filter.endPosition(i) == producer.endPosition() + rightAdjust) {
                        if (invert) {
                            // This producer hit is no good; on to the next.
                            invertedMatch = false;
                            break;
                        }
                        // Yes, this producer hit exactly matches this filter hit
                        filterIndex = i; // remember for captured groups
                        return producerStart;
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown filter operation " + op);
            }
            // Were we looking for non-matching producer hits, and have we not found any?
            if (invertedMatch) {
                return producerStart;
            }
            // Didn't match filter; go to the next position.
            producerStart = producer.nextStartPosition();
            if (producerStart == NO_MORE_POSITIONS)
                return NO_MORE_POSITIONS;

        }
        return producerStart;
    }

    @Override
    public int startPosition() {
        if (alreadyAtFirstMatch)
            return -1; // nextStartPosition() hasn't been called yet
        return producerStart;
    }

    @Override
    public String toString() {
        String not = invert ? "not " : "";
        String ign = (leftAdjust != 0 || rightAdjust != 0) ? ", " + leftAdjust + ", " + rightAdjust : "";
        switch (op) {
        case CONTAINING:
            return "POSFILTER(" + producer + " " + not + "containing " + filter + ign + ")";
        case WITHIN:
            return "POSFILTER(" + producer + " " + not + "within " + filter + ign + ")";
        case STARTS_AT:
            return "POSFILTER(" + producer + " " + not + "starts at " + filter + ign + ")";
        case ENDS_AT:
            return "POSFILTER(" + producer + " " + not + "ends at " + filter + ign + ")";
        case MATCHES:
            return "POSFILTER(" + producer + " " + not + "matches " + filter + ign + ")";
        default:
            throw new IllegalArgumentException("Unknown filter operation " + op);
        }
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        producer.setHitQueryContext(context);
        filter.setHitQueryContext(context);
    }

    @Override
    public void getMatchInfo(MatchInfo[] relationInfo) {
        if (!childClausesCaptureMatchInfo)
            return;
        producer.getMatchInfo(relationInfo);
        filter.getMatchInfo(filterIndex, relationInfo);
    }

    @Override
    public int width() {
        return producer.width();
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        producer.collect(collector);
    }

    @Override
    public float positionsCost() {
        return producer.positionsCost();
    }
}
