package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.eclipse.collections.api.iterator.IntIterator;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

/**
 * Finds all sequences of consecutive hits from the in spans of the
 * specified min and max lengths. Used to implement repetition operators.
 *
 * This generates all possible sequences of consecutive hits, so if we search
 * for B+ in the input string ABBBA, we'll get 3 hits of length 1, 2 hits of
 * length 2, and 1 hit of length 3. In the future, this should be made
 * configurable (to specifically support greedy matching, etc.)
 */
class SpansRepetitionFix extends BLFilterDocsSpans<SpansInBucketsPerDocumentWithStartpointIndex> {
    /** Whether there are more buckets to process */
    boolean moreBuckets = false;

    /** Minimum number of repetitions */
    private final int min;

    /** Maximum number of repetitions */
    private final int max;

    /** Index in bucket of the first match involved in the current repetition.
        This will start at 0 and only increase, visiting each match in the bucket and trying to find repetitions from
        there. */
    private int matchStartIndex;

    /** All the valid end positions found for the current start position.
     *  These are determined by recursively finding connecting matches.
     */
    private MutableIntList endPositions = new IntArrayList();

    /** Index in endPositions list (current end position). */
    private int endPositonIndex;

    /**
     * Current number of repetitions. This will start at min and increase from there as more repetitions can be found.
     * Multiple overlapping matches of different length may therefore be produced.
     */
    private int numRepetitions;

    /**
     * Whether we've already found our first match even though we weren't yet asked for it.
     * Usually this is because we need to determine if the document has any matches at all, or skip it otherwise.
     * If true, startPosition() will still return -1 and the next call to nextStartPosition() will return
     * the hit we've already found.
     */
    private boolean alreadyAtFirstMatch = false;

    /**
     * Construct SpansRepetition.
     *
     * Source must be start-point sorted and unique.
     *
     * @param source spans to find repetitions in
     * @param min minimum number of repetitions
     * @param max maximum number of repetitions
     */
    public SpansRepetitionFix(BLSpans source, int min, int max) {
        // Find all consecutive matches in this Spans
        super(new SpansInBucketsPerDocumentWithStartpointIndex(source));
        this.min = min;
        this.max = max == -1 ? MAX_UNLIMITED : max;
        if (min > this.max)
            throw new IllegalArgumentException("min > max");
        if (min < 1)
            throw new IllegalArgumentException("min and max must be at least 1");
    }

    @Override
    public int endPosition() {
        if (alreadyAtFirstMatch)
            return -1; // .nextStartPosition() not called yet
        if (!moreBuckets)
            return NO_MORE_POSITIONS;
        return endPositions.get(endPositonIndex);
    }

    @Override
    public int nextDoc() throws IOException {
        alreadyAtFirstMatch = false;
        return super.nextDoc();
    }

    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        // Does this document have any clause matches?
        moreBuckets = in.nextBucket() != SpansInBuckets.NO_MORE_BUCKETS;
        if (!moreBuckets)
            return false;

        // Are any of the clause matches the start of a matching repetition?
        for (int i = 0; i < in.bucketSize(); i++) {
            if (findMatchesFromIndex(i)) {
                matchStartIndex = i;
                endPositonIndex = 0;
                alreadyAtFirstMatch = true;
                return true;
            }
        }
        // We didn't find any matches
        return false;
    }

    /**
     * Find matches, if any, starting at the specified index in the current bucket.
     *
     * This will fill the endPositions list with all valid end positions for the current start position.
     *
     * @param indexInBucket index in bucket to start looking for matches
     * @return true if matches were found, false if not
     */
    private boolean findMatchesFromIndex(int indexInBucket) {
        endPositions.clear();
        findEndpointsForRepetition(indexInBucket, 1);
        return !endPositions.isEmpty();
    }

    /**
     * Find all valid end positions for a repetition starting at the specified position.
     *
     * @param fromIndex index in bucket to start looking for further repetitions
     * @param numberSoFar number of repetitions found so far
     */
    private void findEndpointsForRepetition(int fromIndex, int numberSoFar) {
        if (numberSoFar >= min && numberSoFar <= max) {
            // This is a valid repetition; add the end position
            endPositions.add(in.endPosition(fromIndex));
        }
        if (numberSoFar >= max) {
            // Don't continue looking for longer matches
            return;
        }
        int fromPos = in.endPosition(fromIndex);
        IntIterator it = in.indexesForStartpoint(fromPos).intIterator();
        while (it.hasNext()) {
            findEndpointsForRepetition(it.next(), numberSoFar + 1);
        }
    }

    /**
     * Go to the next match.
     *
     * @return true if we're on a valid match, false if we're done.
     */
    @Override
    public int nextStartPosition() throws IOException {
        // Are we done with this document?
        if (in.docID() == NO_MORE_DOCS || !moreBuckets)
            return NO_MORE_POSITIONS;

        // Did we already find the first match?
        if (alreadyAtFirstMatch) {
            // We're already at the first match in the document, because
            // we needed to check if there were matches at all. Return it now.
            alreadyAtFirstMatch = false;
            return in.startPosition(matchStartIndex);
        }

        // Are there more matches at this start position?
        if (!endPositions.isEmpty() && endPositonIndex < endPositions.size() - 1) {
            endPositonIndex++;
            if (endPositonIndex < endPositions.size()) {
                // Still more end positions for this start position
                return in.startPosition(matchStartIndex);
            }
        }
        // No more matches at this start position.
        // Look for the next start position with matches.
        endPositions.clear();
        endPositonIndex = 0;
        while (matchStartIndex < in.bucketSize() - 1) {
            matchStartIndex++;
            if (findMatchesFromIndex(matchStartIndex))
                return in.startPosition(matchStartIndex);
        }

        // No more matches
        return NO_MORE_POSITIONS;
    }

    /**
     * Go to the specified document, if it has hits. If not, go to the next document
     * containing hits.
     *
     * @param target the document number to skip to / over
     * @return true if we're at a valid hit, false if not
     */
    @Override
    public int advance(int target) throws IOException {
        alreadyAtFirstMatch = false;
        return super.advance(target);
    }

    /**
     * @return start of the current hit
     */
    @Override
    public int startPosition() {
        if (alreadyAtFirstMatch)
            return -1; // .nextStartPosition() not called yet
        if (!moreBuckets)
            return NO_MORE_POSITIONS;
        return in.startPosition(matchStartIndex);
    }

    @Override
    public String toString() {
        return "SpansRepetition(" + in + ", " + min + ", " + BLSpanQuery.inf(max) + ")";
    }

    @Override
    public void getMatchInfo(MatchInfo[] relationInfo) {
        // TODO: this uses the first match for match info, but maybe we want something else?
        in.getMatchInfo(matchStartIndex, relationInfo);
    }
}
