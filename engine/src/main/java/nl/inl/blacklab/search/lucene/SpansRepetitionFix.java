package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.eclipse.collections.api.list.primitive.IntList;

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

    /** Index in bucket of the first match involved in the current repetition. This will start at 0 and only increase. */
    private int matchStartIndex;

    /** Index in bucket of the last match involved in the current repetition. This may go up and down if
     *  matches vary in length. */
    private int matchEndIndex;

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
        return in.endPosition(matchEndIndex);
    }

    @Override
    public int nextDoc() throws IOException {
        alreadyAtFirstMatch = false;
        return super.nextDoc();
    }

    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        // See if there's a bucket of matches in this doc
        moreBuckets = in.nextBucket() != SpansInBuckets.NO_MORE_BUCKETS;
        if (moreBuckets) {
            // There are matches in this doc, but we don't know if there's matching repetitions yet.
            matchStartIndex = 0;
            //@@@@
//            while (true) {
//                int endPos = in.endPosition(matchStartIndex);
//                matchEndIndex = findNextRepetition(1, endPos);
//            }
            alreadyAtFirstMatch = true;
            return true;
        }

        // No more matching buckets.
        return false;
    }

    /** Find an extension of the repetition we've found so far, at least min and up to max long. */
    int findNextRepetition(int numberSoFar, int endPos) {
        int current = numberSoFar;
        while (current < max) {
            IntList indexes = in.indexesForStartpoint(endPos);
        }
        return -1; //@@@
    }

    /**
     * Go to the next matching bucket in the current doc, if it has any.
     *
     * @return the start position of the bucket, or NO_MORE_BUCKETS if there's no
     *         more matching buckets
     */
    private int nextBucket() throws IOException {
        moreBuckets = in.nextBucket() != SpansInBuckets.NO_MORE_BUCKETS;
        while (moreBuckets) {
            if (in.bucketSize() >= min) {
                // This stretch is large enough to get a repetition hit;
                // Position us at the first hit and remember we're already there.
                matchStartIndex = 0;
                numRepetitions = min;
                return in.startPosition(matchStartIndex);
            }
            // Not large enough; try next bucket
            moreBuckets = in.nextBucket() != SpansInBuckets.NO_MORE_BUCKETS;
        }
        return SpansInBuckets.NO_MORE_BUCKETS;
    }

    /**
     * Go to the next match.
     *
     * @return true if we're on a valid match, false if we're done.
     */
    @Override
    public int nextStartPosition() throws IOException {
        if (in.docID() == NO_MORE_DOCS || !moreBuckets)
            return NO_MORE_POSITIONS;

        if (alreadyAtFirstMatch) {
            // We're already at the first match in the document, because
            // we needed to check if there were matches at all. Return it now.
            alreadyAtFirstMatch = false;
            return in.startPosition(matchStartIndex);
        }

        // Go to the next hit length for this start point in the current bucket.
        numRepetitions++;

        // Find the first valid hit in the bucket
        if (numRepetitions > max || matchStartIndex + numRepetitions > in.bucketSize()) {
            // On to the next start point.
            matchStartIndex++;
            numRepetitions = min;
        }

        if (matchStartIndex + numRepetitions <= in.bucketSize()) {
            // Still a valid rep. hit.
            return in.startPosition(matchStartIndex);
        }

        // No valid hits left; on to the next matching bucket
        int startPos = nextBucket();
        moreBuckets = startPos != SpansInBuckets.NO_MORE_BUCKETS;
        if (moreBuckets) {
            return startPos;
        }

        // No more matching buckets.
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
        int index = matchStartIndex + numRepetitions - 1; // use the last match for captured groups
        in.getMatchInfo(index, relationInfo);
    }
}
