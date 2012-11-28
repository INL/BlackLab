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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nl.inl.util.ExUtil;
import nl.inl.util.MemoryUtil;
import nl.inl.util.OsUtil;
import nl.inl.util.VersionFile;

import org.apache.log4j.Logger;

/**
 * Keeps a forward index of documents, to quickly answer the question
 * "what word occurs in doc X at position Y"?
 */
public class ForwardIndex {

	/**
	 * Version history:
	 * 1. Initial version.
	 * 2. Added sort index to terms file.
	 */

	/**
	 * Current forward index format version
	 */
	private static final String CURRENT_VERSION = "2";

	private static final int MAX_DIRECT_BUFFER_SIZE = 2147483647;

	protected static final Logger logger = Logger.getLogger(ForwardIndex.class);

	private static final boolean CACHE_FILE_IN_MEM = true;

	private static final boolean USE_MEMORY_MAPPING = true;

	private static final int SIZEOF_INT = 4;

	/**
	 * The number of integer positions to reserve when mapping the file for writing.
	 */
	final static int WRITE_MAP_RESERVE = 250000; // 250K integers = 1M bytes

	/** The memory mapped write buffer */
	private IntBuffer writeBuffer;

	/** Buffer offset (position in file of start of writeBuffer) in integer positions
	 * (so we don't count bytes, we count ints) */
	private long writeBufOffset;

	/**
	 * The TOC entries
	 */
	private List<TocEntry> toc;

	/**
	 * The table of contents (TOC) file, docs.dat
	 */
	private File tocFile;

	/**
	 * The tokens file (stores indexes into terms.dat)
	 */
	private File tokensFile;

	/**
	 * The terms file (stores unique terms)
	 */
	private File termsFile;

	/**
	 * The unique terms in our index
	 */
	private Terms terms;

	private RandomAccessFile tokensFp;

	private MappedByteBuffer tokensFileMapped = null;

	private ByteBuffer tokensFileInMem = null;

	private FileChannel tokensFileChannel;

	private boolean tocModified = false;

	// private boolean indexMode = false;

	public ForwardIndex(File dir) {
		this(dir, false, null, false);
	}

	public ForwardIndex(File dir, boolean indexMode) {
		this(dir, indexMode, null, false);
	}

	public ForwardIndex(File dir, boolean indexMode, Collator collator, boolean create) {
		logger.debug("Opening forward index " + dir);
		if (!dir.exists())
			dir.mkdir();

		// Version check
		if (!indexMode || !create) {
			// We're opening an existing forward index. Check version.
			if (!VersionFile.isTypeVersion(dir, "fi", CURRENT_VERSION)) {
				throw new RuntimeException("Wrong forward index version: "
						+ VersionFile.report(dir) + " (" + CURRENT_VERSION + " expected)");
			}
		} else {
			// We're creating a forward index. Write version.
			VersionFile.write(dir, "fi", CURRENT_VERSION);
		}

		// this.indexMode = indexMode;
		termsFile = new File(dir, "terms.dat");
		tocFile = new File(dir, "docs.dat");
		tokensFile = new File(dir, "tokens.dat");
		toc = new ArrayList<TocEntry>();
		try {
			boolean existing = false;
			if (tocFile.exists()) {
				readToc();
				terms = new Terms(indexMode, collator, termsFile);
				existing = true;
				tocModified = false;
			} else {
				terms = new Terms(indexMode, collator);
				tokensFile.createNewFile();
				tokensFileMapped = null;
				tocModified = true;
			}
			tokensFp = new RandomAccessFile(tokensFile, indexMode ? "rw" : "r");
			tokensFileChannel = tokensFp.getChannel();

			// Tricks to speed up reading
			if (existing && !create && !OsUtil.isWindows()) { // Memory mapping has issues on
																// Windows
				long free = MemoryUtil.getFree();

				logger.debug("Free memory = " + free);
				if (!indexMode && CACHE_FILE_IN_MEM && free / 2 >= tokensFile.length()
						&& tokensFile.length() < MAX_DIRECT_BUFFER_SIZE) { // Enough free memory;
																			// cache whole file
					logger.debug("FI: reading entire tokens file into memory");

					// NOTE: we can't add to the file this way, so we only use this in search mode
					tokensFileInMem = ByteBuffer.allocate((int) tokensFile.length());
					tokensFileChannel.position(0);
					int bytesRead = tokensFileChannel.read(tokensFileInMem);
					if (bytesRead != tokensFileInMem.capacity()) {
						throw new RuntimeException("Could not read whole tokens file into memory!");
					}
				} else if (!indexMode && USE_MEMORY_MAPPING) {
					logger.debug("FI: memory-mapping the tokens file");

					// Memory-map the file (sometimes fails on Windows with large files..? Increase
					// direct buffer size with cmdline option?)
					// NOTE: we can't add to the file this way! We only use this in search mode.
					// @@@ add support for mapped write (need to re-map as the file grows in size)
					tokensFileMapped = tokensFileChannel.map(FileChannel.MapMode.READ_ONLY, 0,
							tokensFileChannel.size());
				} else {
					// Don't cache whole file in memory, don't use memory mapping. Just read from
					// file channel.
					logger.debug("FI: no memory-mapping tokens file");
				}
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		if (create) {
			clear();
		}
	}

	/**
	 * Delete all content in the forward index
	 */
	private void clear() {
		// delete data files and empty TOC
		try {
			tokensFp.setLength(0);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		termsFile.delete();
		tocFile.delete();
		toc.clear();
		tocModified = true;
	}

	/**
	 * Read the table of contents from the file
	 */
	private void readToc() {
		toc.clear();
		try {
			RandomAccessFile raf = new RandomAccessFile(tocFile, "r");
			long fl = tocFile.length();
			try {
				while (raf.getFilePointer() < fl) {
					TocEntry e = TocEntry.deserialize(raf);
					toc.add(e);
				}
			} finally {
				raf.close();
			}
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	private void writeToc() {
		try {
			RandomAccessFile raf = new RandomAccessFile(tocFile, "rw");
			try {
				for (TocEntry e : toc) {
					e.serialize(raf);
				}
			} finally {
				raf.close();
			}
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
		tocModified = false;
	}

	/**
	 * Close the forward index.
	 * Writes the table of contents to disk if modified.
	 */
	public void close() {
		try {
			tokensFileInMem = null;
			if (tocModified) {
				writeToc();
				terms.write(termsFile);
			}
			tokensFileChannel.close();
			tokensFp.close();
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * Store the given content and assign an id to it
	 *
	 * @param content
	 *            the content to store
	 * @return the id assigned to the content
	 */
	public synchronized int addDocument(List<String> content) {
		try {
			if (writeBuffer == null) {
				// Map writeBuffer
				writeBufOffset = tokensFp.length() / SIZEOF_INT;
				MappedByteBuffer byteBuffer = tokensFileChannel.map(FileChannel.MapMode.READ_WRITE,
						writeBufOffset * SIZEOF_INT, (content.size() + WRITE_MAP_RESERVE) * SIZEOF_INT);
				writeBuffer = byteBuffer.asIntBuffer();
			}

			// Make entry
			long entryOffset = writeBufOffset + writeBuffer.position();
			TocEntry e = new TocEntry(entryOffset, content.size(), false);
			toc.add(e);
			tocModified = true;

			if (writeBuffer.remaining() < content.size()) {
				// Remap writeBuffer so we have space available
				writeBufOffset += writeBuffer.position();

				// NOTE: We reserve more space so we don't have to remap for each document, saving time
				MappedByteBuffer byteBuffer = tokensFileChannel.map(FileChannel.MapMode.READ_WRITE,
						writeBufOffset * SIZEOF_INT, (content.size() + WRITE_MAP_RESERVE) * SIZEOF_INT);
				writeBuffer = byteBuffer.asIntBuffer();
			}

			for (String token : content) {
				writeBuffer.put(terms.indexOf(token));
			}


			/*
			// NOTE: We should not re-map for every document, but use a similar approach as with
			// Terms

			MappedByteBuffer writeBuffer = tokensFileChannel.map(FileChannel.MapMode.READ_WRITE,
					e.offset * SIZEOF_INT, content.size() * SIZEOF_INT);
			writeBuffer.position(0);
			IntBuffer ib = writeBuffer.asIntBuffer();
			for (String token : content) {
				ib.put(terms.indexOf(token));
			}

			// Unmap writeBuffer to prevent file lock
			// NOTE: this doesn't do anything anymore, will be removed soon, see method Javadoc.
			Utilities.cleanDirectBufferHack(writeBuffer);
			*/

			return toc.size() - 1;
		} catch (IOException e1) {
			throw new RuntimeException(e1);
		}
	}

	/**
	 * Store the given content and assign an id to it.
	 *
	 * NOTE: this version uses no memory mapping and is quite a bit slower.
	 *
	 * @param content
	 *            the content to store
	 * @return the id assigned to the content
	 */
	public synchronized int addDocumentNoMapping(List<String> content) {
		try { // Make entry
			TocEntry e = new TocEntry(tokensFp.length() / SIZEOF_INT, content.size(), false);
			toc.add(e);
			tocModified = true;

			tokensFp.seek(e.offset * SIZEOF_INT);
			for (String token : content) {
				tokensFp.writeInt(terms.indexOf(token));
			}
			return toc.size() - 1;
		} catch (IOException e1) {
			throw new RuntimeException(e1);
		}
	}

	/**
	 * Retrieve substring from a document.
	 *
	 * @param id
	 *            content store document id
	 * @param start
	 *            start of the substring
	 * @param end
	 *            end of the substring
	 * @return the substring
	 */
	public String[] retrievePart(int id, int start, int end) {
		return retrieveParts(id, new int[] { start }, new int[] { end }).get(0);
	}

	/**
	 * Retrieve part of a document in token ids form.
	 *
	 * @param id
	 *            content store document id
	 * @param start
	 *            start of the part to retrieve
	 * @param end
	 *            end of the part to retrieve
	 * @return the token ids
	 */
	public int[] retrievePartInt(int id, int start, int end) {
		return retrievePartsInt(id, new int[] { start }, new int[] { end }).get(0);
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
	 * NOTE2: Mapped file IO on Windows has some issues that sometimes cause an OutOfMemoryError on
	 * the FileChannel.map() call (which makes no sense, because memory mapping only uses address
	 * space, it doesn't try to read the whole file). Possibly this could be solved by using 64-bit
	 * Java, but we haven't tried. For now we just disable memory mapping on Windows.
	 *
	 * @param contentId
	 *            id of the entry to get substrings from
	 * @param start
	 *            the starting points of the substrings (in words)
	 * @param end
	 *            the end points of the substrings (in words)
	 * @return the parts
	 */
	public synchronized List<String[]> retrieveParts(int contentId, int[] start, int[] end) {

		// First, retrieve the token ids
		List<int[]> resultInt = retrievePartsInt(contentId, start, end);

		// Translate them to strings using the terms index
		List<String[]> result = new ArrayList<String[]>(resultInt.size());
		for (int[] snippetInt: resultInt) {
			String[] snippet = new String[snippetInt.length];
			for (int j = 0; j < snippetInt.length; j++) {
				snippet[j] = terms.get(snippetInt[j]);
			}
			result.add(snippet);
		}
		return result;
	}

	/**
	 * Retrieve one or more parts from the specified content, in the form of token ids.
	 *
	 * This is more efficient than retrieving the whole content, or retrieving parts in separate
	 * calls, because the file is only opened once and random access is used to read only the
	 * required parts.
	 *
	 * NOTE: if offset and length are both -1, retrieves the whole content. This is used by the
	 * retrieve(id) method.
	 *
	 * NOTE2: Mapped file IO on Windows has some issues that sometimes cause an OutOfMemoryError on
	 * the FileChannel.map() call (which makes no sense, because memory mapping only uses address
	 * space, it doesn't try to read the whole file). Possibly this could be solved by using 64-bit
	 * Java, but we haven't tried. For now we just disable memory mapping on Windows.
	 *
	 * @param contentId
	 *            id of the entry to get parts from
	 * @param start
	 *            the starting points of the parts to retrieve (in words)
	 * @param end
	 *            the end points of the parts to retrieve (in words)
	 * @return the parts
	 */
	public synchronized List<int[]> retrievePartsInt(int contentId, int[] start, int[] end) {
		try {
			TocEntry e = toc.get(contentId);
			if (e == null || e.deleted)
				return null;

			int n = start.length;
			if (n != end.length)
				throw new RuntimeException("start and end must be of equal length");
			List<int[]> result = new ArrayList<int[]>(n);

			// Do we have direct access to the tokens file?
			IntBuffer ib = null;
			boolean inMem = false;
			if (tokensFileMapped != null || tokensFileInMem != null) {
				// Yes, the tokens file has either been fully loaded into memory or
				// is mapped into memory. Get an int buffer into the file.
				inMem = true;
				if (tokensFileInMem != null) {
					// Whole file cached in memory
					tokensFileInMem.position((int) e.offset * SIZEOF_INT);
					ib = tokensFileInMem.asIntBuffer();
				} else {
					// File memory-mapped
					tokensFileMapped.position((int) e.offset * SIZEOF_INT);
					ib = tokensFileMapped.asIntBuffer();
				}
			}
			for (int i = 0; i < n; i++) {
				if (start[i] == -1 && end[i] == -1) {
					// whole content
					start[i] = 0;
					end[i] = e.length;
				}
				if (start[i] < 0 || end[i] < 0) {
					throw new RuntimeException("Illegal values, start = " + start[i] + ", end = "
							+ end[i]);
				}
				if (end[i] >= e.length) // Can happen while making KWICs because we don't know the
										// doc length until here
					end[i] = e.length;
				if (start[i] > e.length || end[i] > e.length) {
					throw new RuntimeException("Value(s) out of range, start = " + start[i]
							+ ", end = " + end[i] + ", content length = " + e.length);
				}
				if (end[i] <= start[i]) {
					throw new RuntimeException(
							"Tried to read empty or negative length snippet (from " + start[i]
									+ " to " + end[i] + ")");
				}

				int snippetLength = end[i] - start[i];
				int[] snippet = new int[snippetLength];
				if (inMem) {
					// The whole file is available in memory (or mem-mapped)
					// Position us at the correct place in the file.
					ib.position(start[i]);
				} else {
					// Not mapped. Explicitly read the part we require from disk into an int buffer.
					long offset = e.offset + start[i];

					int bytesToRead = snippetLength * SIZEOF_INT;
					ByteBuffer buffer = ByteBuffer.allocate(bytesToRead);
					int bytesRead = tokensFileChannel.read(buffer, offset * SIZEOF_INT);
					if (bytesRead < bytesToRead) {
						throw new RuntimeException("Not enough bytes read: " + bytesRead + " < "
								+ bytesToRead);
					}
					buffer.position(0);
					ib = buffer.asIntBuffer();
				}
				for (int j = 0; j < snippetLength; j++) {
					snippet[j] = ib.get();
				}
				result.add(snippet);
			}

			return result;
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	public static void main(String[] args) {
		ForwardIndex fi = new ForwardIndex(new File("E:\\temp"));
		try {
			String test = "the quick brown fox jumps over the lazy dog . the brown quick dog jumps beside the lazy fox";
			int doc = fi.addDocument(Arrays.asList(test.split("\\s+")));
			System.out.println("Stored " + doc);

			test = "fox jumps over dog";
			doc = fi.addDocument(Arrays.asList(test.split("\\s+")));
			System.out.println("Stored " + doc);
		} finally {
			fi.close();
		}
	}

}
