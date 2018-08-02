/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.text.CollationKey;
import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.Maps;

import net.jcip.annotations.NotThreadSafe;

/**
 * Keeps a first-come-first-serve list of unique terms. Each term gets a unique
 * index number. These numbers are stored in the forward index to conserve space
 * and allow quick lookups of terms occurring in specific positions.
 *
 * This version of the class stores the terms in a more efficient way so it
 * saves and loads faster, and includes the case-insensitive sorting order.
 * 
 * Thread-safety: not thread-safe in index mode, but thread-safe while searching
 */
@NotThreadSafe // in index mode
class TermsImplV3 extends Terms {
    /**
     * We set this to a lower value on Windows because we can't properly truncate
     * the file due to the file still being mapped (there is no clean way to unmap a
     * mapped file in Java, and Windows doesn't allow truncating a mapped file). The
     * lower value on Windows prevents too much wasted space.
     */
    private static final int DEFAULT_MAX_MAP_SIZE = File.separatorChar == '\\' ? 100000000 : Integer.MAX_VALUE - 100;

    /** Maximum size for blocks of term strings. */
    private static final int DEFAULT_MAX_BLOCK_SIZE = DEFAULT_MAX_MAP_SIZE;

    /**
     * Number of sort buffers we store in the terms file (case-sensitive/insensitive
     * and inverted buffers for both as well)
     */
    private static final int NUM_SORT_BUFFERS = 4;

    /** Number of bytes per int */
    private static final int BYTES_PER_INT = Integer.SIZE / Byte.SIZE;

    protected static final Logger logger = LogManager.getLogger(TermsImplV3.class);

    /** First index in array and number of elements from array */
    static class FirstAndNumber {
        public int first;

        public int number;

        public FirstAndNumber(int first, int number) {
            this.first = first;
            this.number = number;
        }

    }

    /** How many terms total are there? (always valid) */
    int numberOfTerms = 0;

    /** Search mode only: the terms, by index number. */
    String[] terms;

    /**
     * The index number of each sorting position. Inverse of sortPositionPerId[]
     * array. Only valid when indexMode == false.
     */
    int[] idPerSortPosition;

    /**
     * The index number of each case-insensitive sorting position. Inverse of
     * sortPositionPerIdInsensitive[] array. Only valid when indexMode == false.
     */
    int[] idPerSortPositionInsensitive;

    /**
     * The sorting position for each index number. Inverse of idPerSortPosition[]
     * array. Only valid when indexMode == false.
     */
    int[] sortPositionPerId;

    /**
     * The case-insensitive sorting position for each index number. Only valid when
     * indexMode == false.
     */
    int[] sortPositionPerIdInsensitive;

    /**
     * Mapping from term to its unique index number. We use a SortedMap because we
     * wish to store the sorted index numbers later (to speed up sorting).
     */
    Map<CollationKey, Integer> termIndex;

    /**
     * The first index in the sortPositionPerIdInsensitive[] array that matches each
     * term, and the number of matching terms that follow. Used while building NFAs
     * to quickly fetch all indices matching a term case-insensitively. Only valid
     * in search mode.
     */
    Map<CollationKey, FirstAndNumber> termIndexInsensitive;

    /**
     * If true, we're indexing data and adding terms. If false, we're searching and
     * just retrieving terms.
     */
    private boolean indexMode;

    /** If true, termIndex is a valid mapping from term to term id. */
    private boolean termIndexBuilt;

    /**
     * Collator to use for string comparisons
     */
    final Collator collator;

    /**
     * Collator to use for insensitive string comparisons
     */
    Collator collatorInsensitive;

    /** Use new blocks-based terms file, that can grow larger than 2 GB? */
    private boolean useBlockBasedTermsFile = true;

    /**
     * The maximum block size to use while writing the terms file. Ususally around
     * the limit of 2GB, but for testing, we can set this to a lower value.
     */
    private int maxBlockSize = DEFAULT_MAX_BLOCK_SIZE;

    /**
     * The maximum block size to use while writing the terms file. Ususally around
     * the limit of 2GB, but for testing, we can set this to a lower value. Note
     * that this should be significantly larger than maxBlockSize, because we also
     * need to store offsets.
     */
    private int maxMapSize = DEFAULT_MAX_MAP_SIZE;

    TermsImplV3(boolean indexMode, Collators collators, File termsFile, boolean useBlockBasedTermsFile) {
        this.indexMode = indexMode;
        this.collator = collators.get(true, true);
        this.collatorInsensitive = collators.get(false, false);

        if (indexMode) {
            // Index mode: create a SortedMap based on the specified Collator.
            // (used later to get the terms in sort order)
            this.termIndex = new TreeMap<>();
            this.termIndexInsensitive = null;
        } else {
            // We already have the sort order, so TreeMap is not necessary here.
            this.termIndex = Maps.mutable.empty();
            this.termIndexInsensitive = Maps.mutable.empty();
        }
        termIndexBuilt = true;
        setBlockBasedFile(useBlockBasedTermsFile);
        if (termsFile != null && termsFile.exists())
            read(termsFile);
    }

    @Override
    public int indexOf(String term) {

        if (!termIndexBuilt) {
            // We havent' filled termIndex based on terms[] yet.
            // Do so now. (so the first call to this method might be
            // slow in search mode, but it's only used to deserialize
            // HitPropValueContext*, which doesn't happen a lot)
            buildTermIndex();
        }

        // Do we have the term index available (fastest method)?
        if (termIndexBuilt) {
            synchronized (this) {
                // Yes, use the available term index.
                CollationKey key = this.collator.getCollationKey(term);
                Integer index = termIndex.get(key);
                if (index != null)
                    return index;
                if (!indexMode)
                    return NO_TERM; // term not found
                index = termIndex.size();
                termIndex.put(key, index);
                return index;
            }
        }

        // No. (this means we are in search mode, because in
        //      index mode the term index is always available)
        // Do a binary search to find term.
        // Note that the binary search is done on the sorted terms,
        // so we need to guess an ordinal, convert it to a term index,
        // then check the term string, and repeat until we find a match.
        int min = 0, max = idPerSortPosition.length - 1;
        while (true) {
            int guessedOrdinal = (min + max) >>> 1;
            int guessedIndex = idPerSortPosition[guessedOrdinal];
            String guessedTerm = get(guessedIndex);
            int cmp = collator.compare(term, guessedTerm);
            if (cmp == 0)
                return guessedIndex; // found
            if (cmp < 0)
                max = guessedOrdinal - 1;
            else
                min = guessedOrdinal + 1;
            if (max < min)
                return NO_TERM; // not found
        }
    }

    @Override
    public void indexOf(MutableIntSet results, String term, boolean caseSensitive, boolean diacSensitive) {
        // NOTE: we don't do diacritics and case-sensitivity separately, but could in the future.
        //  right now, diacSensitive is ignored and caseSensitive is used for both.
        int[] idLookup = caseSensitive ? idPerSortPosition : idPerSortPositionInsensitive;
        Collator coll = caseSensitive ? collator : collatorInsensitive;

        // Do we have the term index available (fastest method)?
        CollationKey key = coll.getCollationKey(term);
        if (termIndexBuilt) {
            // Yes, use the available term index.
            // NOTE: insensitive index is only available in search mode.
            if (caseSensitive) {
                // Case-/accent-sensitive. Look up the term's id.
                results.add(termIndex.get(key));
                return;
            } else if (termIndexInsensitive != null) {
                // Case-/accent-insensitive. Find the relevant stretch of sort positions and look up the corresponding ids.
                FirstAndNumber firstAndNumber = termIndexInsensitive.get(key);
                for (int i = firstAndNumber.first; i < firstAndNumber.number; i++) {
                    results.add(idLookup[i]);
                }
                return;
            }
        }

        // No termIndex available.
        // (this means we are in search mode, because in index mode the term index is always available)
        // Do a binary search to find term.
        // Note that the binary search is done on the sorted terms,
        // so we need to guess an ordinal, convert it to a term index,
        // then check the term string, and repeat until we find a match.
        int min = 0, max = idLookup.length - 1;
        while (max >= min) {
            int guessedOrdinal = (min + max) >>> 1;
            int guessedIndex = idLookup[guessedOrdinal];
            String guessedTerm = get(guessedIndex);
            CollationKey termKey = coll.getCollationKey(term);
            CollationKey guessedKey = coll.getCollationKey(guessedTerm);
            int cmp = termKey.compareTo(guessedKey); //coll.compare(term, guessedTerm);
            if (cmp == 0) {
                // Found a match. Look both ways to see if there's more matching terms.
                results.add(guessedIndex);
                if (!caseSensitive) {
                    for (int testOrdinal = guessedOrdinal - 1; testOrdinal >= min; testOrdinal--) {
                        int testIndex = idLookup[testOrdinal];
                        CollationKey testKey = coll.getCollationKey(get(testIndex));
                        //if (coll.compare(term, get(testIndex)) != 0)
                        if (termKey.compareTo(testKey) != 0)
                            break;
                        results.add(testIndex);
                    }
                    for (int testOrdinal = guessedOrdinal + 1; testOrdinal <= max; testOrdinal++) {
                        int testIndex = idLookup[testOrdinal];
                        CollationKey testKey = coll.getCollationKey(get(testIndex));
                        //if (coll.compare(term, get(testIndex)) != 0)
                        if (termKey.compareTo(testKey) != 0)
                            break;
                        results.add(testIndex);
                    }
                }
                // found
                return;
            }
            if (cmp < 0)
                max = guessedOrdinal - 1;
            else
                min = guessedOrdinal + 1;
        }
        // not found
    }

    @Override
    public boolean termsEqual(int[] termId, boolean caseSensitive, boolean diacSensitive) {
        // NOTE: we don't do diacritics and case-sensitivity separately, but could in the future.
        //  right now, diacSensitive is ignored and caseSensitive is used for both.
        int[] idLookup = caseSensitive ? sortPositionPerId : sortPositionPerIdInsensitive;
        int id0 = idLookup[termId[0]];
        for (int i = 1; i < termId.length; i++) {
            if (termId[i] == -1 || id0 != idLookup[termId[i]])
                return false;
        }
        return true;
    }

    @Override
    public synchronized void buildTermIndex() {
        if (termIndexBuilt)
            return;

        // Build the case-sensitive term index.
        int n = numberOfTerms();
        for (int i = 0; i < n; i++) {
            termIndex.put(collator.getCollationKey(get(i)), i);
        }

        if (termIndexInsensitive != null) {
            // Now, store the first index in the sortPositionPerIdInsensitive[] array
            // that matches each term, and the number of matching terms that follow.
            // This can be used while building NFAs to quickly fetch all indices matching
            // a term case-insensitively.
            CollationKey prevTermKey = collatorInsensitive.getCollationKey("");
            FirstAndNumber currentItem = null;
            for (int i = 0; i < n; i++) {
                String term = get(idPerSortPositionInsensitive[i]);
                CollationKey termKey = collatorInsensitive.getCollationKey(term);
                if (!termKey.equals(prevTermKey)) {
                    if (currentItem != null)
                        currentItem.number = i - currentItem.first;
                    currentItem = new FirstAndNumber(i, 0);
                    termIndexInsensitive.put(termKey, currentItem);
                    prevTermKey = termKey;
                }
            }
            if (currentItem != null) {
                currentItem.number = n - currentItem.first;
            }
        }

        termIndexBuilt = true;
    }

    @Override
    public synchronized void clear() {
        if (!indexMode)
            throw new RuntimeException("Cannot clear, not in index mode");
        termIndex.clear();
        if (termIndexInsensitive != null)
            termIndexInsensitive.clear();
        termIndexBuilt = true;
    }

    private synchronized void read(File termsFile) {
        termIndex.clear();
        if (termIndexInsensitive != null)
            termIndexInsensitive.clear();
        try {
            try (RandomAccessFile raf = new RandomAccessFile(termsFile, "r")) {
                try (FileChannel fc = raf.getChannel()) {
                    long fileLength = termsFile.length();
                    long fileMapStart = 0;
                    long fileMapLength = Math.min(maxMapSize, fileLength);
                    MappedByteBuffer buf = fc.map(MapMode.READ_ONLY, fileMapStart, fileMapLength);
                    int n = buf.getInt();
                    IntBuffer ib = buf.asIntBuffer();
                    numberOfTerms = n;
                    int[] termStringOffsets = new int[n + 1];
                    terms = new String[n];

                    if (useBlockBasedTermsFile) {
                        // New format, multiple blocks of term strings if necessary,
                        // so term strings may total over 2 GB.

                        // Read the term string offsets and string data block
                        int currentTerm = 0;
                        while (currentTerm < n) {

                            int numTermsThisBlock = ib.get();
                            ib.get(termStringOffsets, currentTerm, numTermsThisBlock); // term
                                                                                       // string
                                                                                       // offsets

                            // Read term strings data
                            int dataBlockSize = termStringOffsets[currentTerm + numTermsThisBlock] = ib.get();
                            buf.position(buf.position() + BYTES_PER_INT * (numTermsThisBlock + 2));
                            byte[] termStringsThisBlock = new byte[dataBlockSize];
                            buf.get(termStringsThisBlock);

                            // Now instantiate String objects from the offsets and byte data
                            int firstTermInBlock = currentTerm;
                            for (; currentTerm < firstTermInBlock + numTermsThisBlock; currentTerm++) {
                                int offset = termStringOffsets[currentTerm];
                                int length = termStringOffsets[currentTerm + 1] - offset;
                                String str = new String(termStringsThisBlock, offset, length, DEFAULT_CHARSET);

                                // We need to find term for id while searching
                                terms[currentTerm] = str;
                            }

                            // Re-map a new part of the file before we read the next block.
                            // (and before we read the sort buffers)
                            long bytesRead = buf.position();
                            fileMapStart += bytesRead;
                            fileMapLength = Math.min(maxMapSize, fileLength - fileMapStart);
                            if (fileMapLength > 0) {
                                buf = fc.map(MapMode.READ_ONLY, fileMapStart, fileMapLength);
                                ib = buf.asIntBuffer();
                            }
                        }

                    } else {
                        // Old format, single term strings block.
                        // Causes problems when term strings total over 2 GB.

                        ib.get(termStringOffsets); // term string offsets
                        int termStringsByteSize = ib.get(); // data block size

                        // termStringByteSize fits in an int, and terms
                        // fits in a single byte array. Use the old code.
                        buf.position(buf.position() + BYTES_PER_INT + BYTES_PER_INT * termStringOffsets.length);
                        byte[] termStrings = new byte[termStringsByteSize];
                        buf.get(termStrings);
                        ib = buf.asIntBuffer();

                        // Now instantiate String objects from the offsets and byte data
                        terms = new String[n];
                        for (int id = 0; id < n; id++) {
                            int offset = termStringOffsets[id];
                            int length = termStringOffsets[id + 1] - offset;
                            String str = new String(termStrings, offset, length, DEFAULT_CHARSET);

                            // We need to find term for id while searching
                            terms[id] = str;
                        }
                    }

                    if (indexMode) {
                        termIndexBuilt = false;
                        buildTermIndex(); // We need to find id for term quickly while indexing
                        terms = null; // useless in index mode because we can't add to it, and we don't need it anyway
                    } else {
                        termIndexBuilt = false; // termIndex hasn't been filled yet

                        // Read the sort order arrays
                        sortPositionPerId = new int[n];
                        sortPositionPerIdInsensitive = new int[n];
                        ib.position(ib.position() + n); // Advance past unused sortPos -> id array (left in there for file compatibility)
                        ib.get(sortPositionPerId);
                        ib.position(ib.position() + n); // Advance past unused sortPos -> id array (left in there for file compatibility)
                        ib.get(sortPositionPerIdInsensitive);

                        // Invert sortPositionPerId[] array, so we can later do a binary search through our
                        // terms to find a specific one. (only needed to deserialize sort/group criteria from URL)
                        idPerSortPosition = new int[n];
                        idPerSortPositionInsensitive = new int[n];
                        Arrays.fill(idPerSortPositionInsensitive, -1);
                        for (int i = 0; i < n; i++) {
                            idPerSortPosition[sortPositionPerId[i]] = i;
                            int x = sortPositionPerIdInsensitive[i];
                            // Multiple terms can have the same (case-insensitive)
                            // sort position. Skip over previous terms so each term is
                            // in the array and we can look at adjacent terms to recover all
                            // the terms with the same sort position later.
                            while (idPerSortPositionInsensitive[x] >= 0)
                                x++;
                            idPerSortPositionInsensitive[x] = i;
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void write(File termsFile) {
        if (!indexMode)
            throw new RuntimeException("Term.write(): not in index mode!");

        try {
            // Open the terms file
            try (RandomAccessFile raf = new RandomAccessFile(termsFile, "rw")) {
                try (FileChannel fc = raf.getChannel()) {
                    int n = termIndex.size();

                    // Fill the terms[] array
                    terms = new String[n];
                    long termStringsByteSize = 0;
                    for (Map.Entry<CollationKey, Integer> entry : termIndex.entrySet()) {
                        String term = entry.getKey().getSourceString();
                        terms[entry.getValue()] = term;
                        termStringsByteSize += term.getBytes(DEFAULT_CHARSET).length;
                    }

                    // Calculate the file length and map the file
                    MappedByteBuffer buf;
                    IntBuffer ib;
                    if (!useBlockBasedTermsFile) {
                        long fileLength = 2 * BYTES_PER_INT + (n + 1) * BYTES_PER_INT + termStringsByteSize
                                + NUM_SORT_BUFFERS * BYTES_PER_INT * n;
                        fc.truncate(fileLength); // truncate if necessary
                        buf = fc.map(MapMode.READ_WRITE, 0, fileLength);
                        buf.putInt(n); // Start with the number of terms
                        ib = buf.asIntBuffer();

                        // Terms file is small enough to fit in a single byte array.
                        // Use the old code.

                        // Calculate byte offsets for all the terms and fill data array
                        int currentOffset = 0;
                        int[] termStringOffsets = new int[n + 1];
                        byte[] termStrings = new byte[(int) termStringsByteSize];
                        for (int i = 0; i < n; i++) {
                            termStringOffsets[i] = currentOffset;
                            byte[] termBytes = terms[i].getBytes(DEFAULT_CHARSET);
                            System.arraycopy(termBytes, 0, termStrings, currentOffset, termBytes.length);
                            currentOffset += termBytes.length;
                        }
                        termStringOffsets[n] = currentOffset;

                        // Write offset and data arrays to file
                        ib.put(termStringOffsets);
                        ib.put((int) termStringsByteSize); // size of the data block to follow
                        buf.position(buf.position() + BYTES_PER_INT + BYTES_PER_INT * termStringOffsets.length); // advance past offsets array
                        buf.put(termStrings);
                        ib = buf.asIntBuffer();
                    } else {
                        long fileMapStart = 0, fileMapLength = maxMapSize;
                        buf = fc.map(MapMode.READ_WRITE, fileMapStart, fileMapLength);
                        buf.putInt(n); // Start with the number of terms      //@4
                        ib = buf.asIntBuffer();
                        long fileLength = BYTES_PER_INT;

                        // Terms file is too large to fit in a single byte array.
                        // Use the new code.
                        int currentTerm = 0;
                        long bytesLeftToWrite = termStringsByteSize;
                        int[] termStringOffsets = new int[n];
                        while (currentTerm < n) {
                            int firstTermInBlock = currentTerm;
                            int blockSize = (int) Math.min(bytesLeftToWrite, maxBlockSize);

                            // Calculate byte offsets for all the terms and fill data array
                            int currentOffset = 0;
                            byte[] termStrings = new byte[blockSize];
                            while (currentTerm < n) {
                                termStringOffsets[currentTerm] = currentOffset;
                                byte[] termBytes = terms[currentTerm].getBytes(DEFAULT_CHARSET);
                                if (currentOffset + termBytes.length > blockSize) {
                                    // Block is full. Write it and continue with next block.
                                    break;
                                }
                                System.arraycopy(termBytes, 0, termStrings, currentOffset, termBytes.length);
                                currentOffset += termBytes.length;
                                currentTerm++;
                                bytesLeftToWrite -= termBytes.length;
                            }

                            // Write offset and data arrays to file
                            int numTermsThisBlock = currentTerm - firstTermInBlock;

                            long blockSizeBytes = 2 * BYTES_PER_INT + numTermsThisBlock * BYTES_PER_INT + currentOffset;

                            ib.put(numTermsThisBlock); //@4
                            ib.put(termStringOffsets, firstTermInBlock, numTermsThisBlock); //@4 * numTermsThisBlock
                            ib.put(currentOffset); // include the offset after the last term at position termStringOffsets[n]
                                                   // (doubles as the size of the data block to follow) //@4
                            int newPosition = buf.position() + BYTES_PER_INT * (2 + numTermsThisBlock);
                            buf.position(newPosition); // advance past offsets array
                            buf.put(termStrings, 0, currentOffset); //@blockSize (max. maxBlockSize)
                            ib = buf.asIntBuffer();
                            fileLength += blockSizeBytes;

                            // Re-map a new part of the file before we write the next block.
                            // (and eventually, the sort buffers, see below)
                            fileMapStart += buf.position();
                            buf = fc.map(MapMode.READ_WRITE, fileMapStart, fileMapLength);
                            ib = buf.asIntBuffer();
                        }

                        // Determine total file length (by adding the sort buffer byte length to the
                        // running total) and truncate the file if necessary
                        // (we can do this now, even though we still have to write the sort buffers,
                        // because we know how large the file will eventually be)
                        fileLength += NUM_SORT_BUFFERS * BYTES_PER_INT * n;
                        if (File.separatorChar != '\\') // causes problems on Windows
                            fc.truncate(fileLength);
                    }

                    // Write the case-sensitive sort order
                    // Because termIndex is a SortedMap, values are returned in key-sorted order.
                    // In other words, the index numbers are in order of sorted terms, so the id
                    // for 'aardvark' comes before the id for 'ape', etc.
                    int i = 0;
                    sortPositionPerId = new int[n];
                    Integer[] insensitive = new Integer[n];
                    for (int id : termIndex.values()) {
                        sortPositionPerId[id] = i;
                        insensitive[i] = id; // fill this so we can re-sort later, faster b/c already partially sorted
                        i++;
                    }
                    ib.put(new int[n]); // NOT USED ANYMORE, JUST FOR FILE COMPATIBILITY
                    ib.put(sortPositionPerId);

                    // Now, sort case-insensitively and write those arrays as well
                    Arrays.sort(insensitive, new Comparator<Integer>() {
                        @Override
                        public int compare(Integer a, Integer b) {
                            return collatorInsensitive.compare(terms[a], terms[b]);
                        }
                    });
                    // Copy into the sortPositionPerIdInsensitive array, making sure that
                    // identical values get identical sort positions!
                    sortPositionPerIdInsensitive = new int[n];
                    int sortPos = 0;
                    for (i = 0; i < n; i++) {
                        if (i == 0
                                || collatorInsensitive.compare(terms[insensitive[i - 1]], terms[insensitive[i]]) != 0) {
                            // Not identical to previous value: gets its own sort position.
                            // If a value is identical to the previous one, it gets the same sort position.
                            sortPos = i;
                        }
                        sortPositionPerIdInsensitive[insensitive[i]] = sortPos;
                    }
                    ib.put(new int[n]); // NOT USED ANYMORE, JUST FOR FILE COMPATIBILITY
                    ib.put(sortPositionPerIdInsensitive);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String get(Integer index) {
        assert index >= 0 && index < numberOfTerms : "Term index out of range (" + index + ", numterms = "
                + numberOfTerms + ")";
        return terms[index];
    }

    @Override
    public int numberOfTerms() {
        return numberOfTerms;
    }

    @Override
    public void toSortOrder(int[] tokenId, int[] sortOrder, boolean sensitive) {
        if (sensitive) {
            for (int i = 0; i < tokenId.length; i++) {
                if (tokenId[i] == NO_TERM)
                    sortOrder[i] = NO_TERM;
                else
                    sortOrder[i] = sortPositionPerId[tokenId[i]];
            }
        } else {
            for (int i = 0; i < tokenId.length; i++) {
                if (tokenId[i] == NO_TERM)
                    sortOrder[i] = NO_TERM;
                else
                    sortOrder[i] = sortPositionPerIdInsensitive[tokenId[i]];
            }
        }
    }

    @Override
    public int compareSortPosition(int tokenId1, int tokenId2, boolean sensitive) {
        if (sensitive) {
            return sortPositionPerId[tokenId1] - sortPositionPerId[tokenId2];
        }
        return sortPositionPerIdInsensitive[tokenId1] - sortPositionPerIdInsensitive[tokenId2];
    }

    @Override
    public int idToSortPosition(int id, boolean sensitive) {
        return sensitive ? sortPositionPerId[id] : sortPositionPerIdInsensitive[id];
    }

    @Override
    protected void setBlockBasedFile(boolean useBlockBasedTermsFile) {
        this.useBlockBasedTermsFile = useBlockBasedTermsFile;
    }

    public void setMaxBlockSize(int maxBlockSize) {
        if ((long) maxBlockSize > ((long) DEFAULT_MAX_MAP_SIZE))
            throw new RuntimeException("Max. block size too large, max. " + DEFAULT_MAX_MAP_SIZE);
        this.maxBlockSize = maxBlockSize;
    }

}
