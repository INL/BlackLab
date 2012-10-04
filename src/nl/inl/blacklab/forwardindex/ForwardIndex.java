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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nl.inl.util.ExUtil;
import nl.inl.util.MemoryUtil;
import nl.inl.util.OsUtil;
import nl.inl.util.Utilities;
import nl.inl.util.VersionFile;

import org.apache.log4j.Logger;

/**
 * Keeps a forward index of documents, to quickly answer the question
 * "what word occurs in doc X at position Y"?
 */
public class ForwardIndex {

	private static final int MAX_DIRECT_BUFFER_SIZE = 2147483647;

	protected static final Logger logger = Logger.getLogger(ForwardIndex.class);

	private static final boolean CACHE_FILE_IN_MEM = true;

	private static final boolean USE_MEMORY_MAPPING = true;

	private static final int SIZEOF_INT = 4;

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
		this(dir, false, false);
	}

	public ForwardIndex(File dir, boolean indexMode) {
		this(dir, indexMode, false);
	}

	public ForwardIndex(File dir, boolean indexMode, boolean create) {
		logger.debug("Opening forward index " + dir);
		if (!dir.exists())
			dir.mkdir();

		// Version check
		if (!indexMode || !create) {
			// We're opening an existing forward index. Check version.
			if (!VersionFile.isTypeVersion(dir, "fi", "1")) {
				throw new RuntimeException("Unknown forward index version: "
						+ VersionFile.report(dir));
			}
		} else {
			// We're creating a forward index. Write version.
			VersionFile.write(dir, "fi", "1");
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
				terms = new Terms(termsFile, indexMode);
				existing = true;
				tocModified = false;
			} else {
				terms = new Terms(indexMode);
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
	 * Delete all content in the document store
	 */
	public void clear() {
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
	 * Write the table of contents to the file
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
			// Make entry
			TocEntry e = new TocEntry(tokensFp.length() / SIZEOF_INT, content.size(), false);
			toc.add(e);
			tocModified = true;

			// TODO: We should not re-map for every document, but use a similar approach as with
			// Terms

			MappedByteBuffer buffer = tokensFileChannel.map(FileChannel.MapMode.READ_WRITE,
					e.offset * SIZEOF_INT, content.size() * SIZEOF_INT);
			buffer.position(0);
			IntBuffer ib = buffer.asIntBuffer();
			for (String token : content) {
				ib.put(terms.indexOf(token));
			}

			// Unmap buffer to prevent file lock
			Utilities.cleanDirectBufferHack(buffer);

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
		if (tokensFileMapped != null || tokensFileInMem != null)
			return retrievePartsMapped(contentId, start, end);
		return retrievePartsUnmapped(contentId, start, end);
	}

	/**
	 * Non-Windows version of retrieveParts (uses memory mapping)
	 */
	private synchronized List<String[]> retrievePartsMapped(int contentId, int[] start, int[] end) {
		try {
			TocEntry e = toc.get(contentId);
			if (e == null || e.deleted)
				return null;

			int n = start.length;
			if (n != end.length)
				throw new RuntimeException("start and end must be of equal length");
			List<String[]> result = new ArrayList<String[]>(n);
			IntBuffer ib;
			if (tokensFileInMem != null) {
				// Whole file cached in memory
				tokensFileInMem.position((int) e.offset * SIZEOF_INT);
				ib = tokensFileInMem.asIntBuffer();
			} else {
				// File memory-mapped
				tokensFileMapped.position((int) e.offset * SIZEOF_INT);
				ib = tokensFileMapped.asIntBuffer();
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
				String[] snippet = new String[snippetLength];
				ib.position(start[i]);
				for (int j = 0; j < snippetLength; j++) {
					snippet[j] = terms.get(ib.get());
				}
				result.add(snippet);
			}

			return result;
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * Unmapped version of retrieveParts (Windows has issues with memory mapping in Java)
	 */
	private synchronized List<String[]> retrievePartsUnmapped(int contentId, int[] start, int[] end) {
		try {
			TocEntry e = toc.get(contentId);
			if (e == null || e.deleted)
				return null;

			int n = start.length;
			if (n != end.length)
				throw new RuntimeException("start and end must be of equal length");
			List<String[]> result = new ArrayList<String[]>(n);

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
				String[] snippet = new String[snippetLength];
				long offset = e.offset + start[i];

				int bytesToRead = snippetLength * SIZEOF_INT;
				ByteBuffer buffer = ByteBuffer.allocate(bytesToRead);
				int bytesRead = tokensFileChannel.read(buffer, offset * SIZEOF_INT);
				if (bytesRead < bytesToRead) {
					throw new RuntimeException("Not enough bytes read: " + bytesRead + " < "
							+ bytesToRead);
				}
				buffer.position(0);
				IntBuffer ib = buffer.asIntBuffer();
				for (int j = 0; j < snippetLength; j++) {
					snippet[j] = terms.get(ib.get());
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
