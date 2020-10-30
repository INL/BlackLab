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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.collections.impl.factory.Maps;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * Store string content by id in a directory of compound files with a TOC file.
 * Quickly retrieve (parts of) the string content.
 *
 * This implementation stores the strings in UTF-8 encoding to save disk space.
 * To guarantee reasonably fast random access times, we keep track of "block
 * offsets", which are byte offsets to the start of (fixed char size) blocks.
 * Block size in bytes can be slightly larger than char size because some UTF-8
 * characters take up more than 1 byte. If the block size is 1000 chars, block
 * offsets might be [0, 1011, 2015, 3020].
 *
 * Thread-safety: not thread-safe in index mode, but thread-safe while searching
 */
@NotThreadSafe // in index mode
public class ContentStoreDirUtf8 extends ContentStoreDirAbstract {
    /** Table of contents entry */
    static class TocEntry {
        /** id of the string */
        int id;

        /** id of the file the string was stored in */
        int fileId;

        /** byte offset into the file of the string */
        int entryOffsetBytes;

        /** length of the string in bytes */
        int entryLengthBytes;

        /** length of the string in characters */
        int entryLengthCharacters;

        /** fixed block size in characters (note that byte size differs per block) */
        int blockSizeCharacters;

        /** relative block start offsets in bytes */
        int[] blockOffsetBytes;

        /** was this entry deleted? (can be removed in next compacting run) */
        boolean deleted;

        TocEntry(int id, int fileId, int offset, int length, int charLength, int blockSize,
                boolean deleted, int[] blockOffset) {
            super();
            this.id = id;
            this.fileId = fileId;
            entryOffsetBytes = offset;
            entryLengthBytes = length;
            entryLengthCharacters = charLength;
            blockSizeCharacters = blockSize;
            this.deleted = deleted;
            blockOffsetBytes = blockOffset;
        }

        /**
         * Store TOC entry in the TOC file
         *
         * @param buf where to serialize to
         * @throws IOException on error
         */
        void serialize(ByteBuffer buf) throws IOException {
            buf.putInt(id);
            buf.putInt(fileId);
            buf.putInt(entryOffsetBytes);
            buf.putInt(entryLengthBytes);
            buf.putInt(deleted ? -1 : entryLengthCharacters);
            buf.putInt(blockSizeCharacters);
            buf.putInt(blockOffsetBytes.length);
            IntBuffer ib = buf.asIntBuffer();
            ib.put(blockOffsetBytes);
            ((Buffer)buf).position(buf.position() + blockOffsetBytes.length * Integer.SIZE / Byte.SIZE);
        }

        /**
         * Read TOC entry from the TOC file
         *
         * @param buf the buffer to read from
         * @return new TocEntry
         * @throws IOException on error
         */
        static TocEntry deserialize(ByteBuffer buf) throws IOException {
            int id = buf.getInt();
            int fileId = buf.getInt();
            int offset = buf.getInt();
            int length = buf.getInt();
            int charLength = buf.getInt();
            boolean deleted = charLength < 0;
            int blockSize = buf.getInt();
            int nBlocks = buf.getInt();
            int[] blockOffsetBytes = new int[nBlocks];
            IntBuffer ib = buf.asIntBuffer();
            ib.get(blockOffsetBytes);
            ((Buffer)buf).position(buf.position() + blockOffsetBytes.length * Integer.SIZE / Byte.SIZE);
            return new TocEntry(id, fileId, offset, length, charLength, blockSize, deleted,
                    blockOffsetBytes);
        }

        /**
         * Get the offset of the first byte of the specified block.
         *
         * @param blockNumber which block?
         * @return byte offset of the first byte in the file
         */
        long getBlockStartOffset(int blockNumber) {
            return entryOffsetBytes + blockOffsetBytes[blockNumber];
        }

        /**
         * Get the offset of the first byte beyond the specified block.
         *
         * @param blockNumber which block?
         * @return byte offset of the first byte beyond the block in the file
         */
        long getBlockEndOffset(int blockNumber) {
            if (blockNumber < blockOffsetBytes.length - 1)
                return entryOffsetBytes + blockOffsetBytes[blockNumber + 1];
            return entryOffsetBytes + entryLengthBytes;
        }

        /**
         * Size of this entry serialized
         *
         * @return the size in bytes
         */
        int sizeBytes() {
            return 28 + blockOffsetBytes.length * 4;
        }
    }

    /**
     * The TOC entries
     */
    private Map<Integer, TocEntry> toc;

    /**
     * The table of contents (TOC) file
     */
    private File tocFile;

    /**
     * Memory mapping of the TOC file
     */
    private ByteBuffer tocFileBuffer;

    /**
     * The TOC file channel.
     */
    private FileChannel tocFileChannel;

    /**
     * The TOC random access file
     */
    private RandomAccessFile tocRaf;

    /**
     * How much to reserve at the end of mapped file for writing
     */
    private int writeMapReserve = 1_000_000; // 1M

    /**
     * Set the size of the write reserve (the amount of space allocated at the end
     * of the file). Larger reserves means less re-mapping.
     *
     * The default is 1M bytes.
     *
     * @param writeMapReserve size of the reserve in bytes.
     */
    public void setWriteMapReserve(int writeMapReserve) {
        this.writeMapReserve = writeMapReserve;
    }

    /**
     * Preferred size of data files. Note that the data files consist only of whole
     * documents, so this size may be exceeded.
     */
    private long dataFileSizeHint = 100_000_000; // 100M

    /**
     * Next content ID.
     */
    private int nextId = 1;

    /**
     * File ID of the current file we're writing to.
     */
    private int currentFileId = 1;

    /**
     * Length of the file we're writing to.
     */
    private int currentFileLength = 0;

    /**
     * When writing, this is the outputstream to the current store file. We save it
     * to save on open/close costs.
     */
    OutputStream currentStoreFileStream = null;

    /**
     * If we're writing content in chunks, this keeps track of how many chars were
     * already written. Used by store() to calculate the total content length in
     * chars.
     */
    private int charsFromEntryWritten = 0;

    /**
     * If we're writing content in chunks, this keeps track of how many bytes were
     * already written. Used by store() to calculate the total content length in
     * bytes.
     */
    private int bytesWritten = 0;

    private List<Integer> blockOffsetWhileStoring;

    /**
     * What block size to use when adding a new document to the content store.
     * Contributing factors for choosing block size:
     *
     * <ul>
     * <li>larger blocks improve compression ratio</li>
     * <li>larger blocks decrease number of blocks you have to read</li>
     * <li>smaller blocks decrease the decompression time</li>
     * <li>smaller blocks increase the chance that we only have to read
     * one disk block for a single concordance (disk blocks are generally 2 or 4K)</li>
     * <li>consider OS I/O caching and memory mapping. Then it becomes the difference between reading a few bytes from
     * memory and reading a few kilobytes and decompressing them. Right now, making
     * concordances is often CPU-bound (because of decompression?)</li>
     * </ul>
     */
    protected int newEntryBlockSizeCharacters = 4000;

    private boolean tocModified = false;

    StringBuilder currentBlockContents = new StringBuilder(newEntryBlockSizeCharacters);

    /**
     * Set the desired block size for new entries
     *
     * @param size the fixed block size in characters
     */
    public void setBlockSizeCharacters(int size) {
        newEntryBlockSizeCharacters = size;
    }

    /**
     * @param dir content store dir
     */
    public ContentStoreDirUtf8(File dir) {
        this(dir, false);
    }

    /**
     * @param dir content store dir
     * @param create if true, create a new content store
     */
    public ContentStoreDirUtf8(File dir, boolean create) {
        super(dir);
        if (!dir.exists() && !dir.mkdir())
            throw new BlackLabRuntimeException("Could not create dir: " + dir);
        tocFile = new File(dir, "toc.dat");
        if (create && tocFile.exists()) {
            // Delete the ContentStore files
            if (!tocFile.delete())
                throw new BlackLabRuntimeException("Could not delete file: " + tocFile);
            File versionFile = new File(dir, "version.dat");
            if (versionFile.exists() && !versionFile.delete())
                throw new BlackLabRuntimeException("Could not delete file: " + versionFile);
            File[] dataFiles = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir1, String name) {
                    return name.matches("data\\d+.dat");
                }
            });
            if (dataFiles == null)
                throw new BlackLabRuntimeException("Error finding old data files in content store dir: " + dir);
            for (File f : dataFiles) {
                if (!f.delete())
                    throw new BlackLabRuntimeException("Could not delete file: " + f);
            }
        }
        toc = Maps.mutable.empty();
        if (tocFile.exists())
            readToc();
        tocModified = false;
        if (create) {
            clear();
            if (tocFile.exists() && !tocFile.delete())
                throw new BlackLabRuntimeException("Could not delete file: " + tocFile);
            setStoreType();
        }
        blockOffsetWhileStoring = new ArrayList<>();
    }

    /**
     * Writes an empty file that indicates the type of store. Subclasses should
     * override this to customize the type string.
     */
    protected void setStoreType() {
        setStoreType("utf8", "1");
    }

    /**
     * Delete all content in the document store
     */
    @Override
    public void clear() {
        closeCurrentStoreFile();

        // delete all data files and empty TOC
        for (Map.Entry<Integer, TocEntry> me : toc.entrySet()) {
            TocEntry e = me.getValue();
            File f = getContentFile(e.fileId);
            if (f.exists() && !f.delete())
                throw new BlackLabRuntimeException("Could not delete file: " + f);
        }
        toc.clear();
        tocModified = true;
        currentFileId = 1;
        currentFileLength = 0;
        nextId = 1;
    }

    private void mapToc(boolean writeable) throws IOException {
        tocRaf = new RandomAccessFile(tocFile, writeable ? "rw" : "r");
        long fl = tocFile.length();
        if (writeable) {
            fl += writeMapReserve;
        } // leave 1M room at the end
        tocFileChannel = tocRaf.getChannel();
        tocFileBuffer = tocFileChannel.map(writeable ? MapMode.READ_WRITE : MapMode.READ_ONLY, 0, fl);
    }

    private void closeMappedToc() {
        if (tocFileBuffer == null)
            return; // not mapped
        try {
            tocFileChannel.close();
            tocFileChannel = null;
            tocRaf.close();
            tocRaf = null;

            tocFileBuffer = null;

        } catch (IOException e) {
            BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Read the table of contents from the file
     */
    private void readToc() {
        toc.clear();
        try {
            mapToc(false);
            try {
                ((Buffer)tocFileBuffer).position(0);
                int n = tocFileBuffer.getInt();
                for (int i = 0; i < n; i++) {
                    TocEntry e = TocEntry.deserialize(tocFileBuffer);
                    toc.put(e.id, e);

                    // Keep track of the current content file (file with highest ID)
                    if (e.fileId > currentFileId) {
                        currentFileId = e.fileId;
                        currentFileLength = 0;
                    }

                    // Keep track of the length of the current content file
                    if (e.fileId == currentFileId) {
                        int endOfEntry = e.entryOffsetBytes + e.entryLengthBytes;
                        if (endOfEntry > currentFileLength)
                            currentFileLength = endOfEntry;
                    }

                    // Keep track of what the next ID should be
                    if (e.id + 1 > nextId)
                        nextId = e.id + 1;
                }
            } finally {
                closeMappedToc();
            }
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    private void writeToc() {
        try {
            mapToc(true);
            tocFileBuffer.putInt(toc.size());
            try {
                for (TocEntry e : toc.values()) {
                    if (tocFileBuffer.remaining() < e.sizeBytes()) {
                        // Close and re-open with extra writing room
                        int p = tocFileBuffer.position();
                        closeMappedToc();
                        mapToc(true);
                        ((Buffer)tocFileBuffer).position(p);
                    }
                    e.serialize(tocFileBuffer);
                }
            } finally {
                closeMappedToc();
            }
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
        tocModified = false;
    }

    /**
     * Close the content store. Writes the table of contents (if modified)
     */
    @Override
    public void close() {
        closeCurrentStoreFile();
        if (tocModified) {
            writeToc();
        }
        closeMappedToc();
    }

    /**
     * Indicate preferred maximum size of data files (defaults to 10M)
     *
     * @param dataFileSizeHint
     */
    public void setDataFileSizeHint(long dataFileSizeHint) {
        this.dataFileSizeHint = dataFileSizeHint;
    }

    /**
     * Add a piece of content to the current block
     *
     * @param contentPart content to add
     */
    public void addToBlock(String contentPart) {
        currentBlockContents.append(contentPart);
    }

    public void addToBlock(char[] contentPart, int start, int length) {
        currentBlockContents.append(contentPart, start, length);
    }

    /**
     * Encode and write the block we've compiled so far and reset for next block
     *
     * @param os where to write to block to
     */
    public void writeCurrentBlock(OutputStream os) {
        try {
            // Encode and write block

            // OPT: we could try memory-mapping the current content store file (not sure if
            // you can grow a file in size while memory-mapped? maybe just put it at a fixed
            // size?) for additional speed.

            String blockContent = currentBlockContents.toString();
            if (blockContent.length() == 0)
                throw new BlackLabRuntimeException("ERROR, tried to write an empty block");
            byte[] buf = encodeBlock(blockContent);
            os.write(buf);
            bytesWritten += buf.length;
            currentBlockContents = new StringBuilder(newEntryBlockSizeCharacters);
        } catch (IOException e) {
            BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Store part of a piece of large content. This may be called several times to
     * store chunks of content, but MUST be *finished* by calling the "normal"
     * store() method. You may call store() with the empty string if you wish.
     *
     * @param content the content to store
     */
    @Override
    public synchronized void storePart(String content) {
        if (content.length() == 0)
            return;
        if (blockOffsetWhileStoring.isEmpty())
            blockOffsetWhileStoring.add(0); // first offset is always 0

        // Calculate what charsFromEntryWritten will be after storing this part
        // (used to determine if we will cross a block boundary)
        int offsetAfterThisPart = charsFromEntryWritten + content.length();

        // See if we're about to cross any block boundaries; if so, write the content up to the
        // first block boundary, save the new block offset, and repeat.
        int thisPartCharsWritten = 0, thisPartCharsLeftToWrite = content.length();

        OutputStream os = openCurrentStoreFile();
        while (true) {
            // Will we cross a(nother) block boundary writing this part of the content?
            int nextBlockBoundary = blockOffsetWhileStoring.size() * newEntryBlockSizeCharacters;
            boolean willWeCrossBlockBoundary = offsetAfterThisPart > nextBlockBoundary;
            if (!willWeCrossBlockBoundary) {
                // No; break out of this loop and write the last bit of content.
                break;
            }

            // How many characters till the block boundary?
            int charsRemainingInBlock = nextBlockBoundary - charsFromEntryWritten;

            // Are we at the block boundary already?
            if (charsRemainingInBlock > 0) {
                // No, not at the boundary yet; add last piece to block
                // and update bookkeeping variables
                addToBlock(content.substring(thisPartCharsWritten, thisPartCharsWritten
                        + charsRemainingInBlock));
                charsFromEntryWritten += charsRemainingInBlock;

                thisPartCharsWritten += charsRemainingInBlock;
                thisPartCharsLeftToWrite -= charsRemainingInBlock;
            }

            // We are now at a block boundary. Write the block and
            // store next block offset.
            if (currentBlockContents.length() > 0) {
                writeCurrentBlock(os);
                blockOffsetWhileStoring.add(bytesWritten);
            }
        }
        // No more block boundaries to cross. If there's any content left to write in the
        // current block,
        // do so now.
        if (thisPartCharsLeftToWrite > 0) {
            addToBlock(content.substring(thisPartCharsWritten, thisPartCharsWritten
                    + thisPartCharsLeftToWrite));
            charsFromEntryWritten += thisPartCharsLeftToWrite;
        }
    }

    @Override
    public void storePart(byte[] content, int offset, int length, Charset cs) {
        if (length <= 0)
            return;
        if (blockOffsetWhileStoring.isEmpty())
            blockOffsetWhileStoring.add(0); // first offset is always 0

        OutputStream os = openCurrentStoreFile();

        CharsetDecoder cd = cs.newDecoder();
        ByteBuffer in = ByteBuffer.wrap(content, offset, length);
        CharBuffer out = CharBuffer.allocate(newEntryBlockSizeCharacters);
        while (in.remaining() > 0) {
            int charsLeftInCurrentBlock = (blockOffsetWhileStoring.size() * newEntryBlockSizeCharacters) - charsFromEntryWritten;
            out.limit(charsLeftInCurrentBlock);

            cd.decode(in, out, true);
            addToBlock(out.array(), 0, out.position());
            charsFromEntryWritten += out.position();
            ((Buffer)out).position(0);

            if ((charsFromEntryWritten % newEntryBlockSizeCharacters) == 0) {
                writeCurrentBlock(os);
                blockOffsetWhileStoring.add(bytesWritten);
            }
        }
    }

    /**
     * Convert the String representation of a block to a byte buffer
     *
     * @param block the block content
     * @return the byte buffer representation
     */
    protected byte[] encodeBlock(String block) {
        return block.getBytes(DEFAULT_CHARSET);
    }

    /**
     * Convert the byte buffer representation of a block back to the original String
     *
     * @param buf the byte buffer
     * @param offset offset in the buffer
     * @param length length of the block in bytes
     * @return the original String content
     */
    protected String decodeBlock(byte[] buf, int offset, int length) {
        return new String(buf, offset, length, DEFAULT_CHARSET);
    }

    /**
     * Store the given content and assign an id to it
     *
     * @param content the content to store
     * @return the id assigned to the content
     */
    @Override
    public synchronized int store(String content) {
        storePart(content);
        return store();
    }

    @Override
    public int store(byte[] content, int offset, int length, Charset cs) {
        storePart(content, offset, length, cs);
        return store();
    }

    private int store() {
        if (currentBlockContents.length() > 0) {
            // Write the last (not completely full) block
            OutputStream os = openCurrentStoreFile();
            writeCurrentBlock(os);
        }
        int[] blockOffsetArray = new int[blockOffsetWhileStoring.size()];
        int i = 0;
        for (Integer bo : blockOffsetWhileStoring) {
            blockOffsetArray[i] = bo;
            i++;
        }
        TocEntry e = new TocEntry(nextId, currentFileId, currentFileLength, bytesWritten,
                charsFromEntryWritten, newEntryBlockSizeCharacters, false, blockOffsetArray);
        nextId++;
        currentFileLength += bytesWritten;
        toc.put(e.id, e);
        tocModified = true;
        charsFromEntryWritten = 0;
        bytesWritten = 0;
        blockOffsetWhileStoring = new ArrayList<>();
        return e.id;
    }

    private OutputStream openCurrentStoreFile() {
        try {
            boolean createNew = false;
            if (currentFileLength > dataFileSizeHint) {
                // Current file is full!
                currentFileId++;
                currentFileLength = 0;
                if (currentStoreFileStream != null)
                    currentStoreFileStream.close();
                currentStoreFileStream = null;
                createNew = true; // start a new store file; delete if old stuff hanging around
            }
            if (currentStoreFileStream == null) {
                File f = getContentFile(currentFileId);
                if (createNew && f.exists() && !f.delete()) // leftover from previous index; delete
                    throw new BlackLabRuntimeException("Could not delete file: " + f);
                currentStoreFileStream = new BufferedOutputStream(new FileOutputStream(f, true));
            }
            return currentStoreFileStream;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    private void closeCurrentStoreFile() {
        try {
            if (currentStoreFileStream != null)
                currentStoreFileStream.close();
        } catch (IOException e) {
            BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Get a data File object, given the data file id.
     *
     * @param fileId the data file id
     * @return the File object
     */
    private File getContentFile(int fileId) {
        return new File(dir, String.format("data%04d.dat", fileId));
    }

    /**
     * Retrieve content with given id
     *
     * @param id the id
     * @return the string
     */
    @Override
    public String retrieve(int id) {
        String[] rv = retrieveParts(id, new int[] { -1 }, new int[] { -1 });
        return rv == null ? null : rv[0];
    }

    /**
     * Retrieve one or more substrings from the specified content.
     *
     * This is more efficient than retrieving the whole content, or retrieving parts
     * in separate calls, because the file is only opened once and random access is
     * used to read only the required parts.
     *
     * NOTE: if offset and length are both -1, retrieves the whole content. This is
     * used by the retrieve(id) method.
     *
     * @param contentId id of the entry to get substrings from
     * @param start the starting points of the substrings (in characters). -1 means
     *            "start of document"
     * @param end the end points of the substrings (in characters). -1 means "end of
     *            document"
     * @return the parts
     */
    @Override
    public synchronized String[] retrieveParts(int contentId, int[] start, int[] end) {
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
            try (FileInputStream fileInputStream = new FileInputStream(getContentFile(e.fileId))) {
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
                        int firstBlock = a / e.blockSizeCharacters;
                        int lastBlock = (b - 1) / e.blockSizeCharacters;

                        // 2 - read and decode blocks
                        StringBuilder decoded = new StringBuilder();
                        for (int j = firstBlock; j <= lastBlock; j++) {
                            long readStartOffset = e.getBlockStartOffset(j);
                            int bytesToRead = (int) (e.getBlockEndOffset(j) - readStartOffset);
                            ByteBuffer buffer = ByteBuffer.allocate(bytesToRead);
                            int bytesRead = fileChannel.read(buffer, readStartOffset);
                            if (bytesRead < bytesToRead) {
                                // Apparently, something went wrong.
                                throw new BlackLabRuntimeException("Not enough bytes read, " + bytesRead
                                        + " < " + bytesToRead);
                            }
                            decoded.append(decodeBlock(buffer.array(), 0, bytesRead));
                        }

                        // 3 - take just what we need
                        int firstChar = a % e.blockSizeCharacters;
                        String decodedStr = decoded.toString();
                        try {
                            result[i] = decodedStr.substring(firstChar, firstChar + b - a);
                        } catch (StringIndexOutOfBoundsException e1) {
                            System.err.println("ERROR!\ndecodedStr.length() = " + decodedStr.length() + "\n" +
                                    "firstChar = " + firstChar + "\na = " + a + "\nb = " + b +
                                    "\nfirstBlock = " + firstBlock + "\nlastBlock = " + lastBlock);
                            System.err.flush();
                            throw e1;
                        }
                    }
                }
            }
            return result;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public synchronized void delete(int id) {
        TocEntry e = toc.get(id);
        e.deleted = true;
        tocModified = true;
    }

    @Override
    public boolean isDeleted(int id) {
        return toc.get(id).deleted;
    }

    @Override
    public int docLength(int id) {
        return toc.get(id).entryLengthCharacters;
    }

    @Override
    public Set<Integer> idSet() {
        return toc.keySet();
    }

    @Override
    public void initialize() {
        // NOP
    }

}
