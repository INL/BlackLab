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
 * This implementation is not thread-safe.
 */
@NotThreadSafe
class AnnotationForwardIndexWriter extends AnnotationForwardIndex {

    protected static final Logger logger = LogManager.getLogger(AnnotationForwardIndexWriter.class);

    /** The memory mapped write int buffer */
    private IntBuffer writeBuffer;

    /**
     * Buffer offset (position in file of start of writeBuffer) in integer positions
     * (so we don't count bytes, we count ints)
     */
    private long writeBufOffset;

    /** Handle for the tokens file */
    private RandomAccessFile writeTokensFp;

    /** File channel for the tokens file */
    private FileChannel writeTokensFileChannel;

    /** Has the table of contents been modified? */
    private boolean tocModified = false;

    /**
     * The table of contents (where documents start in the tokens file and how long
     * they are)
     */
    List<TocEntry> toc = new ArrayList<>();

    /** Deleted TOC entries. Always sorted by size. */
    List<TocEntry> deletedTocEntries = new ArrayList<>();

    AnnotationForwardIndexWriter(Annotation annotation, File dir, Collators collators, boolean create, boolean largeTermsFileSupport) {
        super(annotation, dir, collators, largeTermsFileSupport);

        if (!dir.exists()) {
            if (!create)
                throw new IllegalArgumentException("ForwardIndex doesn't exist: " + dir);
            if (!dir.mkdir())
                throw new BlackLabRuntimeException("Could not create dir: " + dir);
        }

        if (create) {
            if (tokensFile.exists() && !tokensFile.delete())
                throw new BlackLabRuntimeException("Could not delete file: " + tokensFile);
            if (tocFile.exists() && !tocFile.delete())
                throw new BlackLabRuntimeException("Could not delete file: " + tocFile);
            if (termsFile.exists() && !termsFile.delete())
                throw new BlackLabRuntimeException("Could not delete file: " + termsFile);
        }
        try {
            if (tocFile.exists()) {
                readToc();
                terms = Terms.openForWriting(collators, termsFile, useBlockBasedTermsFile);
                tocModified = false;
            } else {
                terms = Terms.openForWriting(collators, null, true);
                if (!tokensFile.createNewFile())
                    throw new BlackLabRuntimeException("Could not create file: " + tokensFile);
                tocModified = true;
                terms.setBlockBasedFile(useBlockBasedTermsFile);
            }
            // Tricks to speed up reading
            // Index mode. Open for writing.
            openTokensFileForWriting();
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
     * Read the table of contents from the file
     */
    protected void readToc() {
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
            ((Buffer)buf).position(buf.position() + SIZEOF_LONG * n);
            IntBuffer ib = buf.asIntBuffer();
            ib.get(length);
            ((Buffer)buf).position(buf.position() + SIZEOF_INT * n);
            buf.get(deleted);
            toc = new ArrayList<>(n);
            deletedTocEntries = new ArrayList<>();
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
    }

    protected void sortDeletedTocEntries() {
        deletedTocEntries.sort(new Comparator<TocEntry>() {
            @Override
            public int compare(TocEntry o1, TocEntry o2) {
                return o1.length - o2.length;
            }
        });
    }



    /**
     * Delete all content in the forward index
     */
    private void clear() {
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
        if (toc != null)
            toc.clear();
        if (deletedTocEntries != null)
            deletedTocEntries.clear();
        tokenFileEndPosition = 0;
        tocModified = true;
    }

    /**
     * Write the table of contents to the file
     */
    private void writeToc() {
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
                ((Buffer)buf).position(buf.position() + SIZEOF_LONG * n);
                IntBuffer ib = buf.asIntBuffer();
                ib.put(length);
                ((Buffer)buf).position(buf.position() + SIZEOF_INT * n);
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

            // Close the FileChannel and RandomAccessFile
            if (writeTokensFileChannel != null) {
                // Cannot truncate if still mapped; cannot force demapping.
                if (File.separatorChar != '\\')
                    writeTokensFileChannel.truncate(tokenFileEndPosition * SIZEOF_INT);
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
            ((Buffer)writeBuffer).position((int) (newDocumentOffset - writeBufOffset));

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
    public synchronized List<int[]> retrievePartsInt(int fiid, int[] start, int[] end) {
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

                int snippetLength = end[i] - start[i];
                int[] snippet = new int[snippetLength];
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
                ((Buffer)buffer).position(0);
                ib = buffer.asIntBuffer();
                ib.get(snippet);
                result.add(snippet);
            }

            return result;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public void deleteDocument(int fiid) {
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

    /**
     * @return the number of documents in the forward index
     */
    @Override
    public int numDocs() {
        if (!initialized)
            initialize();
        return toc.size();
    }

    /**
     * @return the amount of space in free blocks in the forward index.
     */
    @Override
    public long freeSpace() {
        if (!initialized)
            initialize();
        long freeSpace = 0;
        for (TocEntry e : deletedTocEntries) {
            freeSpace += e.length;
        }
        return freeSpace;
    }

    /**
     * @return the number of free blocks in the forward index.
     */
    @Override
    public int freeBlocks() {
        if (!initialized)
            initialize();
        return deletedTocEntries.size();
    }

    /**
     * Gets the length (in tokens) of a document
     *
     * @param fiid forward index id of a document
     * @return length of the document
     */
    @Override
    public int docLength(int fiid) {
        if (!initialized)
            initialize();
        return toc.get(fiid).length;
    }

    /** @return the set of all forward index ids */
    @Override
    public Set<Integer> idSet() {
        if (!initialized)
            initialize();
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



}
