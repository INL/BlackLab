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
import java.nio.Buffer;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.text.CollationKey;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.api.set.primitive.MutableIntSet;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Keeps a first-come-first-serve list of unique terms. Each term gets a unique
 * index number. These numbers are stored in the forward index to conserve space
 * and allow quick lookups of terms occurring in specific positions.
 *
 * This version of the class stores the terms in a more efficient way so it
 * saves and loads faster, and includes the case-insensitive sorting order.
 *
 * This implementation is not thread-safe.
 */
@NotThreadSafe
class TermsWriter extends Terms {

    protected static final Logger logger = LogManager.getLogger(TermsWriter.class);

    /** Maximum size for blocks of term strings. */
    private static final int DEFAULT_MAX_BLOCK_SIZE = DEFAULT_MAX_MAP_SIZE;

    /**
     * Number of sort buffers we store in the terms file (case-sensitive/insensitive
     * and inverted buffers for both as well)
     */
    private static final int NUM_SORT_BUFFERS = 4;

    /**
     * Mapping from term to its unique index number. We use a SortedMap because we
     * wish to store the sorted index numbers later (to speed up sorting).
     */
    Map<CollationKey, Integer> termIndex;

    /**
     * The maximum block size to use while writing the terms file. Usually around
     * the limit of 2GB, but for testing, we can set this to a lower value.
     */
    private int maxBlockSize = DEFAULT_MAX_BLOCK_SIZE;

    TermsWriter(Collators collators, File termsFile, boolean useBlockBasedTermsFile) {
        this.collator = collators.get(MatchSensitivity.SENSITIVE);
        this.collatorInsensitive = collators.get(MatchSensitivity.INSENSITIVE);

        // Index mode: create a SortedMap based on the specified Collator.
        // (used later to get the terms in sort order)
        this.termIndex = new TreeMap<>();

        setBlockBasedFile(useBlockBasedTermsFile);
        if (termsFile != null && termsFile.exists())
            read(termsFile);

        // We need to find id for term quickly while indexing
        // Build the case-sensitive term index.
        for (int i = 0; i < numberOfTerms; i++) {
            termIndex.put(collator.getCollationKey(terms[i]), i);
        }
        terms = null; // useless in index mode because we can't add to it, and we don't need it anyway
    }

    @Override
    public int indexOf(String term) {
        synchronized (this) {
            // Yes, use the available term index.
            CollationKey key = this.collator.getCollationKey(term);
            Integer index = termIndex.get(key);
            if (index != null)
                return index;
            index = termIndex.size();
            termIndex.put(key, index);
            return index;
        }
    }

    @Override
    public void indexOf(MutableIntSet results, String term, MatchSensitivity sensitivity) {
        throw new UnsupportedOperationException("Not available during indexing");
    }

    @Override
    public boolean termsEqual(int[] termId, MatchSensitivity sensitivity) {
        throw new UnsupportedOperationException("Not available during indexing");
    }

    @Override
    public synchronized void clear() {
        termIndex.clear();
    }

    private synchronized void read(File termsFile) {
        termIndex.clear();
        try {
            try (RandomAccessFile raf = new RandomAccessFile(termsFile, "r")) {
                try (FileChannel fc = raf.getChannel()) {
                    long fileLength = termsFile.length();
                    readFromFileChannel(fc, fileLength);
                }
            }
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public synchronized void write(File termsFile) {
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
                        // Old version of terms file (that can't grow larger than 2 GB)
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
                        ((Buffer)buf).position(buf.position() + BYTES_PER_INT + BYTES_PER_INT * termStringOffsets.length); // advance past offsets array
                        buf.put(termStrings);
                        ib = buf.asIntBuffer();
                    } else {
                        // Newer, "block-based" version of terms file that can grow larger than 2 GB.
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
                            long blockSizeBytes = 2 * BYTES_PER_INT;
                            while (currentTerm < n) {
                                termStringOffsets[currentTerm] = currentOffset;
                                byte[] termBytes = terms[currentTerm].getBytes(DEFAULT_CHARSET);
                                long newBlockSizeBytes = blockSizeBytes + BYTES_PER_INT + termBytes.length; // block grows by 1 offset and this term's bytes
                                if (newBlockSizeBytes > maxBlockSize) {
                                    // Block is full. Write it and continue with next block.
                                    break;
                                }
                                System.arraycopy(termBytes, 0, termStrings, currentOffset, termBytes.length);
                                currentOffset += termBytes.length;
                                currentTerm++;
                                bytesLeftToWrite -= termBytes.length;
                                blockSizeBytes = newBlockSizeBytes;
                            }

                            int numTermsThisBlock = currentTerm - firstTermInBlock;

                            // Write offset and data arrays to file
                            if (blockSizeBytes < 0) { // DEBUG, SHOULD NEVER HAPPEN
                                logger.error("***** blockSizeBytes < 0 !!!");
                                logger.error("blockSizeBytes = " + blockSizeBytes);
                                logger.error("n = " + n);
                                logger.error("numTermsThisBlock = " + numTermsThisBlock);
                                logger.error("  currentTerm = " + currentTerm);
                                logger.error("  firstTermInBlock = " + firstTermInBlock);
                                logger.error("currentOffset = " + currentOffset);
                            }

                            ib.put(numTermsThisBlock); //@4
                            ib.put(termStringOffsets, firstTermInBlock, numTermsThisBlock); //@4 * numTermsThisBlock
                            ib.put(currentOffset); // include the offset after the last term at position termStringOffsets[n]
                                                   // (doubles as the size of the data block to follow) //@4
                            int newPosition = buf.position() + BYTES_PER_INT * (2 + numTermsThisBlock);
                            ((Buffer)buf).position(newPosition); // advance past offsets array
                            if (fileMapLength - buf.position() < blockSize) {
                                //throw new RuntimeException("Not enough space in file mapping to write term strings!");

                                // Re-map a new part of the file before we write the term strings
                                fileMapStart += buf.position();
                                buf = fc.map(MapMode.READ_WRITE, fileMapStart, fileMapLength);
                            }
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
                        fileLength += NUM_SORT_BUFFERS * BYTES_PER_INT * (long)n;

                        if (fileLength < 0) { // DEBUG, SHOULD NEVER HAPPEN
                            logger.error("***** fileLength < 0 !!!");
                            logger.error("fileLength = " + fileLength);
                            logger.error("n = " + n);
                        }

                        if (File.separatorChar != '\\') // causes problems on Windows
                            fc.truncate(fileLength);
                    }

                    // Write the case-sensitive sort order
                    // Because termIndex is a SortedMap, values are returned in key-sorted order.
                    // In other words, the index numbers are in order of sorted terms, so the id
                    // for 'aardvark' comes before the id for 'ape', etc.
                    int i = 0;
                    int[] sortPositionPerId = new int[n];
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
                    int[] sortPositionPerIdInsensitive = new int[n];
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
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public String get(int index) {
        assert index >= 0 && index < numberOfTerms : "Term index out of range (" + index + ", numterms = "
                + numberOfTerms + ")";
        return terms[index];
    }

    @Override
    public int numberOfTerms() {
        return numberOfTerms;
    }

    @Override
    public void toSortOrder(int[] tokenId, int[] sortOrder, MatchSensitivity sensitivity) {
        throw new UnsupportedOperationException("Not available during indexing");
    }

    @Override
    public int compareSortPosition(int tokenId1, int tokenId2, MatchSensitivity sensitivity) {
        throw new UnsupportedOperationException("Not available during indexing");
    }

    @Override
    public int idToSortPosition(int id, MatchSensitivity sensitivity) {
        throw new UnsupportedOperationException("Not available during indexing");
    }

    @Override
    protected void setBlockBasedFile(boolean useBlockBasedTermsFile) {
        this.useBlockBasedTermsFile = useBlockBasedTermsFile;
    }

    public void setMaxBlockSize(int maxBlockSize) {
        if ((long) maxBlockSize > ((long) DEFAULT_MAX_MAP_SIZE))
            throw new BlackLabRuntimeException("Max. block size too large, max. " + DEFAULT_MAX_MAP_SIZE);
        this.maxBlockSize = maxBlockSize;
    }

}
