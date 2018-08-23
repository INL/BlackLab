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
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Keeps a forward index of documents, to quickly answer the question "what word
 * occurs in doc X at position Y"?
 * 
 * Thread-safety: not thread-safe in index mode, but thread-safe while
 * searching.
 */
@NotThreadSafe // in index mode
class AnnotationForwardIndexImpl extends AnnotationForwardIndex {

    protected static final Logger logger = LogManager.getLogger(AnnotationForwardIndexImpl.class);

    /** The number of cached fiids we check to see if this field is set anywhere. */
    static final int NUMBER_OF_CACHE_ENTRIES_TO_CHECK = 1000;

    /**
     * Java has as limit of 2GB for MappedByteBuffer. But this could be worked
     * around using arrays of MappedByteBuffers, see:
     * http://stackoverflow.com/questions/5675748/java-memorymapping-big-files
     */
    private static final int MAX_DIRECT_BUFFER_SIZE = Integer.MAX_VALUE;

    /**
     * Desired chunk size. Usually just MAX_DIRECT_BUFFER_SIZE, but can be set to be
     * smaller (for easier testing).
     *
     * NOTE: using MAX_DIRECT_BUFFER_SIZE (2GB) failed on Linux 64 bit, so we're
     * using 1GB for now.
     */
    static int preferredChunkSizeBytes = MAX_DIRECT_BUFFER_SIZE / 2;

    /** Size of a long in bytes. */
    private static final int SIZEOF_LONG = Long.SIZE / Byte.SIZE;

    /**
     * Size of an int in bytes. This will always be 4, according to the standard.
     */
    private static final int SIZEOF_INT = Integer.SIZE / Byte.SIZE;

    /**
     * The number of integer positions to reserve when mapping the file for writing.
     */
    final static int WRITE_MAP_RESERVE = 250000; // 250K integers = 1M bytes

    /** The memory mapped write int buffer */
    private IntBuffer writeBuffer;

    /**
     * Buffer offset (position in file of start of writeBuffer) in integer positions
     * (so we don't count bytes, we count ints)
     */
    private long writeBufOffset;

    /**
     * The table of contents (where documents start in the tokens file and how long
     * they are)
     */
    ArrayList<TocEntry> toc;

    /** Deleted TOC entries. Always sorted by size. */
    ArrayList<TocEntry> deletedTocEntries;

    /** The table of contents (TOC) file, docs.dat */
    private File tocFile;

    /** The tokens file (stores indexes into terms.dat) */
    private File tokensFile;

    /** The terms file (stores unique terms) */
    private File termsFile;

    /** The unique terms in our index */
    private Terms terms;

    /** Handle for the tokens file */
    private RandomAccessFile writeTokensFp;

    /** Mapping into the tokens file */
    private List<ByteBuffer> tokensFileChunks = null;

    /** Offsets of the mappings into the token file */
    private List<Long> tokensFileChunkOffsetBytes = null;

    /** File channel for the tokens file */
    private FileChannel writeTokensFileChannel;

    /** Has the table of contents been modified? */
    private boolean tocModified = false;

    /**
     * The position (in ints) in the tokens file after the last token written. Note
     * that the actual file may be larger because we reserve space at the end.
     */
    private long tokenFileEndPosition = 0;

    /** How we look up forward index id in the index. */
    private FiidLookup fiidLookup;

    /** Are we in index mode (i.e. writing to forward index) or not? */
    private boolean indexMode;

    /**
     * If true, we use the new, block-based terms file, that can grow larger than 2
     * GB.
     */
    private boolean useBlockBasedTermsFile = true;

    /**
     * If true, our Terms can be used for NFA matching (Collator is consistent with
     * other comparisons)
     */
    private boolean canDoNfaMatching;

    /** The annotation for which we're the forward index */
    private Annotation annotation;

    @Override
    public void setIdTranslateInfo(FiidLookup fiidLookup, Annotation annotation) {
        this.annotation = annotation;
        this.fiidLookup = fiidLookup;
    }

    @Override
    public int luceneDocIdToFiid(int docId) {
        return (int) fiidLookup.get(docId);
    }

    AnnotationForwardIndexImpl(File dir, boolean indexMode, Collators collators, boolean create,
            boolean largeTermsFileSupport) {
        canDoNfaMatching = collators.version() != CollatorVersion.V1;

        if (!indexMode && create) {
            throw new IllegalArgumentException("Tried to create new forward index, but not in index mode");
        }

        if (!dir.exists()) {
            if (!create)
                throw new IllegalArgumentException("ForwardIndex doesn't exist: " + dir);
            if (!dir.mkdir())
                throw new BlackLabRuntimeException("Could not create dir: " + dir);
        }

        this.indexMode = indexMode;

        termsFile = new File(dir, "terms.dat");
        tocFile = new File(dir, "docs.dat");
        tokensFile = new File(dir, "tokens.dat");
        if (create) {
            if (tokensFile.exists() && !tokensFile.delete())
                throw new BlackLabRuntimeException("Could not delete file: " + tokensFile);
            if (tocFile.exists() && !tocFile.delete())
                throw new BlackLabRuntimeException("Could not delete file: " + tocFile);
            if (termsFile.exists() && !termsFile.delete())
                throw new BlackLabRuntimeException("Could not delete file: " + termsFile);
        }
        toc = new ArrayList<>();
        deletedTocEntries = new ArrayList<>();
        try {
            setLargeTermsFileSupport(largeTermsFileSupport);
            if (tocFile.exists()) {
                readToc();
                terms = Terms.open(indexMode, collators, termsFile, useBlockBasedTermsFile);
                tocModified = false;
            } else {
                if (!indexMode) {
                    throw new IllegalArgumentException("No TOC found, and not in index mode!");
                }
                terms = Terms.open(indexMode, collators, null, true);
                if (!tokensFile.createNewFile())
                    throw new BlackLabRuntimeException("Could not create file: " + tokensFile);
                tokensFileChunks = null;
                tocModified = true;
                terms.setBlockBasedFile(useBlockBasedTermsFile);
            }
            // Tricks to speed up reading
            if (indexMode) {
                // Index mode. Open for writing.
                openTokensFileForWriting();
            } else {
                // Memory-map the file for reading.
                openTokensFileForReading();
            }
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }

        if (create) {
            clear();
        }
    }

    /**
     * Open the tokens file for writing.
     * 
     * @throws IOException on error
     */
    protected void openTokensFileForWriting() throws IOException {
        writeTokensFp = new RandomAccessFile(tokensFile, "rw");
        writeTokensFileChannel = writeTokensFp.getChannel();
    }

    /**
     * Memory-map the tokens file for reading.
     * 
     * @throws IOException
     */
    private void openTokensFileForReading() throws IOException {
        try (RandomAccessFile tokensFp = new RandomAccessFile(tokensFile, "r");
                FileChannel tokensFileChannel = tokensFp.getChannel()) {
            // Map the tokens file in chunks of 2GB each. When retrieving documents, we always
            // read it from just one chunk, not multiple, but because each chunk begins at a
            // document start, documents of up to 2G tokens can be processed. We could get around
            // this limitation by reading from multiple chunks, but this would make the code
            // more complex.
            tokensFileChunks = new ArrayList<>();
            tokensFileChunkOffsetBytes = new ArrayList<>();
            long mappedBytes = 0;
            long tokenFileEndBytes = tokenFileEndPosition * SIZEOF_INT;
            while (mappedBytes < tokenFileEndBytes) {
                // Find the last TOC entry start point that's also in the previous mapping
                // (or right the first byte after the previous mapping).

                // Look for the largest entryOffset that's no larger than mappedBytes.
                TocEntry mapNextChunkFrom = null;
                for (TocEntry e : toc) {
                    if (e.offset <= mappedBytes && (mapNextChunkFrom == null || e.offset > mapNextChunkFrom.offset))
                        mapNextChunkFrom = e;
                }

                // Uses binary search.
                int min = 0, max = toc.size();
                while (max - min > 1) {
                    int middle = (min + max) / 2;
                    long middleVal = toc.get(middle).offset * SIZEOF_INT;
                    if (middleVal <= mappedBytes) {
                        min = middle;
                    } else {
                        max = middle;
                    }
                }
                long startOfNextMappingBytes = toc.get(min).offset * SIZEOF_INT;

                // Map this chunk
                long sizeBytes = tokenFileEndBytes - startOfNextMappingBytes;
                if (sizeBytes > preferredChunkSizeBytes)
                    sizeBytes = preferredChunkSizeBytes;

                ByteBuffer mapping = tokensFileChannel.map(FileChannel.MapMode.READ_ONLY, startOfNextMappingBytes,
                        sizeBytes);
                tokensFileChunks.add(mapping);
                tokensFileChunkOffsetBytes.add(startOfNextMappingBytes);
                mappedBytes = startOfNextMappingBytes + sizeBytes;
            }
        }
    }

    /**
     * Delete all content in the forward index
     */
    private void clear() {
        if (!indexMode)
            throw new BlackLabRuntimeException("Cannot clear, not in index mode");

        // delete data files and empty TOC
        try {
            if (writeTokensFp == null) {
                openTokensFileForWriting();
            }

            if (File.separatorChar != '\\') // causes problems on Windows
                writeTokensFp.setLength(0);

        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
        if (termsFile.exists() && !termsFile.delete())
            throw new BlackLabRuntimeException("Could not delete file: " + termsFile);
        if (tocFile.exists() && !tocFile.delete())
            throw new BlackLabRuntimeException("Could not delete file: " + tocFile);
        toc.clear();
        deletedTocEntries.clear();
        tokenFileEndPosition = 0;
        tocModified = true;
    }

    /**
     * Read the table of contents from the file
     */
    private void readToc() {
        toc.clear();
        deletedTocEntries.clear();
        try (RandomAccessFile raf = new RandomAccessFile(tocFile, "r");
                FileChannel fc = raf.getChannel()) {
            long fileSize = tocFile.length();
            MappedByteBuffer buf = fc.map(MapMode.READ_ONLY, 0, fileSize);
            int n = buf.getInt();
            long[] offset = new long[n];
            int[] length = new int[n];
            byte[] deleted = new byte[n];
            LongBuffer lb = buf.asLongBuffer();
            lb.get(offset);
            buf.position(buf.position() + SIZEOF_LONG * n);
            IntBuffer ib = buf.asIntBuffer();
            ib.get(length);
            buf.position(buf.position() + SIZEOF_INT * n);
            buf.get(deleted);
            toc.ensureCapacity(n);
            for (int i = 0; i < n; i++) {
                TocEntry e = new TocEntry(offset[i], length[i], deleted[i] != 0);
                toc.add(e);
                if (e.deleted) {
                    deletedTocEntries.add(e);
                }
                long end = e.offset + e.length;
                if (end > tokenFileEndPosition)
                    tokenFileEndPosition = end;
            }
            sortDeletedTocEntries();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
        toc.trimToSize();
        deletedTocEntries.trimToSize();
    }

    private void sortDeletedTocEntries() {
        deletedTocEntries.sort(new Comparator<TocEntry>() {
            @Override
            public int compare(TocEntry o1, TocEntry o2) {
                return o1.length - o2.length;
            }
        });
    }

    /**
     * Write the table of contents to the file
     */
    private void writeToc() {

        if (!indexMode)
            throw new BlackLabRuntimeException("Cannot write ToC, not in index mode");

        try {
            int n = toc.size();
            long[] offset = new long[n];
            int[] length = new int[n];
            byte[] deleted = new byte[n];
            int i = 0;
            for (TocEntry e : toc) {
                offset[i] = e.offset;
                length[i] = e.length;
                deleted[i] = (byte) (e.deleted ? 1 : 0);
                i++;
            }
            try (RandomAccessFile raf = new RandomAccessFile(tocFile, "rw");
                    FileChannel fc = raf.getChannel()) {
                long fileSize = SIZEOF_INT + (SIZEOF_LONG + SIZEOF_INT + 1) * n;
                fc.truncate(fileSize);
                MappedByteBuffer buf = fc.map(MapMode.READ_WRITE, 0, fileSize);
                buf.putInt(n);
                LongBuffer lb = buf.asLongBuffer();
                lb.put(offset);
                buf.position(buf.position() + SIZEOF_LONG * n);
                IntBuffer ib = buf.asIntBuffer();
                ib.put(length);
                buf.position(buf.position() + SIZEOF_INT * n);
                buf.put(deleted);
            }
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
        tocModified = false;
    }

    @Override
    public void close() {
        try {
            if (tocModified) {
                writeToc();
                terms.write(termsFile);
            }

            // Close the FileChannel and RandomAccessFile (indexMode only)
            if (writeTokensFileChannel != null) {
                // Cannot truncate if still mapped; cannot force demapping.
                //tokensFileChannel.truncate(tokenFileEndPosition * SIZEOF_INT);
                writeTokensFileChannel.close();
            }
            if (writeTokensFp != null)
                writeTokensFp.close();

        } catch (Exception e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Find the best-fitting deleted entry for the specified length
     * 
     * @param length length the entry should at least be
     * @return the best-fitting entry
     */
    TocEntry findBestFittingGap(int length) {
        int n = deletedTocEntries.size();

        // Are there any fitting gaps?
        if (n == 0 || deletedTocEntries.get(n - 1).length < length)
            return null;

        // Does the smallest gap fit?
        if (deletedTocEntries.get(0).length >= length)
            return deletedTocEntries.get(0);

        // Do a binary search to find the best fit
        int doesntFit = 0, bestFitSoFar = n - 1;
        while (bestFitSoFar - doesntFit > 1) {
            int newTry = doesntFit + bestFitSoFar / 2;
            if (deletedTocEntries.get(newTry).length < length)
                doesntFit = newTry;
            else
                bestFitSoFar = newTry;
        }
        return deletedTocEntries.get(bestFitSoFar);
    }

    @Override
    public synchronized int addDocument(List<String> content, List<Integer> posIncr) {
        if (!indexMode)
            throw new BlackLabRuntimeException("Cannot add document, not in index mode");

        // Calculate the total number of tokens we need to store, based on the number
        // of positions (we store 1 token per position, regardless of whether we have
        // none, one or multiple values for that position)
        int numberOfTokens;
        if (posIncr == null) {
            // No position increments given; assume always 1
            numberOfTokens = content.size();
        } else {
            // Calculate using position increments
            numberOfTokens = 0;
            for (int inc : posIncr) {
                numberOfTokens += inc;
            }
        }

        // Decide where we're going to store this document,
        // and update ToC
        TocEntry gap = findBestFittingGap(numberOfTokens);
        long newDocumentOffset;
        int mapReserve;
        tocModified = true;
        boolean addNewEntry = true;
        int newDocumentFiid = -1;
        if (gap == null) {
            // No fitting gap; just write it at the end
            newDocumentOffset = tokenFileEndPosition;
            mapReserve = WRITE_MAP_RESERVE; // if writing at end, reserve more space
        } else {
            // Found a fitting gap; write it there
            newDocumentOffset = gap.offset;
            mapReserve = 0; // don't reserve extra write space, not needed
            if (gap.length == numberOfTokens) {
                // Exact fit; delete from free list and re-use entry
                deletedTocEntries.remove(gap);
                gap.deleted = false;
                addNewEntry = false;
                newDocumentFiid = toc.indexOf(gap);
            } else {
                // Not an exact fit; calculate remaining gap and re-sort free list
                gap.offset += numberOfTokens;
                gap.length -= numberOfTokens;
                sortDeletedTocEntries();
            }
        }
        // Do we need to create a new entry for this document in the ToC?
        // (always, unless we found an exact-fitting gap)
        if (addNewEntry) {
            // See if there's an unused entry
            TocEntry smallestFreeEntry = deletedTocEntries.isEmpty() ? null : deletedTocEntries.get(0);
            if (smallestFreeEntry != null && smallestFreeEntry.length == 0) {
                // Yes; re-use
                deletedTocEntries.remove(0);
                smallestFreeEntry.offset = newDocumentOffset;
                smallestFreeEntry.length = numberOfTokens;
                smallestFreeEntry.deleted = false;
                newDocumentFiid = toc.indexOf(smallestFreeEntry);
            } else {
                // No; make new entry
                toc.add(new TocEntry(newDocumentOffset, numberOfTokens, false));
                newDocumentFiid = toc.size() - 1;
            }
        }

        try {
            // Can we use the current write buffer for this write?
            long writeBufEnd = writeBuffer == null ? 0 : writeBufOffset + writeBuffer.limit();
            if (writeBuffer == null || writeBufOffset > newDocumentOffset
                    || writeBufEnd < newDocumentOffset + numberOfTokens) {
                // No, remap it
                writeBufOffset = newDocumentOffset;
                ByteBuffer byteBuffer = writeTokensFileChannel.map(FileChannel.MapMode.READ_WRITE,
                        writeBufOffset * SIZEOF_INT, (numberOfTokens + mapReserve)
                                * SIZEOF_INT);
                writeBuffer = byteBuffer.asIntBuffer();
            }

            // Set the correct start position
            writeBuffer.position((int) (newDocumentOffset - writeBufOffset));

            // Did we increase the length of the tokens file?
            long end = newDocumentOffset + numberOfTokens;
            if (end > tokenFileEndPosition)
                tokenFileEndPosition = end;

            // Write the token ids
            // (first fill the buffer, then write the buffer in 1 call)
            int[] tokenIds = new int[numberOfTokens];
            int tokenIdsIndex = 0;
            Iterator<String> contentIt = content.iterator();
            Iterator<Integer> posIncrIt = posIncr == null ? null : posIncr.iterator();
            int emptyStringTokenId = posIncrIt != null ? terms.indexOf("") : -1;
            while (contentIt.hasNext()) {
                String token = contentIt.next();
                int pi = posIncrIt == null ? 1 : posIncrIt.next();
                if (pi == 0)
                    continue; // we only store the first token at any position
                if (pi > 1) {
                    // Skipped a few tokens; add empty tokens for these positions
                    for (int i = 0; i < pi - 1; i++) {
                        tokenIds[tokenIdsIndex] = emptyStringTokenId;
                        tokenIdsIndex++;
                    }
                }

                tokenIds[tokenIdsIndex] = terms.indexOf(token);
                tokenIdsIndex++;
            }
            if (tokenIdsIndex != numberOfTokens)
                throw new BlackLabRuntimeException(
                        "tokenIdsIndex != numberOfTokens (" + tokenIdsIndex + " != " + numberOfTokens + ")");
            writeBuffer.put(tokenIds);

            return newDocumentFiid;
        } catch (IOException e1) {
            throw BlackLabRuntimeException.wrap(e1);
        }
    }

    @Override
    public synchronized List<int[]> retrievePartsIntByFiid(int fiid, int[] start, int[] end) {
        try {
            TocEntry e = toc.get(fiid);
            if (e == null || e.deleted)
                return null;

            int n = start.length;
            if (n != end.length)
                throw new IllegalArgumentException("start and end must be of equal length");
            List<int[]> result = new ArrayList<>(n);

            for (int i = 0; i < n; i++) {
                if (start[i] == -1)
                    start[i] = 0;
                if (end[i] == -1)
                    end[i] = e.length;
                if (start[i] < 0 || end[i] < 0) {
                    throw new IllegalArgumentException("Illegal values, start = " + start[i] + ", end = "
                            + end[i]);
                }
                if (end[i] > e.length) // Can happen while making KWICs because we don't know the
                                       // doc length until here
                    end[i] = e.length;
                if (start[i] > e.length || end[i] > e.length) {
                    throw new IllegalArgumentException("Value(s) out of range, start = " + start[i]
                            + ", end = " + end[i] + ", content length = " + e.length);
                }
                if (end[i] <= start[i]) {
                    throw new IllegalArgumentException(
                            "Tried to read empty or negative length snippet (from " + start[i]
                                    + " to " + end[i] + ")");
                }

                // Get an IntBuffer to read the desired content
                IntBuffer ib = null;
                boolean mapped = false;
                if (tokensFileChunks != null) {
                    // Yes, the tokens file has has been mapped to memory.
                    // Get an int buffer into the file.
                    mapped = true;

                    // Figure out which chunk to access.
                    ByteBuffer whichChunk = null;
                    long chunkOffsetBytes = -1;
                    long entryOffsetBytes = e.offset * SIZEOF_INT;
                    for (int j = 0; j < tokensFileChunkOffsetBytes.size(); j++) {
                        long offsetBytes = tokensFileChunkOffsetBytes.get(j);
                        ByteBuffer buffer = tokensFileChunks.get(j);
                        if (offsetBytes <= entryOffsetBytes + start[i] * SIZEOF_INT
                                && offsetBytes + buffer.capacity() >= entryOffsetBytes + end[i]
                                        * SIZEOF_INT) {
                            // This one!
                            whichChunk = buffer;
                            chunkOffsetBytes = offsetBytes;
                            break;
                        }
                    }

                    if (whichChunk == null) {
                        throw new BlackLabRuntimeException("Tokens file chunk containing document not found. fiid = " + fiid);
                    }
                    whichChunk.position((int) (e.offset * SIZEOF_INT - chunkOffsetBytes));
                    ib = whichChunk.asIntBuffer();
                }

                int snippetLength = end[i] - start[i];
                int[] snippet = new int[snippetLength];
                if (mapped) {
                    // The file is mem-mapped (search mode).
                    // Position us at the correct place in the file.
                    ib.position(start[i]);
                } else {
                    // Chunks are not mapped (index mode).
                    // Explicitly read the part we require from disk into an int buffer.
                    long offset = e.offset + start[i];

                    int bytesToRead = snippetLength * SIZEOF_INT;
                    ByteBuffer buffer = ByteBuffer.allocate(bytesToRead);
                    int bytesRead = writeTokensFileChannel.read(buffer, offset * SIZEOF_INT);
                    if (bytesRead < bytesToRead) {
                        throw new BlackLabRuntimeException("Not enough bytes read: " + bytesRead
                                + " < " + bytesToRead);
                    }
                    buffer.position(0);
                    ib = buffer.asIntBuffer();
                }
                ib.get(snippet);
                result.add(snippet);
            }

            return result;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public Terms terms() {
        return terms;
    }

    @Override
    public int numDocs() {
        return toc.size();
    }

    @Override
    public int docLengthByFiid(int fiid) {
        return toc.get(fiid).length;
    }

    @Override
    public void deleteDocumentByFiid(int fiid) {
        if (!indexMode)
            throw new BlackLabRuntimeException("Cannot delete document, not in index mode");
        TocEntry tocEntry = toc.get(fiid);
        tocEntry.deleted = true;
        deletedTocEntries.add(tocEntry); // NOTE: mergeAdjacentDeletedEntries takes care of re-sorting
        mergeAdjacentDeletedEntries();
        tocModified = true;
    }

    /**
     * Check if we can merge two (or more) deleted entries to create a larger gap,
     * and do so.
     *
     * Also takes care of truncating the file if there are deleted entries at the
     * end.
     */
    private void mergeAdjacentDeletedEntries() {
        // Sort by offset, so we can find adjacent entries
        deletedTocEntries.sort(Comparator.naturalOrder());

        // Find and merge adjacent entries
        TocEntry prev = deletedTocEntries.get(0);
        for (int i = 1; i < deletedTocEntries.size(); i++) {
            TocEntry current = deletedTocEntries.get(i);
            if (current.offset == prev.offset + prev.length) {
                // Found two adjacent deleted entries. Merge them.
                current.offset = prev.offset;
                current.length += prev.length;

                // length == 0 means a toc entry is unused
                // we can't delete toc entries because it messes up
                // the fiids. We will reuse them in addDocument().
                prev.length = 0;
            }
            prev = current;
        }

        TocEntry lastEntry = deletedTocEntries.get(deletedTocEntries.size() - 1);
        if (lastEntry.offset + lastEntry.length >= tokenFileEndPosition) {
            // Free entry at the end of the token file. Remove the entry and
            // make the tokens file shorter.
            tokenFileEndPosition -= lastEntry.length;
            lastEntry.length = 0;
        }

        // Re-sort on gap length
        sortDeletedTocEntries();
    }

    @Override
    public long freeSpace() {
        long freeSpace = 0;
        for (TocEntry e : deletedTocEntries) {
            freeSpace += e.length;
        }
        return freeSpace;
    }

    @Override
    public int freeBlocks() {
        return deletedTocEntries.size();
    }

    @Override
    public long totalSize() {
        return tokenFileEndPosition;
    }

    @Override
    protected void setLargeTermsFileSupport(boolean b) {
        this.useBlockBasedTermsFile = b;
    }

    @Override
    public Set<Integer> idSet() {
        return new AbstractSet<Integer>() {
            @Override
            public boolean contains(Object o) {
                return !toc.get((Integer) o).deleted;
            }

            @Override
            public boolean isEmpty() {
                return toc.size() == deletedTocEntries.size();
            }

            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {
                    int current = -1;
                    int next = -1;

                    @Override
                    public boolean hasNext() {
                        if (next < 0)
                            findNext();
                        return next < toc.size();
                    }

                    private void findNext() {
                        next = current + 1;
                        while (next < toc.size() && toc.get(next).deleted) {
                            next++;
                        }
                    }

                    @Override
                    public Integer next() {
                        if (next < 0)
                            findNext();
                        if (next >= toc.size())
                            throw new NoSuchElementException();
                        current = next;
                        next = -1;
                        return current;
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public int size() {
                return toc.size() - deletedTocEntries.size();
            }
        };
    }

    @Override
    public boolean canDoNfaMatching() {
        return canDoNfaMatching;
    }

    @Override
    public Annotation annotation() {
        return annotation;
    }
}
