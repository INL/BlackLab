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
import java.io.FileNotFoundException;
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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Keeps a forward index of documents, to quickly answer the question "what word
 * occurs in doc X at position Y"?
 *
 * This implementation is thread-safe.
 */
class AnnotationForwardIndexReader extends AnnotationForwardIndex {

    protected static final Logger logger = LogManager.getLogger(AnnotationForwardIndexReader.class);

    /** Mapping into the tokens file */
    private List<ByteBuffer> tokensFileChunks = null;

    /** Offsets of the mappings into the token file */
    private List<Long> tokensFileChunkOffsetBytes = null;

    /** Collators to use for terms file */
    private Collators collators;

    /** Offset of each document */
    long[] offset;

    /** Length of each document */
    int[] length;

    /** Deleted status of each document */
    byte[] deleted;

    /** Deleted TOC entries. Always sorted by size. */
    List<Integer> deletedTocEntries = null;

    /** Build term indexes right away or lazily? */
    private boolean buildTermIndexesOnInit;

    AnnotationForwardIndexReader(Annotation annotation, File dir, Collators collators, boolean largeTermsFileSupport, boolean buildTermIndexesOnInit) {
        super(annotation, dir, collators, largeTermsFileSupport);

        if (!dir.exists()) {
            throw new IllegalArgumentException("ForwardIndex doesn't exist: " + dir);
        }

        if (!tocFile.exists())
            throw new IllegalArgumentException("No TOC found, and not in index mode: " + tocFile);
        this.collators = collators; // for reading terms file in initialize()
        this.buildTermIndexesOnInit = buildTermIndexesOnInit;
    }

    /**
     * Memory-map the tokens file for reading.
     */
    @Override
    public synchronized void initialize() {
        if (initialized)
            return;

        //logger.debug("  START read TOC " + tocFile);
        readToc();
        //logger.debug("  END   read TOC " + tocFile);

        //logger.debug("  START read Terms " + tocFile);
        terms = Terms.openForReading(collators, termsFile, useBlockBasedTermsFile, buildTermIndexesOnInit);
        //logger.debug("  END   read Terms " + tocFile);
        //logger.debug("  START Terms.initialize() " + tocFile);
        terms.initialize();
        //logger.debug("  END   Terms.initialize() " + tocFile);

        //logger.debug("  START map tokens file " + tocFile);
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
                int mapNextChunkFrom = -1;
                for (int i = 0; i < offset.length; i++) {
                    if (offset[i] <= mappedBytes && (mapNextChunkFrom < 0 || offset[i] > offset[mapNextChunkFrom]))
                        mapNextChunkFrom = i;
                }

                // Uses binary search.
                int min = 0, max = offset.length;
                while (max - min > 1) {
                    int middle = (min + max) / 2;
                    long middleVal = offset[middle] * SIZEOF_INT;
                    if (middleVal <= mappedBytes) {
                        min = middle;
                    } else {
                        max = middle;
                    }
                }
                long startOfNextMappingBytes = offset[min] * SIZEOF_INT;

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
        } catch (FileNotFoundException e1) {
            throw BlackLabRuntimeException.wrap(e1);
        } catch (IOException e1) {
            throw BlackLabRuntimeException.wrap(e1);
        }
        //logger.debug("  END map tokens file " + tocFile);

        //logger.debug("END initialize AFI " + tocFile.getParent());
        initialized = true;
    }

    @Override
    public void close() {
        // NOP
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
            offset = new long[n];
            length = new int[n];
            deleted = new byte[n];
            LongBuffer lb = buf.asLongBuffer();
            lb.get(offset);
            ((Buffer)buf).position(buf.position() + SIZEOF_LONG * n);
            IntBuffer ib = buf.asIntBuffer();
            ib.get(length);
            ((Buffer)buf).position(buf.position() + SIZEOF_INT * n);
            buf.get(deleted);
            deletedTocEntries = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (deleted[i] != 0) {
                    deletedTocEntries.add(i);
                }
                long end = offset[i] + length[i];
                if (end > tokenFileEndPosition)
                    tokenFileEndPosition = end;
            }
            sortDeletedTocEntries();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    protected void sortDeletedTocEntries() {
        deletedTocEntries.sort( (o1, o2) -> length[o1] - length[o2] );
    }


    @Override
    public int addDocument(List<String> content, List<Integer> posIncr) {
        throw new UnsupportedOperationException("Not supported in search mode");
    }

    @Override
    public List<int[]> retrievePartsInt(int fiid, int[] starts, int[] ends) {
        if (!initialized)
            initialize();

        if (deleted[fiid] != 0)
            return null;

        int n = starts.length;
        if (n != ends.length)
            throw new IllegalArgumentException("start and end must be of equal length");
        List<int[]> result = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            int start = starts[i]; // don't modify the start/end array contents!
            int end = ends[i];
            
            if (start == -1)
                start = 0;
            if (end == -1)
                end = length[fiid];
            if (start < 0 || end < 0) {
                throw new IllegalArgumentException("Illegal values, start = " + start + ", end = "
                        + end);
            }
            if (end > length[fiid]) // Can happen while making KWICs because we don't know the
                                   // doc length until here
                end = length[fiid];
            if (start > length[fiid] || end > length[fiid]) {
                throw new IllegalArgumentException("Value(s) out of range, start = " + start
                        + ", end = " + end + ", content length = " + length[fiid]);
            }
            if (end <= start) {
                throw new IllegalArgumentException(
                        "Tried to read empty or negative length snippet (from " + start
                                + " to " + end + ")");
            }

            // Get an IntBuffer to read the desired content
            IntBuffer ib = null;

            // The tokens file has has been mapped to memory.
            // Get an int buffer into the file.

            // Figure out which chunk to access.
            ByteBuffer whichChunk = null;
            long chunkOffsetBytes = -1;
            long entryOffsetBytes = offset[fiid] * SIZEOF_INT;
            for (int j = 0; j < tokensFileChunkOffsetBytes.size(); j++) {
                long offsetBytes = tokensFileChunkOffsetBytes.get(j);
                ByteBuffer buffer = tokensFileChunks.get(j);
                if (offsetBytes <= entryOffsetBytes + start * SIZEOF_INT
                        && offsetBytes + buffer.capacity() >= entryOffsetBytes + end
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
            ((Buffer)whichChunk).position((int) (offset[fiid] * SIZEOF_INT - chunkOffsetBytes));
            ib = whichChunk.asIntBuffer();

            int snippetLength = end - start;
            int[] snippet = new int[snippetLength];

            // The file is mem-mapped (search mode).
            // Position us at the correct place in the file.
            ib.position(start);
            ib.get(snippet);
            result.add(snippet);
        }

        return result;
    }
    
    public List<int[]> retrievePartsIntAllocationless(int fiid, int[] starts, int[] ends, List<int[]> ret) {
        if (!initialized)
            initialize();

        if (deleted[fiid] != 0)
            return null;

        int n = starts.length;
        if (n != ends.length)
            throw new IllegalArgumentException("start and end must be of equal length");
        List<int[]> result = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            int start = starts[i]; // don't modify the start/end array contents!
            int end = ends[i];
            
            if (start == -1)
                start = 0;
            if (end == -1)
                end = length[fiid];
            if (start < 0 || end < 0) {
                throw new IllegalArgumentException("Illegal values, start = " + start + ", end = "
                        + end);
            }
            if (end > length[fiid]) // Can happen while making KWICs because we don't know the
                                   // doc length until here
                end = length[fiid];
            if (start > length[fiid] || end > length[fiid]) {
                throw new IllegalArgumentException("Value(s) out of range, start = " + start
                        + ", end = " + end + ", content length = " + length[fiid]);
            }
            if (end <= start) {
                throw new IllegalArgumentException(
                        "Tried to read empty or negative length snippet (from " + start
                                + " to " + end + ")");
            }

            // Get an IntBuffer to read the desired content
            IntBuffer ib = null;

            // The tokens file has has been mapped to memory.
            // Get an int buffer into the file.

            // Figure out which chunk to access.
            ByteBuffer whichChunk = null;
            long chunkOffsetBytes = -1;
            long entryOffsetBytes = offset[fiid] * SIZEOF_INT;
            for (int j = 0; j < tokensFileChunkOffsetBytes.size(); j++) {
                long offsetBytes = tokensFileChunkOffsetBytes.get(j);
                ByteBuffer buffer = tokensFileChunks.get(j);
                if (offsetBytes <= entryOffsetBytes + start * SIZEOF_INT
                        && offsetBytes + buffer.capacity() >= entryOffsetBytes + end
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
            ((Buffer)whichChunk).position((int) (offset[fiid] * SIZEOF_INT - chunkOffsetBytes));
            ib = whichChunk.asIntBuffer();

            int snippetLength = end - start;
            int[] snippet = new int[snippetLength];

            // The file is mem-mapped (search mode).
            // Position us at the correct place in the file.
            ib.position(start);
            ib.get(snippet);
            result.add(snippet);
        }

        return result;
    }

    @Override
    public void deleteDocument(int fiid) {
        throw new UnsupportedOperationException("Not supported in search mode");
    }

    /**
     * @return the number of documents in the forward index
     */
    @Override
    public int numDocs() {
        if (!initialized)
            initialize();
        return offset.length;
    }

    /**
     * @return the amount of space in free blocks in the forward index.
     */
    @Override
    public long freeSpace() {
        if (!initialized)
            initialize();
        long freeSpace = 0;
        for (Integer e : deletedTocEntries) {
            freeSpace += length[e];
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
        return length[fiid];
    }


    /** @return the set of all forward index ids */
    @Override
    public Set<Integer> idSet() {
        if (!initialized)
            initialize();
        return new AbstractSet<Integer>() {
            @Override
            public boolean contains(Object o) {
                return deleted[(Integer)o] == 0;
            }

            @Override
            public boolean isEmpty() {
                return offset.length == deletedTocEntries.size();
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
                        return next < offset.length;
                    }

                    private void findNext() {
                        next = current + 1;
                        while (next < offset.length && deleted[next] != 0) {
                            next++;
                        }
                    }

                    @Override
                    public Integer next() {
                        if (next < 0)
                            findNext();
                        if (next >= offset.length)
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
                return offset.length - deletedTocEntries.size();
            }
        };
    }



}
