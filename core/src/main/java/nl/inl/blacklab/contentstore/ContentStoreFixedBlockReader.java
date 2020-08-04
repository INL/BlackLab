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
package nl.inl.blacklab.contentstore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.util.SimpleResourcePool;

/**
 * Store string content by id in a compound file and a TOC file. Quickly
 * retrieve (parts of) the string content.
 *
 * Stores files in a file containing fixed-length (4K) blocks of zipped UTF-8. A
 * file allocation table keeps track of each file's blocks as well as the
 * character offset associated with each block so we can quickly access the
 * data. Free blocks will be re-used to save space.
 * 
 * Thread-safety: not thread-safe in index mode, but thread-safe while searching
 */
@NotThreadSafe // in index mode
public class ContentStoreFixedBlockReader extends ContentStoreFixedBlock {
    //private static final Logger logger = LogManager.getLogger(ContentStoreDirFixedBlock.class);

    SimpleResourcePool<Inflater> decompresserPool;

    /**
     * @param dir content store dir
     * @throws ErrorOpeningIndex 
     */
    public ContentStoreFixedBlockReader(File dir) throws ErrorOpeningIndex {
        super(dir);
        if (!dir.exists())
            throw new ErrorOpeningIndex("Dir doesn't exist: " + dir);
        if (!tocFile.exists())
            throw new ErrorOpeningIndex("Toc file doesn't exist: " + tocFile);

        decompresserPool = new SimpleResourcePool<Inflater>(POOL_SIZE) {
            @Override
            public Inflater createResource() {
                return new Inflater();
            }

            @Override
            public void destroyResource(Inflater resource) {
                resource.end();
            }
        };
    }
    
    @Override
    protected void performInitialization() {
        readToc();
    }

    @Override
    protected synchronized void mapToc(boolean writable) throws IOException {
        if (writable)
            throw new UnsupportedOperationException("writable == true, but not in index mode");
        tocRaf = new RandomAccessFile(tocFile, "r");
        long fl = tocFile.length();
        tocFileChannel = tocRaf.getChannel();
        tocFileBuffer = tocFileChannel.map(MapMode.READ_ONLY, 0, fl);
    }

    /**
     * Close the content store.
     */
    @Override
    public void close() {
        if (!initialized)
            initialize();
        decompresserPool.close();
        closeMappedToc();
        super.close();
    }

    /**
     * Retrieve content with given id
     *
     * @param id the id
     * @return the string
     */
    @Override
    public String retrieve(int id) {
        if (!initialized)
            initialize();
        String[] rv = retrieveParts(id, new int[] { -1 }, new int[] { -1 });
        return rv == null ? null : rv[0];
    }

    @Override
    public synchronized String[] retrieveParts(int contentId, int[] start, int[] end) {
        if (!initialized)
            initialize();
        try {
            // Find the correct TOC entry
            TocEntry e = toc.get(contentId);
            if (e == null || e.deleted)
                return null;

            // Sanity-check parameters
            int n = start.length;
            if (n != end.length)
                throw new IllegalArgumentException("start and end must be of equal length");

            // Create array for results
            String[] result = new String[n];

            // Open the file
            try (FileInputStream fileInputStream = new FileInputStream(contentsFile)) {
                try (FileChannel fileChannel = fileInputStream.getChannel()) {
                    // Retrieve the strings requested
                    for (int i = 0; i < n; i++) {
                        int a = start[i];
                        int b = end[i];

                        if (a == -1)
                            a = 0;
                        if (b == -1)
                            b = e.entryLengthCharacters;

                        // Check values
                        if (a < 0 || b < 0) {
                            throw new IllegalArgumentException("Illegal values, start = " + a + ", end = " + b);
                        }
                        if (a > e.entryLengthCharacters || b > e.entryLengthCharacters) {
                            throw new IllegalArgumentException("Value(s) out of range, start = " + a
                                    + ", end = " + b + ", content length = " + e.entryLengthCharacters);
                        }
                        if (b <= a) {
                            throw new IllegalArgumentException(
                                    "Tried to read empty or negative length snippet (from " + a
                                            + " to " + b + ")");
                        }

                        // 1 - determine what blocks to read
                        int firstBlock = -1, lastBlock = -1;
                        int bl = 0;
                        int charOffset = -1;
                        for (int offs : e.blockCharOffsets) {
                            if (offs <= a) {
                                firstBlock = bl; // last block that starts before a
                                charOffset = offs;
                            }
                            if (offs > b && lastBlock == -1) {
                                lastBlock = bl - 1; // first block that ends after b
                                break;
                            }
                            bl++;
                        }
                        if (lastBlock == -1)
                            lastBlock = bl - 1; // last available block

                        // 2 - read and decode blocks
                        StringBuilder decoded = new StringBuilder();
                        for (int j = firstBlock; j <= lastBlock; j++) {
                            long blockNum = e.getBlockNumber(j);
                            long readStartOffset = blockNum * BLOCK_SIZE_BYTES;
                            int bytesToRead = BLOCK_SIZE_BYTES;
                            ByteBuffer buffer = ByteBuffer.allocate(bytesToRead);
                            int bytesRead = fileChannel.read(buffer, readStartOffset);
                            if (bytesRead < bytesToRead) {
                                // Apparently, something went wrong.
                                throw new BlackLabRuntimeException("Not enough bytes read, " + bytesRead
                                        + " < " + bytesToRead);
                            }
                            String decodedBlock = decodeBlock(buffer.array(), 0, bytesRead);
                            decoded.append(decodedBlock);
                        }

                        // 3 - take just what we need
                        int firstChar = a - charOffset;
                        result[i] = decoded.substring(firstChar, firstChar + b - a);
                    }
                }
            }
            return result;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    protected String decodeBlock(byte[] buf, int offset, int length) throws IOException {
        if (!initialized)
            initialize();
        try {
            // unzip block
            Inflater decompresser = decompresserPool.acquire();
            byte[] zipbuf = zipbufPool.acquire();
            try {
                decompresser.reset();
                decompresser.setInput(buf, offset, length);
                int resultLength = decompresser.inflate(zipbuf);
                if (resultLength <= 0) {
                    throw new IOException("Error, inflate returned " + resultLength);
                }
                if (!decompresser.finished()) {
                    // This shouldn't happen because our max block size prevents it
                    throw new IOException("Unzip buffer size insufficient");
                }
                return new String(zipbuf, 0, resultLength, DEFAULT_CHARSET);
            } finally {
                decompresserPool.release(decompresser);
                zipbufPool.release(zipbuf);
            }
        } catch (DataFormatException e) {
            throw new IOException(e);
        }
    }

    @Override
    public int store(String content) {
        throw new UnsupportedOperationException("Not supported if not in index mode");
    }

    @Override
    public int store(byte[] content, int offset, int length, Charset cs) {
        throw new UnsupportedOperationException("Not supported if not in index mode");
    }

    @Override
    public void storePart(String content) {
        throw new UnsupportedOperationException("Not supported if not in index mode");
    }
    
    @Override
    public void storePart(byte[] content, int offset, int length, Charset cs) {
        throw new UnsupportedOperationException("Not supported if not in index mode");
    }

    @Override
    public void clear() throws IOException {
        throw new UnsupportedOperationException("Not supported if not in index mode");
    }

    @Override
    public void delete(int id) {
        throw new UnsupportedOperationException("Not supported if not in index mode");
    }

}
