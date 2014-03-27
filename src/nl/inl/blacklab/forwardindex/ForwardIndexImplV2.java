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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.util.ExUtil;
import nl.inl.util.MemoryUtil;
import nl.inl.util.VersionFile;

import org.apache.log4j.Logger;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.search.FieldCache;

/**
 * Keeps a forward index of documents, to quickly answer the question
 * "what word occurs in doc X at position Y"?
 *
 * NOTE: version 2 will be phased out soon. Re-index your data if you're
 * still using it!
 *
 * @deprecated re-index your data if you're still on v2.
 */
@Deprecated
class ForwardIndexImplV2 extends ForwardIndex {

	protected static final Logger logger = Logger.getLogger(ForwardIndexImplV2.class);

	/**
	 * If true, we want to disable actual I/O and assign random term id's instead.
	 * (used to test the impact of I/O on sorting/grouping)
	 */
	private static final boolean TESTING_IO_IMPACT = false;

	/** Java has as limit of 2GB for MappedByteBuffer.
	 *  But this could be worked around using arrays of MappedByteBuffers, see:
	 *  http://stackoverflow.com/questions/5675748/java-memorymapping-big-files
	 */
	private static final int MAX_DIRECT_BUFFER_SIZE = Integer.MAX_VALUE;

	/** Desired chunk size. Usually just MAX_DIRECT_BUFFER_SIZE, but can be
	 *  set to be smaller (for easier testing).
	 *
	 *  NOTE: using MAX_DIRECT_BUFFER_SIZE (2GB) failed on Linux 64 bit, so
	 *  we're using 1GB for now.
	 */
	static int preferredChunkSizeBytes = MAX_DIRECT_BUFFER_SIZE / 2;

	/**
	 * Try to keep the whole file in memory, if there's enough free memory available.
	 * Turn this off to use memory-mapping (leaving caching to the OS).
	 * NOTE: leaving it to the OS is probably a better idea, as it prevents double copies of
	 * the file and makes more physical memory available for smart, efficient OS-caching.
	 */
	static boolean keepInMemoryIfPossible = false;

	/**
	 * When deciding whether or not to keep forward indices in memory, this is how much
	 * heap memory we should keep free for other purposes. By default, keep 2.5G free.
	 * NOTE: keeping the forward index in memory is probably a bad idea as it costs a lot of
	 * physical memory and makes OS caching superfluous. Just reduce your application's
	 * memory requirements and rely on the OS caching via memory mapping instead.
	 */
	private static long keepMemoryFree = 2500000;

	/**
	 * Use memory mapping to access the file.
	 * Turn this off for testing.
	 */
	static boolean useMemoryMapping = true;

	/** Size of an int in bytes. This will always be 4, according to the standard. */
	private static final int SIZEOF_INT = Integer.SIZE / Byte.SIZE;

	/** The number of integer positions to reserve when mapping the file for writing. */
	final static int WRITE_MAP_RESERVE = 250000; // 250K integers = 1M bytes

	/** The memory mapped write buffer */
	private IntBuffer writeBuffer;

	/** Buffer offset (position in file of start of writeBuffer) in integer positions
	 * (so we don't count bytes, we count ints) */
	private long writeBufOffset;

	/** The table of contents (where documents start in the tokens file and how long they are) */
	private List<TocEntry> toc;

	/** The table of contents (TOC) file, docs.dat */
	private File tocFile;

	/** The tokens file (stores indexes into terms.dat) */
	private File tokensFile;

	/** The terms file (stores unique terms) */
	private File termsFile;

	/** The unique terms in our index */
	private Terms terms;

	/** Handle for the tokens file */
	private RandomAccessFile tokensFp;

	/** Mapping into the tokens file */
	private List<ByteBuffer> tokensFileChunks = null;

	/** Offsets of the mappings into the token file */
	private List<Long> tokensFileChunkOffsetBytes = null;

	/** File channel for the tokens file */
	private FileChannel tokensFileChannel;

	/** Has the table of contents been modified? */
	private boolean tocModified = false;

	/** The position (in ints) in the tokens file after the last token written. Note that
	 *  the actual file may be larger because we reserve space at the end. */
	private long tokenFileEndPosition = 0;

	/** Index reader, for getting documents (for translating from Lucene doc id to fiid) */
	private DirectoryReader reader;

	/** fiid field name in the Lucene index (for translating from Lucene doc id to fiid) */
	private String fiidFieldName;

	/** Cached fiid field */
	private FieldCache.Ints cachedFiids;

	@Override
	public void setIdTranslateInfo(DirectoryReader reader, String lucenePropFieldName) {
		this.reader = reader;
		this.fiidFieldName = ComplexFieldUtil.forwardIndexIdField(lucenePropFieldName);
		try {
			SlowCompositeReaderWrapper srw = new SlowCompositeReaderWrapper(reader);
			cachedFiids = FieldCache.DEFAULT.getInts(srw, fiidFieldName, true);

			// Check if the cache was retrieved OK
			boolean allZeroes = true;
			for (int i = 0; i < ForwardIndexImplV3.NUMBER_OF_CACHE_ENTRIES_TO_CHECK; i++) {
				if (cachedFiids.get(i) != 0) {
					allZeroes = false;
					break;
				}
			}
			if (allZeroes) {
				// Tokens lengths weren't saved in the index, skip cache
				cachedFiids = null;
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int luceneDocIdToFiid(int docId) {
		if (cachedFiids != null)
			return cachedFiids.get(docId);

		// Not cached; find fiid by reading stored value from Document now
		try {
			return Integer.parseInt(reader.document(docId).get(fiidFieldName));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public ForwardIndexImplV2(File dir, boolean indexMode, Collator collator, boolean create) {
		if (!dir.exists()) {
			if (!create)
				throw new RuntimeException("ForwardIndex doesn't exist: " + dir);
			dir.mkdir();
		}

		// Version check
		if (!indexMode || !create) {
			// We're opening an existing forward index. Check version.
			if (!VersionFile.isTypeVersion(dir, "fi", "2")) {
				throw new RuntimeException("Not a forward index or wrong version: "
						+ VersionFile.report(dir) + " (fi 2 expected)");
			}
		} else {
			// We're creating a forward index. Write version.
			VersionFile.write(dir, "fi", "2");
		}

		termsFile = new File(dir, "terms.dat");
		tocFile = new File(dir, "docs.dat");
		tokensFile = new File(dir, "tokens.dat");
		if (create) {
			if (tokensFile.exists())
				tokensFile.delete();
			if (tocFile.exists())
				tocFile.delete();
			if (termsFile.exists())
				termsFile.delete();
		}
		toc = new ArrayList<TocEntry>();
		try {
			boolean existing = false;
			if (tocFile.exists()) {
				readToc();
				terms = new TermsImplV2(indexMode, collator, termsFile);
				existing = true;
				tocModified = false;
			} else {
				terms = new TermsImplV2(indexMode, collator);
				tokensFile.createNewFile();
				tokensFileChunks = null;
				tocModified = true;
			}
			openTokensFile(indexMode);

			// Tricks to speed up reading
			if (existing && !create /*&& !OsUtil.isWindows()*/) { // Memory mapping has issues on
																// Windows
				long free = MemoryUtil.getFree();

				if (!indexMode && keepInMemoryIfPossible && free - keepMemoryFree >= tokensFile.length()) {
					// Enough free memory; cache whole file
					// NOTE: we can't add to the file this way, so we only use this in search mode
					memoryMapTokensFile(true);

					// We don't need the file handle anymore; close it to free up any cached resources.
					tokensFp.close();
					tokensFp = null;
					tokensFileChannel = null;

				} else if (!indexMode && useMemoryMapping) {

					// Memory-map the file (sometimes fails on Windows with large files..? Increase
					// direct buffer size with cmdline option?)
					// NOTE: we can't add to the file this way! We only use this in search mode.
					// @@@ add support for mapped write (need to re-map as the file grows in size)
					memoryMapTokensFile(false);

				} else {
					// Don't cache whole file in memory, don't use memory mapping. Just read from
					// file channel.
				}
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		if (create) {
			clear();
		}
	}

	private void openTokensFile(boolean indexMode) throws FileNotFoundException {
		tokensFp = new RandomAccessFile(tokensFile, indexMode ? "rw" : "r");
		tokensFileChannel = tokensFp.getChannel();
	}

	private void memoryMapTokensFile(boolean keepInMemory) throws IOException {

		// Map the tokens file in chunks of 2GB each. When retrieving documents, we always
		// read it from just one chunk, not multiple, but because each chunk begins at a
		// document start, documents of up to 2G tokens can be processed. We could get around
		// this limitation by reading from multiple chunks, but this would make the code
		// more complex.
		tokensFileChunks = new ArrayList<ByteBuffer>();
		tokensFileChunkOffsetBytes = new ArrayList<Long>();
		long mappedBytes = 0;
		long tokenFileEndBytes = getTokenFileEndPosition() * SIZEOF_INT;
		Collections.sort(toc);
		while (mappedBytes < tokenFileEndBytes) {
			// Find the last TOC entry start point that's also in the previous mapping
			// (or right the first byte after the previous mapping).
			long startOfNextMappingBytes = 0;

			// Look for the largest entryOffset that's smaller than mappedBytes.
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
			startOfNextMappingBytes = toc.get(min).offset * SIZEOF_INT;

			// Map this chunk
			long sizeBytes = tokenFileEndBytes - startOfNextMappingBytes;
			if (sizeBytes > preferredChunkSizeBytes)
				sizeBytes = preferredChunkSizeBytes;

			ByteBuffer mapping;
			if (keepInMemory) {
				mapping = ByteBuffer.allocate((int)sizeBytes);
				tokensFileChannel.position(startOfNextMappingBytes);
				int bytesRead = tokensFileChannel.read(mapping);
				if (bytesRead != mapping.capacity()) {
					throw new RuntimeException("Could not read tokens file chunk into memory!");
				}
			} else {
				mapping = tokensFileChannel.map(FileChannel.MapMode.READ_ONLY, startOfNextMappingBytes,
						sizeBytes);
			}
			tokensFileChunks.add(mapping);
			tokensFileChunkOffsetBytes.add(startOfNextMappingBytes);
			mappedBytes = startOfNextMappingBytes + sizeBytes;
		}
	}

	/**
	 * Returns the total number of tokens stored in the file
	 * @return the number of tokens
	 */
	private long getTokenFileEndPosition() {
		return tokenFileEndPosition;
	}

	/**
	 * Delete all content in the forward index
	 */
	private void clear() {
		// delete data files and empty TOC
		try {
			if (tokensFp == null)
				openTokensFile(true);
			tokensFp.setLength(0);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		termsFile.delete();
		tocFile.delete();
		toc.clear();
		tokenFileEndPosition = 0;
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

					long end = e.offset + e.length;
					if (end > tokenFileEndPosition)
						tokenFileEndPosition = end;
				}
			} finally {
				raf.close();
			}
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * Write the table of contents to the file
	 */
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

	@Override
	public void close() {
		try {
			if (tocModified) {
				writeToc();
				terms.write(termsFile);
			}

			if (tokensFileChannel != null)
				tokensFileChannel.close();

			if (tokensFp != null)
				tokensFp.close();

		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
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
			for (int inc: posIncr) {
				numberOfTokens += inc;
			}
		}

		try {
			if (writeBuffer == null) {
				// Map writeBuffer

				writeBufOffset = getTokenFileEndPosition();

				MappedByteBuffer byteBuffer = tokensFileChannel.map(FileChannel.MapMode.READ_WRITE,
						writeBufOffset * SIZEOF_INT, (numberOfTokens + WRITE_MAP_RESERVE) * SIZEOF_INT);
				writeBuffer = byteBuffer.asIntBuffer();
			}

			// Make entry
			long entryOffset = writeBufOffset + writeBuffer.position();
			TocEntry e = new TocEntry(entryOffset, numberOfTokens, false);
			toc.add(e);
			long end = e.offset + e.length;
			if (end > tokenFileEndPosition)
				tokenFileEndPosition = end;
			tocModified = true;

			if (writeBuffer.remaining() < numberOfTokens) {
				// Remap writeBuffer so we have space available
				writeBufOffset += writeBuffer.position();

				// NOTE: We reserve more space so we don't have to remap for each document, saving time
				MappedByteBuffer byteBuffer = tokensFileChannel.map(FileChannel.MapMode.READ_WRITE,
						writeBufOffset * SIZEOF_INT, (numberOfTokens + WRITE_MAP_RESERVE) * SIZEOF_INT);
				writeBuffer = byteBuffer.asIntBuffer();
			}

			Iterator<String> contentIt = content.iterator();
			Iterator<Integer> posIncrIt = posIncr == null ? null : posIncr.iterator();
			while (contentIt.hasNext()) {
				String token = contentIt.next();
				int pi = posIncrIt == null ? 1 : posIncrIt.next();
				if (pi == 0)
					continue; // we only store the first token at any position
				if (pi > 1) {
					// Skipped a few tokens; add empty tokens for these positions
					for (int i = 0; i < pi - 1; i++) {
						writeBuffer.put(terms.indexOf(""));
					}
				}

				writeBuffer.put(terms.indexOf(token));
			}

			return toc.size() - 1;
		} catch (IOException e1) {
			throw new RuntimeException(e1);
		}
	}

	@Override
	public synchronized int addDocument(List<String> content) {
		return addDocument(content, null);
	}

	@Deprecated
	@Override
	public synchronized List<String[]> retrieveParts(int fiid, int[] start, int[] end) {

		// First, retrieve the token ids
		List<int[]> resultInt = retrievePartsInt(fiid, start, end);

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

	@Deprecated
	@Override
	public synchronized List<int[]> retrievePartsSortOrder(int fiid, int[] start, int[] end, boolean sensitive) {

		// First, retrieve the token ids
		List<int[]> resultInt = retrievePartsInt(fiid, start, end);

		// Translate them to sort orders
		for (int[] snippetInt: resultInt) {
			terms.toSortOrder(snippetInt, snippetInt, sensitive);
		}
		return resultInt;
	}

	@Override
	public void warmUp() throws InterruptedException {
		int fiid = 0;
		int oneReadPerHowManyChars = 4000;
		for (TocEntry e: toc) {
			int n = e.length / oneReadPerHowManyChars;

			int[] starts = new int[n];
			int[] ends = new int[n];
			for (int i = 0; i < n; i++) {
				starts[i] = i * oneReadPerHowManyChars;
				ends[i] = starts[i] + 10;
			}
			retrievePartsInt(fiid, starts, ends);
			fiid++;
			if (fiid % 100 == 0) {
				// Allow a little bit of other processing to go on,
				// and check for thread interruption
				Thread.sleep(1);
			}
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
				throw new RuntimeException("start and end must be of equal length");
			List<int[]> result = new ArrayList<int[]>(n);

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

				// Get an IntBuffer to read the desired content
				IntBuffer ib = null;
				boolean inMem = false;
				if (tokensFileChunks != null) {
					// Yes, the tokens file has either been fully loaded into memory or
					// is mapped into memory. Get an int buffer into the file.
					inMem = true;

					// Figure out which chunk to access.
					ByteBuffer whichChunk = null;
					long chunkOffsetBytes = -1;
					long entryOffsetBytes = e.offset * SIZEOF_INT;
					for (int j = 0; j < tokensFileChunkOffsetBytes.size(); j++) {
						long offsetBytes = tokensFileChunkOffsetBytes.get(j);
						ByteBuffer buffer = tokensFileChunks.get(j);
						if (offsetBytes <= entryOffsetBytes + start[i] * SIZEOF_INT && offsetBytes + buffer.capacity() >= entryOffsetBytes + end[i] * SIZEOF_INT) {
							// This one!
							whichChunk = buffer;
							chunkOffsetBytes = offsetBytes;
							break;
						}
					}

					whichChunk.position((int) (e.offset * SIZEOF_INT - chunkOffsetBytes));
					ib = whichChunk.asIntBuffer();
				}

				int snippetLength = end[i] - start[i];
				int[] snippet = new int[snippetLength];
				if (TESTING_IO_IMPACT) {
					// We're testing how much impact forward index I/O has on sorting/grouping.
					// Fill the array with random token ids instead of reading them from the
					// file.
					int numberOfTerms = terms.numberOfTerms();
					for (int j = 0; j < snippetLength; j++) {
						int randomTermId = (int) Math.random() * numberOfTerms;
						snippet[j] = randomTermId;
					}
				} else {
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
				}
				result.add(snippet);
			}

			return result;
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	public static void main(String[] args) {
		ForwardIndex fi = new ForwardIndexImplV2(new File("E:\\temp"), true, null, false);
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

	@Override
	public Terms getTerms() {
		return terms;
	}

	@Override
	public int getNumDocs() {
		return toc.size();
	}

	@Override
	public int getDocLength(int fiid) {
		return toc.get(fiid).length;
	}

	@Override
	public void deleteDocument(int fiid) {
		throw new UnsupportedOperationException();
	}

	@Override
	public long getFreeSpace() {
		return 0;
	}

	@Override
	public int getFreeBlocks() {
		return 0;
	}

	@Override
	public long getTotalSize() {
		return tokenFileEndPosition;
	}

}
