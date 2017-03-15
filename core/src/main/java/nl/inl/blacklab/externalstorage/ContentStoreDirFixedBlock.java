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
package nl.inl.blacklab.externalstorage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.api.iterator.IntIterator;
import org.eclipse.collections.api.iterator.MutableIntIterator;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import nl.inl.util.CollUtil;
import nl.inl.util.ExUtil;
import nl.inl.util.SimpleResourcePool;

/**
 * Store string content by id in a compound file and a TOC file. Quickly retrieve
 * (parts of) the string content.
 *
 * Stores files in a file containing fixed-length (4K) blocks of zipped UTF-8.
 * A file allocation table keeps track of each file's blocks as well as the
 * character offset associated with each block so we can quickly access the data.
 * Free blocks will be re-used to save space.
 */
public class ContentStoreDirFixedBlock extends ContentStoreDirAbstract {
	private static final Logger logger = LogManager.getLogger(ContentStoreDirFixedBlock.class);

	/** The type of content store. Written to version file and detected when opening. */
	private static final String CONTENT_STORE_TYPE_NAME = "fixedblock";

	/** Version of this type of content store. Written to version file and detected when opening. */
	private static final String CURRENT_VERSION = "1";

	/** Name of the version file */
	private static final String VERSION_FILE_NAME = "version.dat";

	/** Name of the table of contents file */
	private static final String TOC_FILE_NAME = "toc.dat";

	/** Name of the file containing all the original file contents (zipped) */
	private static final String CONTENTS_FILE_NAME = "file-contents.dat";

	/** How many bytes an int consists of (used when repositioning file pointers) */
	private static final int BYTES_PER_INT = Integer.SIZE / Byte.SIZE;

	/**
	 * Block size for the contents file.
	 *
	 * Contributing factors for choosing block size:
	 * - larger blocks improve compression ratio
	 * - larger blocks decrease number of blocks you have to read
	 * - smaller blocks decrease the decompression time
	 * - smaller blocks increase the chance that we only have to read one disk block for a single concordance
	 *   (disk blocks are generally 2 or 4K)
	 * - consider OS I/O caching and memory mapping. Then it becomes the difference between reading
	 *   a few bytes from memory and reading a few kilobytes and decompressing them. Right now,
	 *   making concordances is often CPU-bound (because of decompression?)
	 */
	private static final int BLOCK_SIZE_BYTES = 4096;

	/** How small a block can get without triggering a retry with more input characters */
	private static final int MINIMUM_ACCEPTABLE_BLOCK_SIZE = BLOCK_SIZE_BYTES * 9 / 10;

	/** The expected average compression factor */
	private static final float AVERAGE_COMPRESSION_FACTOR = 4;

	/** A conservative estimate to avoid our estimates going over the block size */
	private static final float CONSERVATIVE_COMPRESSION_FACTOR = AVERAGE_COMPRESSION_FACTOR * 7 / 8;

	/** How many characters we will usually be able to fit within a BLOCK_SIZE */
	private static final int TYPICAL_BLOCK_SIZE_CHARACTERS = (int)(BLOCK_SIZE_BYTES * CONSERVATIVE_COMPRESSION_FACTOR);

	/** The expected maximum compression factor */
	private static final float MAX_COMPRESSION_FACTOR = 20;

	/** Maximum byte size of unencoded block (we make the zip buffer one larger to detect when buffer space was insufficient) */
	private static final int MAX_BLOCK_SIZE_BYTES = (int)(BLOCK_SIZE_BYTES * MAX_COMPRESSION_FACTOR);

	/** How many available characters will trigger a block write. */
	private static final int WRITE_BLOCK_WHEN_CHARACTERS_AVAILABLE = (int)(BLOCK_SIZE_BYTES * MAX_COMPRESSION_FACTOR);

	/** Table of contents entry */
	static class TocEntry {

		/** content store id for this document */
		public int id;

		/** length of the encoded string in bytes */
		public int entryLengthBytes;

		/** length of the decoded string in characters */
		public int entryLengthCharacters;

		/** blocks this document is stored in */
		public int[] blockIndices;

		/** first character stored in each block */
		public int[] blockCharOffsets;

		/** was this entry deleted? (can be removed in next compacting run) */
		public boolean deleted;

		public TocEntry(int id, int length, int charLength, boolean deleted, int[] blockIndices, int[] blockCharOffsets) {
			super();
			this.id = id;
			entryLengthBytes = length;
			entryLengthCharacters = charLength;
			this.deleted = deleted;
			this.blockIndices = blockIndices;
			this.blockCharOffsets = blockCharOffsets;
		}

		/**
		 * Store TOC entry in the TOC file
		 *
		 * @param buf
		 *            where to serialize to
		 * @throws IOException
		 *             on error
		 */
		public void serialize(ByteBuffer buf) throws IOException {
			buf.putInt(id);
			buf.putInt(entryLengthBytes);
			buf.putInt(deleted ? -1 : entryLengthCharacters);
			buf.putInt(blockIndices.length);
			IntBuffer ib = buf.asIntBuffer();
			ib.put(blockIndices);
			ib.put(blockCharOffsets);
			buf.position(buf.position() + blockIndices.length * BYTES_PER_INT * 2);
		}

		/**
		 * Read TOC entry from the TOC file
		 *
		 * @param buf
		 *            the buffer to read from
		 * @return new TocEntry
		 * @throws IOException
		 *             on error
		 */
		public static TocEntry deserialize(ByteBuffer buf) throws IOException {
			int id = buf.getInt();
			int length = buf.getInt();
			int charLength = buf.getInt();
			boolean deleted = charLength < 0;
			int nBlocks = buf.getInt();
			int[] blockIndices = new int[nBlocks];
			int[] blockCharOffsets = new int[nBlocks];
			IntBuffer ib = buf.asIntBuffer();
			ib.get(blockIndices);
			ib.get(blockCharOffsets);
			buf.position(buf.position() + blockIndices.length * BYTES_PER_INT * 2);
			return new TocEntry(id, length, charLength, deleted, blockIndices, blockCharOffsets);
		}

		/**
		 * Get the offset of the first byte of the specified block.
		 *
		 * @param blockNumber
		 *            which block?
		 * @return byte offset of the first byte in the file
		 */
		public int getBlockNumber(int blockNumber) {
			return blockIndices[blockNumber];
		}

		/**
		 * Size of this entry serialized
		 *
		 * @return the size in bytes
		 */
		public int sizeBytes() {
			return 4 * BYTES_PER_INT + blockIndices.length * BYTES_PER_INT * 2;
		}
	}

	/**
	 * The TOC entries
	 */
	private MutableIntObjectMap<TocEntry> toc;

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
	private int writeMapReserve = 1000000; // 1M

	/**
	 * Set the size of the write reserve (the amount of
	 * space allocated at the end of the file). Larger reserves
	 * means less re-mapping.
	 *
	 * The default is 1M bytes.
	 *
	 * @param writeMapReserve size of the reserve in bytes.
	 */
	public void setWriteMapReserve(int writeMapReserve) {
		this.writeMapReserve = writeMapReserve;
	}

	/** Next content ID */
	private int nextId = 1;

	/** The file containing all the original file contents */
	File contentsFile;

	/** Handle into the contents file */
	RandomAccessFile rafContentsFile;

	/** Channel into the contents file */
	FileChannel fchContentsFile;

	/** Keeps track of how many chars were in the blocks we've already written.
	 *  Used by store() to calculate the total content length in chars.
	 */
	private int charsFromEntryWritten = 0;

	/**
	 * If we're writing content in chunks, this keeps track of how many bytes were already written.
	 * Used by store() to calculate the total content length in bytes.
	 */
	private int bytesWritten = 0;

	/** Keeps track of the block ids we've stored parts the current file in so far */
	private IntArrayList blockIndicesWhileStoring;

	/** Keeps track of the char offsets of the blocks of the current file so far */
	private IntArrayList blockCharOffsetsWhileStoring;

	/** If true, the toc file should be updated dat the end */
	private boolean tocModified = false;

	/** Contents still waiting to be written to the contents file in blocks */
	StringBuilder unwrittenContents = new StringBuilder(BLOCK_SIZE_BYTES * 10);

	/** Used to pad blocks that are less than BLOCK_SIZE long */
	private byte[] blockPadding = new byte[BLOCK_SIZE_BYTES];

	/** Total number of blocks in the contents file */
	private int totalBlocks;

	/** The sorted list of free blocks in the contents file */
	private IntArrayList freeBlocks = new IntArrayList();

	/**
	 * @param dir content store dir
	 * @param create if true, create a new content store
	 */
	public ContentStoreDirFixedBlock(File dir, boolean create) {
		this.dir = dir;
		if (!dir.exists())
			dir.mkdir();
		tocFile = new File(dir, TOC_FILE_NAME);
		contentsFile = new File(dir, CONTENTS_FILE_NAME);
		if (create && tocFile.exists()) {
			// Delete the ContentStore files
			tocFile.delete();
			new File(dir, VERSION_FILE_NAME).delete();
			new File(dir, CONTENTS_FILE_NAME).delete();

			// Also delete old content store format files if present
			File[] dataFiles = dir.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir_, String name) {
					return name.matches("data\\d+.dat");
				}
			});
			for (File f : dataFiles) {
				f.delete();
			}
		}
		toc = IntObjectMaps.mutable.empty();  //Maps.mutable.empty();
		if (tocFile.exists())
			readToc();
		tocModified = false;
		if (create) {
			clear();
			if (tocFile.exists())
				tocFile.delete();
			setStoreType();
		}
		blockIndicesWhileStoring = new IntArrayList();
		blockCharOffsetsWhileStoring = new IntArrayList();

		final int POOL_SIZE = 10;
		compresserPool = new SimpleResourcePool<Deflater>(POOL_SIZE){
			@Override
			public Deflater createResource() {
				return new Deflater();
			}

			@Override
			public void destroyResource(Deflater resource) {
				resource.end();
			}
		};
		decompresserPool = new SimpleResourcePool<Inflater>(POOL_SIZE){
			@Override
			public Inflater createResource() {
				return new Inflater();
			}

			@Override
			public void destroyResource(Inflater resource) {
				resource.end();
			}
		};
		zipbufPool = new SimpleResourcePool<byte[]>(POOL_SIZE){
			@Override
			public byte[] createResource() {
				return new byte[MAX_BLOCK_SIZE_BYTES+1]; // one larger to detect when buffer space was insufficient
			}
		};
	}

	/**
	 * Delete all content in the document store
	 */
	@Override
	public void clear() {
		closeContentsFile();

		// delete contents file and empty TOC
		if (contentsFile.exists())
			contentsFile.delete();
		toc.clear();
		freeBlocks.clear();
		tocModified = true;
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
			throw new RuntimeException(e);
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
				tocFileBuffer.position(0);
				int n = tocFileBuffer.getInt();
				totalBlocks = 0;
				for (int i = 0; i < n; i++) {
					TocEntry e = TocEntry.deserialize(tocFileBuffer);
					toc.put(e.id, e);

					// Keep track of the number of blocks
					for (int bl: e.blockIndices) {
						if (bl > totalBlocks - 1)
							totalBlocks = bl + 1;
					}

					// Keep track of what the next ID should be
					if (e.id + 1 > nextId)
						nextId = e.id + 1;
				}
			} finally {
				closeMappedToc();
			}

			// Determine occupied blocks
			boolean[] blockOccupied = new boolean[totalBlocks]; // automatically initialized to false
			int numOccupied = 0;
			for (TocEntry e: toc) {
				for (int bl: e.blockIndices) {
					blockOccupied[bl] = true;
					numOccupied++;
				}
			}
			// Build the list of free blocks
			freeBlocks.clear();
			freeBlocks.ensureCapacity(totalBlocks - numOccupied);
			for (int i = 0; i < totalBlocks; i++) {
				if (!blockOccupied[i])
					freeBlocks.add(i);
			}

		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
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
						tocFileBuffer.position(p);
					}
					e.serialize(tocFileBuffer);
				}
			} finally {
				closeMappedToc();
			}
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
		tocModified = false;
	}

	/**
	 * Close the content store. Writes the table of contents (if modified)
	 */
	@Override
	public void close() {
		compresserPool.close();
		decompresserPool.close();
		zipbufPool.close();

		closeContentsFile();
		if (tocModified) {
			writeToc();
		}
		closeMappedToc();
	}

	/**
	 * Encode and write the block we've compiled so far and reset for next block
	 * @param writeLastBlock if true, we'll write the last block too even if it's not full
	 */
	public void writeBlocks(boolean writeLastBlock) {
		ensureContentsFileOpen();

		// Do we have a block to write?
		while (writeLastBlock && unwrittenContents.length() > 0 || unwrittenContents.length() >= WRITE_BLOCK_WHEN_CHARACTERS_AVAILABLE) {
			int lenBefore = unwrittenContents.length();
			byte[] encoded = encodeBlock(); // encode a number of characters to produce a 4K block
			int lenAfter = unwrittenContents.length();
			int charLen = lenBefore - lenAfter;
			int blockIndex = writeToFreeBlock(encoded);
			blockIndicesWhileStoring.add(blockIndex);
			blockCharOffsetsWhileStoring.add(charsFromEntryWritten);
			charsFromEntryWritten += charLen;
			bytesWritten += encoded.length;
		}
	}

	/**
	 * Writes the block data to a free block and returns the block number.
	 *
	 * @param encoded the block data
	 * @return the block number
	 */
	private int writeToFreeBlock(byte[] encoded) {
		int freeBlock;
		if (freeBlocks.size() == 0) {
			// Add a new one at the end
			totalBlocks++;
			freeBlock = totalBlocks - 1;
		} else {
			// Take the first from the list
			freeBlock = freeBlocks.removeAtIndex(0);
		}
		long offset = (long)freeBlock * BLOCK_SIZE_BYTES;

		// Write data to the block
		try {
			fchContentsFile.position(offset);
			ByteBuffer buf = ByteBuffer.wrap(encoded);
			fchContentsFile.write(buf);
			// pad block with garbage
			buf = ByteBuffer.wrap(blockPadding, 0, BLOCK_SIZE_BYTES - encoded.length);
			fchContentsFile.write(buf);
			return freeBlock;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Store part of a piece of large content. This may be called several times to store chunks of
	 * content, but MUST be *finished* by calling the "normal" store() method. You may call store()
	 * with the empty string if you wish.
	 *
	 * @param content
	 *            the content to store
	 */
	@Override
	public synchronized void storePart(String content) {
		if (content.length() == 0)
			return;

		unwrittenContents.append(content);
		writeBlocks(false);
	}

	/**
	 * Store the given content and assign an id to it.
	 *
	 * @param content
	 *            the content to store
	 * @return the id assigned to the content
	 */
	@Override
	public synchronized int store(String content) {
		storePart(content);
		if (unwrittenContents.length() > 0) {
			// Write the last (not completely full) block
			writeBlocks(true);
		}

		// Convert lists to arrays of primitives for storing
		int[] blockIndices = new int[blockIndicesWhileStoring.size()];
		int i = 0;
		IntIterator it = blockIndicesWhileStoring.intIterator();
		while (it.hasNext()) {
			blockIndices[i] = it.next();
			i++;
		}
		int[] blockCharOffsets = new int[blockCharOffsetsWhileStoring.size()];
		i = 0;
		it = blockCharOffsetsWhileStoring.intIterator();
		while (it.hasNext()) {
			blockCharOffsets[i] = it.next();
			i++;
		}

		TocEntry e = new TocEntry(nextId, bytesWritten, charsFromEntryWritten, false, blockIndices, blockCharOffsets);
		nextId++;
		toc.put(e.id, e);
		tocModified = true;
		charsFromEntryWritten = 0;
		bytesWritten = 0;
		blockIndicesWhileStoring.clear();
		blockCharOffsetsWhileStoring.clear();
		return e.id;
	}

	private void ensureContentsFileOpen() {
		try {
			if (rafContentsFile == null) {
				File theContentsFile = new File(dir, CONTENTS_FILE_NAME);
				rafContentsFile = new RandomAccessFile(theContentsFile, "rw");
				fchContentsFile = rafContentsFile.getChannel();
			}
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Contents file not found" + CONTENTS_FILE_NAME, e);
		}
	}

	private void closeContentsFile() {
		try {
			if (rafContentsFile != null) {
				fchContentsFile.close();
				fchContentsFile = null;
				rafContentsFile.close();
				rafContentsFile = null;
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Retrieve content with given id
	 *
	 * @param id
	 *            the id
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
	 * This is more efficient than retrieving the whole content, or retrieving parts in separate
	 * calls, because the file is only opened once and random access is used to read only the
	 * required parts.
	 *
	 * NOTE: if offset and length are both -1, retrieves the whole content. This is used by the
	 * retrieve(id) method.
	 *
	 * @param contentId
	 *            id of the entry to get substrings from
	 * @param start
	 *            the starting points of the substrings (in characters).
	 *            -1 means "start of document"
	 * @param end
	 *            the end points of the substrings (in characters).
	 *            -1 means "end of document"
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
						for (int offs: e.blockCharOffsets) {
							if (offs <= a) {
								firstBlock = bl; // last block that starts before a
								charOffset = offs;
							}
							if (offs > b && lastBlock == -1) {
								lastBlock = bl - 1;  // first block that ends after b
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
								throw new RuntimeException("Not enough bytes read, " + bytesRead
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
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	@Override
	public synchronized void delete(int id) {
		TocEntry e = toc.get(id);
		e.deleted = true;
		for (int bl: e.blockIndices) {
			freeBlocks.add(bl);
		}
		freeBlocks.sortThis();
		tocModified = true;
	}

	@Override
	public Set<Integer> getDocIds() {
		return CollUtil.toJavaSet(toc.keySet());
	}

	@Override
	public boolean isDeleted(int id) {
		return toc.get(id).deleted;
	}

	@Override
	public int getDocLength(int id) {
		return toc.get(id).entryLengthCharacters;
	}

	SimpleResourcePool<Deflater> compresserPool;

	SimpleResourcePool<Inflater> decompresserPool;

	SimpleResourcePool<byte[]> zipbufPool;

	protected void setStoreType() {
		setStoreType(CONTENT_STORE_TYPE_NAME, CURRENT_VERSION);
	}

	protected byte[] encodeBlock() {

		int length = TYPICAL_BLOCK_SIZE_CHARACTERS;
		int available = unwrittenContents.length();
		if (length > available)
			length = available;

		Deflater compresser = compresserPool.acquire();
		byte[] zipbuf = zipbufPool.acquire();
		boolean doMinCheck = true;
		try {
			while (true) {

				// Serialize to bytes
				byte[] encoded;
				while (true) {
					encoded = unwrittenContents.substring(0, length).getBytes(DEFAULT_CHARSET);

					// Make sure the block fits in our zip buffer
					if (encoded.length <= MAX_BLOCK_SIZE_BYTES)
						break;
					length -= (encoded.length - MAX_BLOCK_SIZE_BYTES) * 2;
					doMinCheck = false;
				}

				// Compress
				compresser.reset();
				compresser.setInput(encoded);
				compresser.finish();
				int compressedDataLength = compresser.deflate(zipbuf, 0, zipbuf.length, Deflater.FULL_FLUSH);
				if (compressedDataLength <= 0) {
					throw new RuntimeException("Error, deflate returned " + compressedDataLength);
				}
				if (compressedDataLength == zipbuf.length) {
					throw new RuntimeException("Error, deflate returned size of zipbuf, this indicates insufficient space");
				}

				// Check the size
				float waste = (float)(BLOCK_SIZE_BYTES - compressedDataLength) / BLOCK_SIZE_BYTES;
				float ratio = (float)length / compressedDataLength;

				if (compressedDataLength > BLOCK_SIZE_BYTES) {
					// Compressed block too large.
					// Shrink the uncompressed data length by 5% more than what we expect to be required.
					float shrinkFactor = 1.0f + (1.05f * (compressedDataLength - BLOCK_SIZE_BYTES)) / BLOCK_SIZE_BYTES;
					logger.debug("Block size too large, retrying. Char length: " + length + ", encoded length: " + compressedDataLength + " > " + BLOCK_SIZE_BYTES + ", shrinkFactor: " + shrinkFactor);
					length = (int)(length / shrinkFactor);
					if (length <= 0)
						length = 1;
					doMinCheck = false; // prevent oscillation between enlarging and shrinking
				} else if (doMinCheck && length < available && compressedDataLength < MINIMUM_ACCEPTABLE_BLOCK_SIZE) {
					// Compressed block too small.
					// Grow the uncompressed data length by 5% less than what we expect is possible.
					float growFactor = 1.0f + (0.95f * (BLOCK_SIZE_BYTES - compressedDataLength)) / compressedDataLength;
					logger.debug("Block size too small, retrying. Char length: " + length + ", encoded length: " + compressedDataLength + " < " + MINIMUM_ACCEPTABLE_BLOCK_SIZE + ", growFactor: " + growFactor);
					length = (int)(length * growFactor);
					if (length > available)
						length = available;
				} else {
					logger.debug("Block ok. Char length: " + length + ", encoded length: " + compressedDataLength + ", waste%: " + waste + ", ratio: " + ratio);
					unwrittenContents.delete(0, length);
					return Arrays.copyOfRange(zipbuf, 0, compressedDataLength);
				}
			}
		} finally {
			compresserPool.release(compresser);
			zipbufPool.release(zipbuf);
		}
	}

	protected String decodeBlock(byte[] buf, int offset, int length) {
		try {
			// unzip block
			Inflater decompresser = decompresserPool.acquire();
			byte[] zipbuf = zipbufPool.acquire();
			try {
				decompresser.reset();
				decompresser.setInput(buf, offset, length);
				int resultLength = decompresser.inflate(zipbuf);
				if (resultLength <= 0) {
					throw new RuntimeException("Error, inflate returned " + resultLength);
				}
				if (!decompresser.finished()) {
					// This shouldn't happen because our max block size prevents it
					throw new RuntimeException("Unzip buffer size insufficient");
				}
				return new String(zipbuf, 0, resultLength, DEFAULT_CHARSET);
			} finally {
				decompresserPool.release(decompresser);
				zipbufPool.release(zipbuf);
			}
		} catch (DataFormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Set<Integer> idSet() {
		final MutableIntSet cids = toc.keySet();
		final MutableIntIterator it = cids.intIterator();
		return new AbstractSet<Integer>() {
			@Override
			public Iterator<Integer> iterator() {
				return new Iterator<Integer>() {
					@Override
					public boolean hasNext() {
						return it.hasNext();
					}

					@Override
					public Integer next() {
						return it.next();
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}

			@Override
			public int size() {
				return cids.size();
			}
		};
	}
}
