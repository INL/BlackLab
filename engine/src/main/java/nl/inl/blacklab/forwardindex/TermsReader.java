package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.text.Collator;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.api.set.primitive.MutableIntSet;

import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.TIntObjectHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.BlockTimer;

public class TermsReader extends Terms {

    protected static final Logger logger = LogManager.getLogger(TermsReader.class);

    protected final File termsFile;

    protected final ThreadLocal<int[]> arrayAndOffsetAndLength = ThreadLocal.withInitial(() -> new int[3]);

    private int[] insensitivePosition2GroupId;
    private int[] sensitivePosition2GroupId;
    private int[] termId2SensitivePosition;
    private int[] termId2InsensitivePosition;

    /**
     * Contains a leading int specifying how many ids for a given group, followed by the list of ids.
     * For a group of size 2 containing the ids 4 and 8, contains [...2, 4, 9, ...]
     * insensitivePosition2GroupId and sensitivePosition2GroupId contain the index of the leading int
     * in this array for all sensitive/insensitive sorting positions respectively.
     */
    private int[] groupId2TermIds;

    private byte[][] termCharData;
    /**
     * Lower 32 bits indicate the array, upper 32 bits indicate the index within the {@link #termCharData} array.
     * This is needed to allow more than 2gb of term character data
     */
    private long[] termId2CharDataOffset;

    public TermsReader(Collators collators, File termsFile, boolean useBlockBasedTermsFile, boolean buildTermIndexesOnInit) {
        this.termsFile = termsFile;
        this.useBlockBasedTermsFile = useBlockBasedTermsFile;
        this.collator = collators.get(MatchSensitivity.SENSITIVE);
        this.collatorInsensitive = collators.get(MatchSensitivity.INSENSITIVE);

        try (RandomAccessFile raf = new RandomAccessFile(termsFile, "r")) {
            try (FileChannel fc = raf.getChannel()) {
                read(fc);
            }
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    private void read(FileChannel fc) throws IOException {
        try (BlockTimer t = BlockTimer.create("Initializing terms " + this.termsFile)) {
            logger.debug("Initializing termsreader " + termsFile);
            final long start = System.nanoTime();

            long fileLength = termsFile.length();
            IntBuffer ib = readFromFileChannel(fc, fileLength);

            int[] termId2SensitivePosition = new int[numberOfTerms];
            int[] termId2InsensitivePosition = new int[numberOfTerms];

            // Read the sort order arrays
            ib.position(ib.position() + numberOfTerms); // Advance past unused sortPos -> id array (left in there for file compatibility)
            ib.get(termId2SensitivePosition);
            ib.position(ib.position() + numberOfTerms); // Advance past unused sortPos -> id array (left in there for file compatibility)
            ib.get(termId2InsensitivePosition);
            ib = null; // garbage collect option

            // Invert the mapping of term id-> insensitive into insensitive -> term ids
            int numGroupsThatAreNotSizeOne = 0;
            TIntObjectHashMap<IntArrayList> insensitivePosition2TermIds = new TIntObjectHashMap<>(terms.length);
            for(int termId = 0; termId < termId2InsensitivePosition.length; ++termId) {
                int insensitivePosition = termId2InsensitivePosition[termId];
                IntArrayList v = new IntArrayList(1);
                v.add(termId);

                IntArrayList prev = insensitivePosition2TermIds.put(insensitivePosition, v);
                if (prev != null) {
                    v.addAll(prev);

                    if (prev.size() == 1)
                        ++numGroupsThatAreNotSizeOne;
                }
            }

            fillTermDataGroups(termId2SensitivePosition, termId2InsensitivePosition, insensitivePosition2TermIds, numGroupsThatAreNotSizeOne);
            fillTermCharData(this.terms);
            this.terms = null;

            logger.debug("finishing initializing termsreader " + termsFile + " - " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + "ms to process " + numberOfTerms + " terms");
        }
    }

    // TODO optimize by removing the 1 at groupId < terms.length
    // Since we know it's always there (no collisions in this section - length is always 1)
    /**
     * Initializes the following members:
     * - {@link #termId2SensitivePosition}
     * - {@link #termId2InsensitivePosition}
     * - {@link #groupId2TermIds}
     * - {@link #sensitivePosition2GroupId}
     * - {@link #insensitivePosition2GroupId}
     *
     * @param termId2SortPositionSensitive
     * @param termId2SortPositionInsensitive
     * @param insensitiveSortPosition2TermIds
     * @param numGroupsThatAreNotSizeOne in the insensitive hashmap - used to initialize the groupId2termIds map at the right length.
     */
    private void fillTermDataGroups(int[] termId2SortPositionSensitive, int[] termId2SortPositionInsensitive, TIntObjectHashMap<IntArrayList> insensitiveSortPosition2TermIds, int numGroupsThatAreNotSizeOne) {
        // This is a safe upper bound: one group per sensitive (with one entry) = 2*numberOfTerms.
        // Then for the insensitive side, one group per entry in insensitiveSortPosition2TermIds + 1 int for each term
        // in reality this is the maximum upper bound.
        // to accurately do this we'd need to know the number of groups with only one entry

        int numGroupsOfSizeOne = insensitiveSortPosition2TermIds.size() - numGroupsThatAreNotSizeOne;
        int numTermsInGroupsAboveSizeOne = terms.length - numGroupsOfSizeOne;

        this.termId2SensitivePosition = termId2SortPositionSensitive;
        this.termId2InsensitivePosition = termId2SortPositionInsensitive;
        // to be filled
        this.groupId2TermIds = new int[terms.length * 2 /* sensitive groups - all size 1 */ + numGroupsThatAreNotSizeOne + numTermsInGroupsAboveSizeOne];
        this.insensitivePosition2GroupId = new int[this.numberOfTerms]; // NOTE: since not every insensitive sort position exists, this will have empty spots
        this.sensitivePosition2GroupId = new int[this.numberOfTerms];
        fill(this.insensitivePosition2GroupId, -1);

        // First create all sensitive entries
        int offset = 0;
        for (int termId = 0; termId < termId2SortPositionSensitive.length; ++termId) {
           final int positionSensitive = termId2SortPositionSensitive[termId];

           this.sensitivePosition2GroupId[positionSensitive] = offset;
           this.groupId2TermIds[offset++] = 1; // sensitive positions are unique (1 per term) - so group is size always 1
           this.groupId2TermIds[offset++] = termId; // and contains this term.
        }

        // now place all insensitives
        TIntObjectIterator<IntArrayList> it = insensitiveSortPosition2TermIds.iterator();
        while (it.hasNext()) {
            it.advance();

            final int insensitivePosition = it.key();
            final IntArrayList termIds = it.value();
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
                groupId2TermIds[offset++] = termIds.getInt(i); // NOTE: became termIds.getInt(0) in move to IntArrayList; probably a typo?
            }
        }

        // fill empty spots using the last good entry
        // if we don't do this binary searching over this array won't work (as it contains random uninitialized values and if we land on one of them we'd compare wrong)
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
     * Initializes
     * - {@link #termCharData}
     * - {@link #termId2CharDataOffset}
     */
    private void fillTermCharData(String[] terms) {
        // convert all to byte[] and tally total number of bytes
        // free the String instances while doing this so memory usage doesn't spike so much
        this.termId2CharDataOffset = new long[numberOfTerms];
        byte[][] bytes = new byte[numberOfTerms][];
        long bytesRemainingToBeWritten = 0;
        for (int i = 0; i < numberOfTerms; ++i) {
            byte[] b = terms[i].getBytes(DEFAULT_CHARSET);
            terms[i] = null;
            bytes[i] = b;
            bytesRemainingToBeWritten += b.length;
        }

        byte[][] termCharData = new byte[0][];
        byte[] curArray;
        for (int termIndex = 0; termIndex < numberOfTerms; ++termIndex) {
            // allocate new term bytes array, subtract what will fit
            final int curArrayLength = (int) Long.min(bytesRemainingToBeWritten, Integer.MAX_VALUE);
            curArray = new byte[curArrayLength];

            // now write terms until the array runs out of space or we have written all remaining terms
            int offset = termCharData.length * Integer.MAX_VALUE; // set to beginning of current array
            while (termIndex < numberOfTerms) {
                final byte[] termBytes = bytes[termIndex];
                if ((offset + termBytes.length) > curArrayLength) { --termIndex; /* note we didn't write this term yet, so re-process it next iteration */ break; }
                bytes[termIndex] = null;  // free original byte[], only do after we verify it can be copied!

                this.termId2CharDataOffset[termIndex] = offset;

                System.arraycopy(termBytes, 0, curArray, offset, termBytes.length);

                offset += termBytes.length;
                ++termIndex;
                bytesRemainingToBeWritten -= termBytes.length;
            }

            // add the (now filled) current array to the set.
            byte[][] tmp = termCharData;
            termCharData = new byte[tmp.length + 1][];
            System.arraycopy(tmp, 0, termCharData, 0, tmp.length);
            termCharData[termCharData.length - 1] = curArray;

            // and go to the top (allocate new array - copy remaining terms..)
        }

        this.termCharData = termCharData;
    }


    @Override
    public void clear() {
        throw new UnsupportedOperationException("Not in write mode");
    }

    @Override
    public void write(File termsFile) {
        throw new UnsupportedOperationException("Not in write mode");
    }

    @Override
    public int indexOf(String term) {
        final int groupId = getGroupId(term, MatchSensitivity.SENSITIVE);
        if (groupId == -1) return -1;
        final int termId = this.groupId2TermIds[groupId + 1];
        return termId;
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
        return sensitivity.isCaseSensitive() ? this.getSortPositionSensitive(id): this.getSortPositionInsensitive(id);
    }

    @Override
    public String get(int id) {
        if (id >= numberOfTerms || id < 0) { return ""; }
        final int[] arrayAndOffsetAndLength = getOffsetAndLength(id);
        return new String(termCharData[arrayAndOffsetAndLength[0]], arrayAndOffsetAndLength[1], arrayAndOffsetAndLength[2], DEFAULT_CHARSET);
    }

    @Override
    public boolean termsEqual(int[] termId, MatchSensitivity sensitivity) {
        if (termId.length < 2)
            return true;

        // sensitive compare - just get the sort index
        if (sensitivity.isCaseSensitive()) {
            int last = getSortPositionSensitive(termId[0]);
            for (int termIdIndex = 1; termIdIndex < termId.length; ++termIdIndex) {
                int cur = getSortPositionSensitive(termId[termIdIndex]);
                if (cur != last) { return false; }
                last = cur;
            }
            return true;
        }

        // insensitive compare - get the insensitive sort index
        int last = getSortPositionInsensitive(termId[0]);
        for (int termIdIndex = 1; termIdIndex < termId.length; ++termIdIndex) {
            int cur = getSortPositionInsensitive(termId[termIdIndex]);
            if (cur != last) { return false; }
            last = cur;
        }
        return true;
    }



    /**
     * Returns the threadlocal arrayAndOffsetAndLength, the array is reused between calls.
     * index 0 contains the char array
     * index 1 contains the offset within the char array
     * index 2 contains the length
     * @param termId
     * @return the
     */
    private int[] getOffsetAndLength(int termId) {
        final int[] arrayAndOffsetAndLength = this.arrayAndOffsetAndLength.get();
        final long offset = this.termId2CharDataOffset[termId];
        final int arrayIndex = (int) (offset >> 32);
        final int indexInArray = (int) (offset & 0xffffffffL); // only keep upper 32 bits

        final boolean isLastTermInArray = termId == (numberOfTerms - 1) || (((int) (this.termId2CharDataOffset[termId + 1] >> 32)) != arrayIndex);
        int length = 0;
        if (isLastTermInArray) {
            final byte[] relevantArray = termCharData[arrayIndex];
            // find first null byte, that will terminate the string (or else until the border of the array)
            while (relevantArray.length > (indexInArray + length) && relevantArray[indexInArray + length] != 0) { ++length; }
        } else {
            length = (int) (termId2CharDataOffset[termId + 1] - offset);
        }

        arrayAndOffsetAndLength[0] = arrayIndex;
        arrayAndOffsetAndLength[1] = indexInArray;
        arrayAndOffsetAndLength[2] = length;
        return arrayAndOffsetAndLength;
    }

    private int getGroupId(String term, MatchSensitivity sensitivity) {
        final Collator coll = sensitivity.isCaseSensitive() ? this.collator : this.collatorInsensitive;
        final int[] sortPosition2GroupId = sensitivity.isCaseSensitive() ? this.sensitivePosition2GroupId : this.insensitivePosition2GroupId;

        // binary search
        int l = 0;
        int r = sortPosition2GroupId.length - 1;

        int matchingGroupId = -1;
        while (l <= r) {
            final int sortPositionToCheck = l + (r - l) / 2;
            final int groupId = sortPosition2GroupId[sortPositionToCheck];
            final int termIdToCompareTo = this.groupId2TermIds[groupId + 1]; // TODO < numterms optimization
            final String termToCompareTo = get(termIdToCompareTo);

            final int result = coll.compare(term, termToCompareTo);
            if (result == 0) {
                matchingGroupId = groupId;
                break;
            }

            if (result < 0) { // position we're looking for is before this result, move right (upper) boundary to just before current position
                r = sortPositionToCheck - 1;
            } else { // position we're looking for is after this result, move left (lower) boundary to just after current position
                l = sortPositionToCheck + 1;
            }
        }

        return matchingGroupId;
    }


    private int getSortPositionSensitive(int termId) {
        if (termId < 0 || termId >= numberOfTerms) { return -1; }
        return this.termId2SensitivePosition[termId];
    }

    private int getSortPositionInsensitive(int termId) {
        if (termId < 0 || termId >= numberOfTerms) { return -1; }
        return this.termId2InsensitivePosition[termId];
    }

    // https://stackoverflow.com/a/25508988
    private static void fill(int[] array, int value) {
      int len = array.length;

      if (len > 0){
        array[0] = value;
      }

      //Value of i will be [1, 2, 4, 8, 16, 32, ..., len]
      for (int i = 1; i < len; i += i) {
        System.arraycopy(array, 0, array, i, ((len - i) < i) ? (len - i) : i);
      }
    }
}
