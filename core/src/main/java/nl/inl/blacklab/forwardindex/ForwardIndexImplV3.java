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
import java.nio.LongBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.text.Collator;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.lucene.index.IndexReader;

import nl.inl.util.ExUtil;

/**
 * Keeps a forward index of documents, to quickly answer the question
 * "what word occurs in doc X at position Y"?
 */
class ForwardIndexImplV3 extends ForwardIndex {

	protected static final Logger logger = Logger.getLogger(ForwardIndexImplV3.class);

	/** The number of cached fiids we check to see if this field is set anywhere. */
	static final int NUMBER_OF_CACHE_ENTRIES_TO_CHECK = 1000;

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
	 * Use memory mapping to access the file.
	 * Turn this off for testing.
	 */
	static boolean useMemoryMapping = true;

	/** Size of a long in bytes. */
	private static final int SIZEOF_LONG = Long.SIZE / Byte.SIZE;

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

	/** How we look up forward index id in the index. */
	private FiidLookup fiidLookup;

	/** Are we in index mode (i.e. writing to forward index) or not? */
	private boolean indexMode;

	/** If true, we use the new, block-based terms file, that can grow larger than 2 GB. */
	private boolean useBlockBasedTermsFile = true;

	@Override
	public void setIdTranslateInfo(IndexReader reader, String lucenePropFieldName) {
		fiidLookup = new FiidLookup(reader, lucenePropFieldName);
	}

	@Override
	public int luceneDocIdToFiid(int docId) {
		return (int)fiidLookup.get(docId);
	}

	ForwardIndexImplV3(File dir, boolean indexMode, Collator collator, boolean create, boolean largeTermsFileSupport) {
		if (!dir.exists()) {
			if (!create)
				throw new IllegalArgumentException("ForwardIndex doesn't exist: " + dir);
			dir.mkdir();
		}

		this.indexMode = indexMode;

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
		toc = new ArrayList<>();
		deletedTocEntries = new ArrayList<>();
		try {
			setLargeTermsFileSupport(largeTermsFileSupport);
			boolean existing = false;
			if (tocFile.exists()) {
				readToc();
				terms = new TermsImplV3(indexMode, collator, termsFile, useBlockBasedTermsFile);
				existing = true;
				tocModified = false;
			} else {
				terms = new TermsImplV3(indexMode, collator, null, true);
				tokensFile.createNewFile();
				tokensFileChunks = null;
				tocModified = true;
				terms.setBlockBasedFile(useBlockBasedTermsFile);
			}
			openTokensFile();

			// Tricks to speed up reading
			if (existing && !create) {
				if (!indexMode && useMemoryMapping) {

					// Memory-map the file
					// NOTE: We only use this in search mode right now.
					// @@@ add support for mapped write? (need to re-map as the file grows in size)
					memoryMapTokensFile(false);

				} else {
					// Don't use memory mapping. Just read from file channel.
				}
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		if (create) {
			clear();
		}
	}

	private void openTokensFile() throws FileNotFoundException {
		tokensFp = new RandomAccessFile(tokensFile, indexMode ? "rw" : "r");
		tokensFileChannel = tokensFp.getChannel();
	}

	private void memoryMapTokensFile(boolean keepInMemory) throws IOException {

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
			for (TocEntry e: toc) {
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

			ByteBuffer mapping;
			if (keepInMemory) {
				mapping = ByteBuffer.allocate((int) sizeBytes);
				tokensFileChannel.position(startOfNextMappingBytes);
				int bytesRead = tokensFileChannel.read(mapping);
				if (bytesRead != mapping.capacity()) {
					throw new RuntimeException("Could not read tokens file chunk into memory!");
				}
			} else {
				mapping = tokensFileChannel.map(FileChannel.MapMode.READ_ONLY,
						startOfNextMappingBytes, sizeBytes);
			}
			tokensFileChunks.add(mapping);
			tokensFileChunkOffsetBytes.add(startOfNextMappingBytes);
			mappedBytes = startOfNextMappingBytes + sizeBytes;
		}
	}

	/**
	 * Delete all content in the forward index
	 */
	private void clear() {
		if (!indexMode)
			throw new RuntimeException("Cannot clear, not in index mode");

		// delete data files and empty TOC
		try {
			if (tokensFp == null)
				openTokensFile();
			tokensFp.setLength(0);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		termsFile.delete();
		tocFile.delete();
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
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
		toc.trimToSize();
		deletedTocEntries.trimToSize();
	}

	private void sortDeletedTocEntries() {
		Collections.sort(deletedTocEntries, new Comparator<TocEntry>() {
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
			throw new RuntimeException("Cannot write ToC, not in index mode");

		try {
			int n = toc.size();
			long[] offset = new long[n];
			int[] length = new int[n];
			byte[] deleted = new byte[n];
			int i = 0;
			for (TocEntry e: toc) {
				offset[i] = e.offset;
				length[i] = e.length;
				deleted[i] = (byte) (e.deleted ? 1 : 0);
				i++;
			}
			try (RandomAccessFile raf = new RandomAccessFile(tocFile, "rw")) {
				try (FileChannel fc = raf.getChannel()) {
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

			if (tokensFileChannel != null) {
				// Cannot truncate if still mapped; cannot force demapping.
				//tokensFileChannel.truncate(tokenFileEndPosition * SIZEOF_INT);
				tokensFileChannel.close();
			}

			if (tokensFp != null)
				tokensFp.close();

		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * Find the best-fitting deleted entry for the specified length
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
			throw new RuntimeException("Cannot add document, not in index mode");

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
		}
		else {
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
			if (writeBuffer == null || writeBufOffset > newDocumentOffset || writeBufEnd < newDocumentOffset + numberOfTokens) {
				// No, remap it
				writeBufOffset = newDocumentOffset;
				MappedByteBuffer byteBuffer = tokensFileChannel.map(FileChannel.MapMode.READ_WRITE,
						writeBufOffset * SIZEOF_INT, (numberOfTokens + mapReserve)
								* SIZEOF_INT);
				writeBuffer = byteBuffer.asIntBuffer();
			}

			// Set the correct start position
			writeBuffer.position((int)(newDocumentOffset - writeBufOffset));

			// Did we increase the length of the tokens file?
			long end = newDocumentOffset + numberOfTokens;
			if (end > tokenFileEndPosition)
				tokenFileEndPosition = end;

			// Write the token ids
			// (first fill the buffer, then write the buffer in 1 call)
			int [] tokenIds = new int[numberOfTokens];
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
				throw new RuntimeException("tokenIdsIndex != numberOfTokens (" + tokenIdsIndex + " != " + numberOfTokens + ")");
			writeBuffer.put(tokenIds);

			return newDocumentFiid;
		} catch (IOException e1) {
			throw new RuntimeException(e1);
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
						if (offsetBytes <= entryOffsetBytes + start[i] * SIZEOF_INT
								&& offsetBytes + buffer.capacity() >= entryOffsetBytes + end[i]
										* SIZEOF_INT) {
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
						// The file is mem-mapped.
						// Position us at the correct place in the file.
						ib.position(start[i]);
					} else {
						// Not mapped. Explicitly read the part we require from disk into an int
						// buffer.
						long offset = e.offset + start[i];

						int bytesToRead = snippetLength * SIZEOF_INT;
						ByteBuffer buffer = ByteBuffer.allocate(bytesToRead);
						int bytesRead = tokensFileChannel.read(buffer, offset * SIZEOF_INT);
						if (bytesRead < bytesToRead) {
							throw new RuntimeException("Not enough bytes read: " + bytesRead
									+ " < " + bytesToRead);
						}
						buffer.position(0);
						ib = buffer.asIntBuffer();
					}
					ib.get(snippet);
				}
				result.add(snippet);
			}

			return result;
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
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
		if (!indexMode)
			throw new RuntimeException("Cannot delete document, not in index mode");
		TocEntry tocEntry = toc.get(fiid);
		tocEntry.deleted = true;
		deletedTocEntries.add(tocEntry); // NOTE: mergeAdjacentDeletedEntries takes care of re-sorting
		mergeAdjacentDeletedEntries();
		tocModified = true;
	}

	/**
	 * Check if we can merge two (or more) deleted entries to create a
	 * larger gap, and do so.
	 *
	 * Also takes care of truncating the file if there are deleted entries
	 * at the end.
	 */
	private void mergeAdjacentDeletedEntries() {
		// Sort by offset, so we can find adjacent entries
		Collections.sort(deletedTocEntries);

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
	public long getFreeSpace() {
		long freeSpace = 0;
		for (TocEntry e: deletedTocEntries) {
			freeSpace += e.length;
		}
		return freeSpace;
	}

	@Override
	public int getFreeBlocks() {
		return deletedTocEntries.size();
	}

	@Override
	public long getTotalSize() {
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
				return !toc.get((Integer)o).deleted;
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
