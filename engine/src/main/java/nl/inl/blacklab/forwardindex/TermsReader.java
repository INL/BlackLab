package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class TermsReader extends Terms {

    protected static final Logger logger = LogManager.getLogger(TermsReader.class);

    protected final File termsFile;

    protected final ThreadLocal<int[]> arrayAndOffsetAndLength = ThreadLocal.withInitial(() -> new int[3]);

    /**
     * Encodes 4 values across 2 longs for every term: [sortpossensitive 32, sortposinsensitive 32, offset 64]. Access using termId*2
     * To get the term's length, subtract the offset from the next term's offset. For the last term, subtract the offset from the array length.
     * TODO: just split this into multiple dedicated arrays...
     */
    protected long[] termData;
    /** Concatenated strings of all terms known to us. Get offset and length for a specific term from the {@link #termData} array */
    protected byte[][] termCharData;

    /** For a term's hash, contains an index into the {@link #termDataGroups array}, in which the term ids for that string hash can be found. */
    final protected TIntIntHashMap stringHash2TermDataGroupsIndex = new TIntIntHashMap(10, 0.75f, -1, -1);
    final protected TObjectIntHashMap<byte[]> collationKey2TermDataGroupsIndex = new TObjectIntHashMap<byte[]>(10, 0.75f, -1) {
        // Important: override this!
        protected int hash(Object notnull) { return Arrays.hashCode((byte[]) notnull); }
        protected boolean equals(Object notnull, Object two) { return two != null && Arrays.equals((byte[]) notnull, (byte[]) two); }
    };
    /**
     * Go from a string to an index into this array through the {@link #stringHash2TermDataGroupsIndex} or {@link #collationKey2TermDataGroupsIndex}
     * This array then contains a number of ints: the first is the count of term IDs that follow. Then are that number of term ids.
     * a = [offset+0] = number of term ids in the group
     * b = [offset+(1 to a)] = term ids for use in the termData array (this limits us to 2^30-1 terms = ~1072M)
     */
    protected int[] termDataGroups; // [count, index...]

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

    @Override
    public int indexOf(String term) {
        return getTermId(term, MatchSensitivity.SENSITIVE);
    }

    @Override
    public void indexOf(MutableIntSet results, String term, MatchSensitivity sensitivity) {
        if (sensitivity.isCaseSensitive()) {
            results.add(getTermId(term, sensitivity));
        }

        // insensitive
        final int groupId = collationKey2TermDataGroupsIndex.get(collatorInsensitive.getCollationKey(term).toByteArray());
        if (groupId == -1) {
            results.add(-1);
            return;
        }
        final int numMatchedTerms = termDataGroups[groupId];
        for (int i = 1; i <= numMatchedTerms; ++i) {
            results.add(termDataGroups[groupId + i]);
        }
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
    public String get(int id) {
        if (id >= numberOfTerms || id < 0) { return ""; }
        final int[] arrayAndOffsetAndLength = getOffsetAndLength(id);
        return new String(termCharData[arrayAndOffsetAndLength[0]], arrayAndOffsetAndLength[1], arrayAndOffsetAndLength[2], DEFAULT_CHARSET);
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
        final long offset = termData[termId * 2 + 1];
        final int arrayIndex = (int) (offset >> 32); // int cast does the floor() for us, that's good.
        final int indexInArray = (int) (offset & 0xffffffffL); // only keep upper 32 bits

        final boolean isLastTerm = termId == (numberOfTerms - 1);
        final int length = (int) (isLastTerm ? termCharData[arrayIndex].length - offset : termData[(termId + 1) * 2 + 1] - offset);

        arrayAndOffsetAndLength[0] = arrayIndex;
        arrayAndOffsetAndLength[1] = indexInArray;
        arrayAndOffsetAndLength[2] = length;
        return arrayAndOffsetAndLength;
    }


    @Override
    public int numberOfTerms() {
        return numberOfTerms;
    }

    @Override
    public int idToSortPosition(int id, MatchSensitivity sensitivity) {
        return sensitivity.isCaseSensitive() ? getSortPositionSensitive(id) : getSortPositionInsensitive(id);
    }

    protected void setBlockBasedFile(boolean useBlockBasedTermsFile) {
        this.useBlockBasedTermsFile = useBlockBasedTermsFile;
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

    private void read(FileChannel fc) throws IOException {
        logger.debug("Initializing termsreader " + termsFile);
        final long start = System.nanoTime();

        long fileLength = termsFile.length();
        IntBuffer ib = readFromFileChannel(fc, fileLength);

        // now build the insensitive sorting positions..

        // Read the sort order arrays
        int[] sortPositionSensitive = new int[numberOfTerms];
        int[] sortPositionInsensitive = new int[numberOfTerms];
        ib.position(ib.position() + numberOfTerms); // Advance past unused sortPos -> id array (left in there for file compatibility)
        ib.get(sortPositionSensitive);
        ib.position(ib.position() + numberOfTerms); // Advance past unused sortPos -> id array (left in there for file compatibility)
        ib.get(sortPositionInsensitive);
        ib = null; // garbage collect option


        TIntObjectHashMap<IntArrayList> stringHash2TermIds = new TIntObjectHashMap<>();
        THashMap<byte[], IntArrayList> collationKeyBytes2TermIds = new THashMap<byte[], IntArrayList>() {
            protected int hash(Object notnull) { return Arrays.hashCode((byte[]) notnull); };
        };


        prepareMapping2TermIds(stringHash2TermIds, collationKeyBytes2TermIds);

        fillTermDataGroups(stringHash2TermIds, collationKeyBytes2TermIds, terms);
        final long[] chardataOffsets = fillTermCharData(terms);
        fillTermData(sortPositionSensitive, sortPositionInsensitive, chardataOffsets);
        terms = null;

        logger.debug("finishing initializing termsreader " + termsFile + " - " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + "ms to process " + numberOfTerms + " terms");
    }

    private void prepareMapping2TermIds(TIntObjectHashMap<IntArrayList> stringHash2TermIds, THashMap<byte[], IntArrayList> collationKeyBytes2TermIds) {
        for (int termId = 0; termId < terms.length; ++termId) {
            final String term = terms[termId];
            final byte[] collationKeyBytes = collatorInsensitive.getCollationKey(term).toByteArray();
            final int hash = term.hashCode();

            if (!stringHash2TermIds.containsKey(hash)) {
                final IntArrayList newList = new IntArrayList(1);
                newList.add(termId);
                stringHash2TermIds.put(hash, newList);
            }
            else
                stringHash2TermIds.get(hash).add(termId);

            if (!collationKeyBytes2TermIds.containsKey(collationKeyBytes)) {
                final IntArrayList newList = new IntArrayList(1);
                newList.add(termId);
                collationKeyBytes2TermIds.put(collationKeyBytes, newList);
            }
            else
                collationKeyBytes2TermIds.get(collationKeyBytes).add(termId);
        }
    }

    /*
     * creates/fills the following
     * - termCharData
     *
     * returns the offset + length of all terms.
     */
    private long[] fillTermCharData(String[] terms) {
        long[] termOffsets = new long[terms.length];

        long bytesRemainingToBeWritten = 0;
        for (final String t : terms) { bytesRemainingToBeWritten += t.getBytes(DEFAULT_CHARSET).length; }

        byte[][] termCharData = new byte[0][];
        byte[] curArray;
        for (int termIndex = 0; termIndex < terms.length; ++termIndex) {
            // allocate new term bytes array, subtract what will fit
            final int curArrayLength = (int) Long.min(bytesRemainingToBeWritten, (long) Integer.MAX_VALUE);
            curArray = new byte[curArrayLength];

            // now write terms until the array runs out of space or we have written all remaining terms
            int offset = 0;
            while (termIndex < terms.length) {
                final byte[] termBytes = terms[termIndex].getBytes(DEFAULT_CHARSET);
                if ((offset + termBytes.length) > curArrayLength) { --termIndex; /* note we didn't write this term yet, so re-process it next iteration */ break; }

                termOffsets[termIndex] = offset;

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
        return termOffsets;
    }

    /*
     * creates/fills the following
     * - termData
     */
    private void fillTermData(int[] sortPositionSensitive, int[] sortPositionInsensitive, long[] charDataOffsets) {
        termData = new long[numberOfTerms * 2];
        for (int i = 0; i < numberOfTerms; ++i) {
            termData[i*2] = (((long)sortPositionSensitive[i]) << 32) | (sortPositionInsensitive[i] & 0xffffffffL); // don't just cast, or sign bit might become set and it lies outside the upper 32 bits.
            termData[i*2+1] = charDataOffsets[i];
        }
    }

    /*
     * creates/fills the following
     * - stringHash2TermDataGroupsIndex
     * - collationKey2TermDataGroupsIndex
     * - termDataGroups
     */
    private void fillTermDataGroups(TIntObjectHashMap<IntArrayList> stringHash2TermIds, THashMap<byte[], IntArrayList> collationKeyBytes2TermIds, String[] terms) {
        // hmm, the size should logically be 2 entries for all terms (once in the sensitive side, once in the insensitive side) + 1 per group
        termDataGroups = new int[stringHash2TermIds.size() + collationKeyBytes2TermIds.size() + numberOfTerms * 2];

        int offset = 0;
        TIntObjectIterator<IntArrayList> it = stringHash2TermIds.iterator();
        while (it.hasNext()) {
            it.advance();

            final int stringHash = it.key();
            final IntArrayList termIds = it.value();
            final int numTermIds = termIds.size();

            stringHash2TermDataGroupsIndex.put(stringHash, offset);
            termDataGroups[offset++] = numTermIds;
            for (int i = 0; i < numTermIds; ++i) {
                termDataGroups[offset++] = termIds.get(i);
            }
        }

        // We have the term id, the terms[] array is still intact,
        // which means we can compute the other one and find it.
        // the hash is fairly fast too, so it shouldn't slow us down much.

        int collapsedGroups = 0;
        Iterator<Entry<byte[], IntArrayList>> it2 = collationKeyBytes2TermIds.entrySet().iterator();
        while (it2.hasNext()) {
            final Entry<byte[], IntArrayList> e = it2.next();
            final byte[] collationKeyBytes = e.getKey();
            final IntArrayList termIds = e.getValue();
            final int numTermIds = termIds.size();

            if (numTermIds == 1) { // see if we can collapse with the hash entry for this term id
                final int stringHashForThisTerm  = terms[termIds.get(0)].hashCode();
                final IntArrayList termIdsForStringHash = stringHash2TermIds.get(stringHashForThisTerm);
                final boolean stringHashPointsToSingleTerm = termIdsForStringHash.size() == 1;
                if (stringHashPointsToSingleTerm) { // cool, since this collationkey also points to a single term (the same one), we can reuse the entry in the termDataGroups array
                    collationKey2TermDataGroupsIndex.put(collationKeyBytes, stringHash2TermDataGroupsIndex.get(stringHashForThisTerm));
                    ++collapsedGroups;
                    continue;
                }
            }

            collationKey2TermDataGroupsIndex.put(collationKeyBytes, offset);
            termDataGroups[offset++] = numTermIds;
            for (int i = 0; i < numTermIds; ++i) {
                termDataGroups[offset++] = termIds.get(i);
            }
        }

        // Reclaim the size
        final int sizeBefore = termDataGroups.length;
        final int sizeAfter = offset;
        logger.debug("Saving " + (sizeBefore - sizeAfter)/1024/1024*4 + "MB by collapsing " + collapsedGroups + " dataGroups - cool!");
        final int[] trimmedTermDataGroups = new int[sizeAfter];
        System.arraycopy(termDataGroups, 0, trimmedTermDataGroups, 0, sizeAfter);
        termDataGroups = trimmedTermDataGroups;
    }

    private int getSortPositionSensitive(int termId) {
        if (termId < 0 || termId >= numberOfTerms) { return -1; }
        final long sortPositionData = termData[termId * 2];
        return (int) (sortPositionData >> 32);
    }

    private int getSortPositionInsensitive(int termId) {
        if (termId < 0 || termId >= numberOfTerms) { return -1; }
        final long sortPositionData = termData[termId * 2];
        return (int) (sortPositionData & 0xffffffffL);
    }

    private int getTermId(String term, MatchSensitivity sensitivity) {
        if (sensitivity.isCaseSensitive()) {
            final int groupIndex = stringHash2TermDataGroupsIndex.get(term.hashCode());
            if (groupIndex == -1) { return -1; } // unknown term.

            // Found one or more terms with this string hash - find the one that actually has the correct string
            final int numTermsWithThisStringHash = termDataGroups[groupIndex];
            final byte[] termBytes = term.getBytes(DEFAULT_CHARSET); //compare bytes in the strings, more LOC but faster since we don't have to build a new string from the termCharData arrays


            for (int i = 1; i <= numTermsWithThisStringHash; ++i) {
                final int termId = termDataGroups[groupIndex + i];
                final int[] arrayAndOffsetAndLength = getOffsetAndLength(termId);
                if (memcmp(termCharData[arrayAndOffsetAndLength[0]], arrayAndOffsetAndLength[1], termBytes, 0, arrayAndOffsetAndLength[2]))
                    return termDataGroups[groupIndex + i];
            }
            // There was a matching hash, but it was a hash collision, and the requested term is not one we have stored...
            return -1;
        }

        // insensitive compare.
        // This can sometimes match more than one term id, return the first one.
        final int groupIndex = collationKey2TermDataGroupsIndex.get(collatorInsensitive.getCollationKey(term).toByteArray());
        if (groupIndex == -1) { return -1; } // unknown term
        return termDataGroups[groupIndex + 1]; // no need to match, collationkey does not suffer from hash collisions so it's guaranteed the same term.
    }


    /** NOTE: assumes offset + length do not go out of bounds */
    private static boolean memcmp(byte[] a, int offsetA, byte[] b, int offsetB, int length) {
        for (int i = 0; i < length; ++i) {
            if (a[offsetA + i] != b[offsetB + i]) return false;
        }
        return true;
    }
}
