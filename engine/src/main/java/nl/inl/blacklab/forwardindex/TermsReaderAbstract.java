package nl.inl.blacklab.forwardindex;

import java.text.Collator;
import java.util.Arrays;

import org.eclipse.collections.api.set.primitive.MutableIntSet;

import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.TIntObjectHashMap;
import it.unimi.dsi.fastutil.BigArrays;
import it.unimi.dsi.fastutil.bytes.ByteBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.BlockTimer;

/**
 * Abstrct base class for TermsReader and TermsIntegrated.
 *
 * Each term gets a sensitive and insensitive sort position.
 * Multiple terms may get the same sort position, if those terms
 * are considered equal by the collators involved.
 * For each sort position, a "group id" is assigned, which is
 * actually an offset into the groupId2TermIds array. At this
 * offset is the number of terms n in the group, and then the
 * n term ids themselves.
 * Finally, there's a big character array containing the term
 * string data, and an offsets array that specified for each term id
 * where that term string begins in the character array.
 */
public abstract class TermsReaderAbstract implements Terms {

    /** Will automatically be set to true if assertions are enabled (-ea). */
    protected static boolean DEBUGGING = false;

    /** If assertions are enabled, should we validate term sorts initially? Slows down index opening. */
    protected static final boolean DEBUG_VALIDATE_SORT = false;

    /** Log the timing of different initialization tasks? */
    protected static final boolean LOG_TIMINGS = false;

    /** How many terms total are there? (always valid) */
    private int numberOfTerms;

    /** Collator to use for sensitive string comparisons */
    protected final Collator collator;

    /** Collator to use for insensitive string comparisons */
    protected final Collator collatorInsensitive;

    /** Insensitive sort position to start index of group in groupId2TermIds */
    private int[] insensitivePosition2GroupId;

    /** Sensitive sort position to start index of group in groupId2TermIds */
    private int[] sensitivePosition2GroupId;

    /** Term id to sensitive sort position */
    private int[] termId2SensitivePosition;

    /** Term id to sensitive sort position */
    private int[] termId2InsensitivePosition;

    /**
     * Contains a leading int specifying how many ids for a given group, followed by the list of ids.
     * For a group of size 2 containing the ids 4 and 8, contains [..., 2, 4, 8, ...]
     * {@link #insensitivePosition2GroupId} and {@link #sensitivePosition2GroupId} contain the index of the leading int
     * in this array for all sensitive/insensitive sorting positions respectively.
     */
    private int[] groupId2TermIds;

    /** The character data for all terms. */
    private ByteBigArrayBigList termCharData;

    /**
     * Contains the start position in termCharData array for each term id.
     * The length of the term must be computed by using the start position of the next term.
     */
    private long[] termId2CharDataOffset;

    public TermsReaderAbstract(Collators collators) {
        assert DEBUGGING = true;
        this.collator = collators.get(MatchSensitivity.SENSITIVE);
        this.collatorInsensitive = collators.get(MatchSensitivity.INSENSITIVE);
    }

    protected void finishInitialization(String name, String[] terms, int[] termId2SensitivePosition,
            int[] termId2InsensitivePosition) {

        numberOfTerms = terms.length;
        this.termId2SensitivePosition = termId2SensitivePosition;
        this.termId2InsensitivePosition = termId2InsensitivePosition;

        assert this.termId2SensitivePosition.length == numberOfTerms;
        assert this.termId2InsensitivePosition.length == numberOfTerms;

        TIntObjectHashMap<IntList> insensitivePosition2TermIds = new TIntObjectHashMap<>(numberOfTerms);
        int numGroupsThatAreNotSizeOne = 0;
        try (BlockTimer ignored = BlockTimer.create(LOG_TIMINGS, name + ": finish > invert mapping")) {
            // Invert the mapping of term id-> insensitive sort position into insensitive sort position -> term ids
            IntArrayList newList = new IntArrayList(1);
            for (int termId = 0; termId < this.termId2InsensitivePosition.length; ++termId) {
                int insensitivePosition = this.termId2InsensitivePosition[termId];

                IntList termIdsForInsensitivePosition =
                        insensitivePosition2TermIds.putIfAbsent(insensitivePosition, newList);
                if (termIdsForInsensitivePosition == null) {
                    // There was no list associated with this insensitivePosition yet, so our new list was used.
                    termIdsForInsensitivePosition = newList;
                    newList = new IntArrayList(1); // prepare a new list for next time
                }
                // add at index 0 to stay consistent with previous code...
                // (probably not needed? replace with just add()? then the first term in the group will be the first
                //  term encountered as opposed to the last, makes more sense?)
                termIdsForInsensitivePosition.add(0, termId);
                if (termIdsForInsensitivePosition.size() == 2)
                    ++numGroupsThatAreNotSizeOne;

//                IntList v = new IntArrayList(1);
//                v.add(termId);
//                IntList prev = insensitivePosition2TermIds.put(insensitivePosition, v);
//                if (prev != null) {
//                    v.addAll(prev);
//                    if (prev.size() == 1)
//                        ++numGroupsThatAreNotSizeOne;
//                }


            }
        }

        try (BlockTimer ignored = BlockTimer.create(LOG_TIMINGS, name + ": finish > fillTermDataGroups")) {
            fillTermDataGroups(insensitivePosition2TermIds, numGroupsThatAreNotSizeOne);
        }

        if (DEBUGGING && DEBUG_VALIDATE_SORT) {
            // Verify sorts
            String prev = null;
            for (int i = 0; i < sensitivePosition2GroupId.length; i++) {
                int groupId = sensitivePosition2GroupId[i];
                assert groupId >= 0;
                assert groupId2TermIds[groupId] == 1;
                int termId = groupId2TermIds[groupId + 1];
                assert termId >= 0 && termId < terms.length;
                String term = terms[termId];
                if (prev != null)
                    assert collator.compare(prev, term) == -1;
                prev = term;
            }
            prev = null;
            int prevTermId = -1;
            for (int i = 0; i < insensitivePosition2GroupId.length; i++) {
                int groupId = insensitivePosition2GroupId[i];
                assert groupId >= 0;
                int termId = groupId2TermIds[groupId + 1];
                if (prevTermId != -1 && termId != prevTermId) {
                    String firstTerm = terms[termId];
                    if (prev != null)
                        assert collatorInsensitive.compare(prev, firstTerm) <= 0;
                    prev = firstTerm;
                }
                prevTermId = termId;
            }
        }

        try (BlockTimer ignored = BlockTimer.create(LOG_TIMINGS, name + ": finish > fillTermCharData")) {
            fillTermCharData(terms);
        }
    }

    // OPT: optimize by removing the 1 at groupId < terms.length
    //   Since we know it's always there (no collisions in this section - length is always 1)
    /**
     * Initializes the following members:
     * - {@link #termId2SensitivePosition}
     * - {@link #termId2InsensitivePosition}
     * - {@link #groupId2TermIds}
     * - {@link #sensitivePosition2GroupId}
     * - {@link #insensitivePosition2GroupId}
     *
     * @param numGroupsThatAreNotSizeOne in the insensitive hashmap - used to initialize the groupId2termIds map at the right length.
     */
    protected void fillTermDataGroups(TIntObjectHashMap<IntList> insensitivePosition2TermIds,
            int numGroupsThatAreNotSizeOne) {

        // This is a safe upper bound: one group per sensitive (with one entry) = 2*numberOfTerms.
        // Then for the insensitive side, one group per entry in insensitiveSortPosition2TermIds + 1 int for each term
        // in reality this is the maximum upper bound.
        // Because we know how many insensitive groups are size 1 (and can therefore re-use the sensitive group),
        // we can lower it a bit.
        int numberOfIntsForSensitiveEntries = numberOfTerms * 2;
        int numInsensitiveGroupsOfSizeOne = insensitivePosition2TermIds.size() - numGroupsThatAreNotSizeOne;
        int numTermsInGroupsAboveSizeOne = numberOfTerms - numInsensitiveGroupsOfSizeOne;
        int numberOfIntsForInsensitiveEntries = numGroupsThatAreNotSizeOne + numTermsInGroupsAboveSizeOne;
        int maxNumberOfGroups = numberOfIntsForSensitiveEntries + numberOfIntsForInsensitiveEntries;

        this.groupId2TermIds = new int[maxNumberOfGroups];

        this.insensitivePosition2GroupId = new int[numberOfTerms]; // NOTE: since not every insensitive sort position exists, this will have empty spots
        Arrays.fill(this.insensitivePosition2GroupId, -1);

        this.sensitivePosition2GroupId = new int[numberOfTerms];
        Arrays.fill(this.sensitivePosition2GroupId, -1);

        // First create all sensitive entries
        int offset = 0;
        for (int termId = 0; termId < numberOfTerms; ++termId) {
            final int positionSensitive = termId2SensitivePosition[termId];

            this.sensitivePosition2GroupId[positionSensitive] = offset;
            this.groupId2TermIds[offset++] = 1; // sensitive positions are unique (1 per term) - so group is size always 1
            this.groupId2TermIds[offset++] = termId; // and contains this term.
        }

        // now place all insensitives
        TIntObjectIterator<IntList> it = insensitivePosition2TermIds.iterator();
        while (it.hasNext()) {
            it.advance();

            final int insensitivePosition = it.key();
            final IntList termIds = it.value();
            final int numTermIds = termIds.size();

            // reuse sensitive group when it contains the same data
            if (numTermIds == 1) {
                final int termId = termIds.getInt(0);
                final int sensitivePosition = this.termId2SensitivePosition[termId];
                final int groupId = this.sensitivePosition2GroupId[sensitivePosition];

                this.insensitivePosition2GroupId[insensitivePosition] = groupId;
                continue;
            }

            // cannot share group - not the same members. Create a new one
            this.insensitivePosition2GroupId[insensitivePosition] = offset;
            this.groupId2TermIds[offset++] = numTermIds;
            for (int i = 0; i < numTermIds; ++i) {
                groupId2TermIds[offset++] = termIds.getInt(i);
            }
        }

        // fill empty spots using the last good entry
        // if we don't do this binary searching over this array won't work (as it contains random uninitialized values
        // and if we land on one of them we'd compare wrong)
        int last = 0;
        for (int i = 0; i < this.insensitivePosition2GroupId.length; ++i) {
            if (this.insensitivePosition2GroupId[i] != -1)
                last = this.insensitivePosition2GroupId[i];
            else
                this.insensitivePosition2GroupId[i] = last;
        }

        if (offset < groupId2TermIds.length) {
            throw new RuntimeException("what is going on here");
        }
    }

    /**
     * Converts terms string array to byte data and stored offsets.
     *
     * Initializes
     * - {@link #termCharData}
     * - {@link #termId2CharDataOffset}
     */
    protected void fillTermCharData(String[] terms) {
        // convert all to byte[] and tally total number of bytes
        // free the String instances while doing this so memory usage doesn't spike so much
        this.termCharData = new ByteBigArrayBigList();
        long bytesWritten = 0;
        this.termId2CharDataOffset = new long[numberOfTerms];
        for (int i = 0; i < numberOfTerms; ++i) {
            this.termId2CharDataOffset[i] = bytesWritten;
            byte[] bytes = terms[i].getBytes(DEFAULT_CHARSET);
            byte[][] bb = BigArrays.wrap(bytes);
            termCharData.addElements(bytesWritten, bb);
            bytesWritten += bytes.length;
        }
        this.termCharData.trim(); // clear extra space.
    }

    @Override
    public int indexOf(String term) {
        final int groupId = getGroupId(term, MatchSensitivity.SENSITIVE);
        if (groupId == -1)
            return -1;
        // Return the first term in this group
        return this.groupId2TermIds[groupId + 1];
    }

    @Override
    public void indexOf(MutableIntSet results, String term, MatchSensitivity sensitivity) {
        final int groupId = getGroupId(term, sensitivity);
        if (groupId == -1) {
            results.add(-1);
            return;
        }

        final int groupSize = this.groupId2TermIds[groupId];
        for (int i = 0; i < groupSize; ++i) {
            results.add(this.groupId2TermIds[groupId + 1 + i]);
        }
    }

    @Override
    public int numberOfTerms() {
        return numberOfTerms;
    }

    @Override
    public int idToSortPosition(int id, MatchSensitivity sensitivity) {
        return sensitivity.isCaseSensitive() ? this.getSortPositionSensitive(id) : this.getSortPositionInsensitive(id);
    }

    private int getSortPositionSensitive(int termId) {
        if (termId < 0 || termId >= numberOfTerms) {
            return -1;
        }
        return this.termId2SensitivePosition[termId];
    }

    private int getSortPositionInsensitive(int termId) {
        if (termId < 0 || termId >= numberOfTerms) {
            return -1;
        }
        return this.termId2InsensitivePosition[termId];
    }

    @Override
    public String get(int id) {
        if (id >= numberOfTerms || id < 0) {
            return "";
        }
        boolean isLastId = id == (this.termId2CharDataOffset.length - 1);
        long start = this.termId2CharDataOffset[id];
        long end = (isLastId ? this.termCharData.size64() : this.termId2CharDataOffset[id+1]);
        int length = (int) (end-start);

        byte[] out = new byte[length];
        this.termCharData.getElements(start, out, 0, length);
        return new String(out, DEFAULT_CHARSET);
    }

    @Override
    public boolean termsEqual(int[] termId, MatchSensitivity sensitivity) {
        if (termId.length < 2)
            return true;

        // sensitive compare - just get the sort index
        if (sensitivity.isCaseSensitive()) {
            int expected = getSortPositionSensitive(termId[0]);
            for (int termIdIndex = 1; termIdIndex < termId.length; ++termIdIndex) {
                int cur = getSortPositionSensitive(termId[termIdIndex]);
                if (cur != expected)
                    return false;
            }
            return true;
        }

        // insensitive compare - get the insensitive sort index
        int expected = getSortPositionInsensitive(termId[0]);
        for (int termIdIndex = 1; termIdIndex < termId.length; ++termIdIndex) {
            int cur = getSortPositionInsensitive(termId[termIdIndex]);
            if (cur != expected)
                return false;
        }
        return true;
    }

    private int getGroupId(String term, MatchSensitivity sensitivity) {
        final Collator coll = sensitivity.isCaseSensitive() ? this.collator : this.collatorInsensitive;
        final int[] sortPosition2GroupId = sensitivity.isCaseSensitive() ?
                this.sensitivePosition2GroupId :
                this.insensitivePosition2GroupId;

        // binary search
        int l = 0;
        int r = sortPosition2GroupId.length - 1;

        int matchingGroupId = -1;
        while (l <= r) {

            final int sortPositionToCheck = l + (r - l) / 2;
            final int groupId = sortPosition2GroupId[sortPositionToCheck];
            final int termIdToCompareTo = this.groupId2TermIds[groupId + 1]; // OPT: < numterms optimization
            final String termToCompareTo = get(termIdToCompareTo);

            final int result = coll.compare(term, termToCompareTo);

            if (result == 0) {
                matchingGroupId = groupId;
                break;
            }

            if (result < 0) {
                // position we're looking for is before this result, move right (upper) boundary to just before current position
                r = sortPositionToCheck - 1;
            } else {
                // position we're looking for is after this result, move left (lower) boundary to just after current position
                l = sortPositionToCheck + 1;
            }
        }

        return matchingGroupId;
    }

    private void printSensitivityInformation(MatchSensitivity sens) {
        int[] groupMapping = sens.equals(MatchSensitivity.SENSITIVE) ? this.sensitivePosition2GroupId : this.insensitivePosition2GroupId;
        System.out.println("----- " + sens + " -----");
        for (int i = 0; i < groupMapping.length; ++i) {
            int groupID = this.sensitivePosition2GroupId[i];
            int groupSize = this.groupId2TermIds[groupID];
            int[] groupContents = new int[groupSize];
            System.arraycopy(this.groupId2TermIds, groupID + 1, groupContents, 0, groupSize);

            String[] terms = new String[groupSize];
            for (int j = 0; j < groupContents.length; ++j) terms[j] = this.get(groupContents[j]);
            System.out.println(i + "\t" + Arrays.toString(terms));
        }
    }

    public void printDebugInformation() {
        System.out.println("---- debug information for Terms -----");
        System.out.println("----- Per ID -----");
        for (int i = 0; i < this.numberOfTerms; ++i) {
            String term = this.get(i);
            int sensitive = this.termId2SensitivePosition[i];
            int insensitive = this.termId2InsensitivePosition[i];
            System.out.println(i + "\t" + term + "\t" + sensitive + "\t" + insensitive);
        }
        this.printSensitivityInformation(MatchSensitivity.SENSITIVE);
        this.printSensitivityInformation(MatchSensitivity.INSENSITIVE);
    }
}
