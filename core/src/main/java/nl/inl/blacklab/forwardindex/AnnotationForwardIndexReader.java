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
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

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
    
    /** Has the tokens file been mapped? */
    private boolean initialized = false;

    AnnotationForwardIndexReader(File dir, Collators collators, boolean largeTermsFileSupport) {
        super(dir, collators, largeTermsFileSupport);
        
        if (!dir.exists()) {
            throw new IllegalArgumentException("ForwardIndex doesn't exist: " + dir);
        }

        if (tocFile.exists()) {
            readToc();
            terms = Terms.openForReading(collators, termsFile, useBlockBasedTermsFile);
        } else {
            throw new IllegalArgumentException("No TOC found, and not in index mode!");
        }
        logger.debug("Opened forward index " + dir);
    }

    /**
     * Memory-map the tokens file for reading.
     */
    @Override
    public synchronized void initialize() {
        if (initialized)
            return;
        
        terms.initialize();
        
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
        } catch (FileNotFoundException e1) {
            throw BlackLabRuntimeException.wrap(e1);
        } catch (IOException e1) {
            throw BlackLabRuntimeException.wrap(e1);
        }
        
        initialized = true;
    }

    @Override
    public void close() {
        // NOP
    }
    
    @Override
    public int addDocument(List<String> content, List<Integer> posIncr) {
        throw new UnsupportedOperationException("Not supported in search mode");
    }

    @Override
    public List<int[]> retrievePartsIntByFiid(int fiid, int[] start, int[] end) {
        if (!initialized)
            initialize();
        
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
            
            // The tokens file has has been mapped to memory.
            // Get an int buffer into the file.

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

            int snippetLength = end[i] - start[i];
            int[] snippet = new int[snippetLength];
            
            // The file is mem-mapped (search mode).
            // Position us at the correct place in the file.
            ib.position(start[i]);
            ib.get(snippet);
            result.add(snippet);
        }

        return result;
    }

    @Override
    public void deleteDocumentByFiid(int fiid) {
        throw new UnsupportedOperationException("Not supported in search mode");
    }

}
