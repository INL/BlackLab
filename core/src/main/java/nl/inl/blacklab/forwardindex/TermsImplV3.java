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
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.impl.factory.Maps;

/**
 * Keeps a first-come-first-serve list of unique terms.
 * Each term gets a unique index number. These numbers are
 * stored in the forward index to conserve space and allow quick
 * lookups of terms occurring in specific positions.
 *
 * This version of the class stores the terms in a more efficient way so it
 * saves and loads faster, and includes the case-insensitive sorting order.
 */
class TermsImplV3 extends Terms {
	private static final int APPROX_MAX_ARRAY_SIZE = Integer.MAX_VALUE - 100;

	/** Number of sort buffers we store in the terms file (case-sensitive/insensitive and inverted buffers for both as well) */
	private static final int NUM_SORT_BUFFERS = 4;

	/** Number of bytes per int */
	private static final int BYTES_PER_INT = Integer.SIZE / Byte.SIZE;

	protected static final Logger logger = LogManager.getLogger(TermsImplV3.class);

	/** A mapped block from the terms file */
	static class TermStringBlock {

		/** The mapped block */
		private ByteBuffer buffer;

		/** Number of term strings in this block */
		private int numberOfTerms;

		/** Offsets of the term strings in this block */
		private int[] offsets;

		public TermStringBlock(ByteBuffer buffer, int numberOfTerms, int[] offsets) {
			super();
			this.buffer = buffer;
			this.numberOfTerms = numberOfTerms;
			this.offsets = offsets;
		}

		/** Get a term string from this block
		 * @param i term string index in this block
		 * @return corresponding term string
		 */
		public String get(int i) {
			int termOffset = offsets[i];
			int termLength = offsets[i + 1] - termOffset;
			buffer.position(termOffset);
			byte[] termBytes = new byte[termLength];
			buffer.get(termBytes);
			return new String(termBytes, DEFAULT_CHARSET);
		}

		/** Get the number of terms in this block.
		 *  @return number of terms in this block.
		 */
		public int numberOfTerms() {
			return numberOfTerms;
		}

	}

	/** Mapped blocks from the terms file */
	List<TermStringBlock> termStringBlocks;

	/** How many terms total are there? (always valid) */
	int numberOfTerms = 0;

	/** The index number of each sorting position. Inverse of sortPositionPerId[] array.
	 *  Only valid when indexMode == false. */
	int[] idPerSortPosition;

	/** The sorting position for each index number. Inverse of idPerSortPosition[]
	 *  array. Only valid when indexMode == false. */
	int[] sortPositionPerId;

	/** The case-insensitive sorting position for each index number.
	 *  Only valid when indexMode == false. */
	int[] sortPositionPerIdInsensitive;

	/**
	 * Mapping from term to its unique index number. We use a SortedMap because we wish to
	 * store the sorted index numbers later (to speed up sorting). Only valid in indexMode.
	 */
	Map<String, Integer> termIndex;

	/** If true, we're indexing data and adding terms. If false, we're searching and just retrieving terms. */
	private boolean indexMode;

	/** If true, termIndex is a valid mapping from term to term id. */
	private boolean termIndexBuilt;

	/**
	 * Collator to use for string comparisons
	 */
	final Collator collator;

	/**
	 * Collator to use for insensitive string comparisons
	 */
	Collator collatorInsensitive;

	/** Use new blocks-based terms file, that can grow larger than 2 GB? */
	private boolean useBlockBasedTermsFile = true;

	/** The maximum block size to use while writing the terms file.
	 *  Ususally around the limit of 2GB, but for testing, we can set this to
	 *  a lower value. */
	private int maxBlockSize = APPROX_MAX_ARRAY_SIZE;

	TermsImplV3(boolean indexMode, Collator collator, File termsFile, boolean useBlockBasedTermsFile) {
		this.indexMode = indexMode;
		if (collator == null)
			collator = Collator.getInstance();
		this.collator = (Collator)collator.clone();
		this.collator.setStrength(Collator.TERTIARY);
		this.collatorInsensitive = (Collator)collator.clone();
		collatorInsensitive.setStrength(Collator.PRIMARY);

		if (indexMode) {
			// Index mode: create a SortedMap based on the specified Collator.
			// (used later to get the terms in sort order)
			this.termIndex = new TreeMap<>(this.collator);
		} else {
			// We already have the sort order, so TreeMap is not necessary here.
			this.termIndex = Maps.mutable.empty();
		}
		termIndexBuilt = true;
		setBlockBasedFile(useBlockBasedTermsFile);
		if (termsFile != null && termsFile.exists())
			read(termsFile);
	}

	@Override
	public int indexOf(String term) {

		// Do we have the term index available (fastest method)?
		if (!termIndexBuilt) {

			// No. (this means we are in search mode, because in
			//      index mode the term index is always available)
			// Do a binary search to find term.
			// Note that the binary search is done on the sorted terms,
			// so we need to guess an ordinal, convert it to a term index,
			// then check the term string, and repeat until we find a match.
			int min = 0, max = idPerSortPosition.length - 1;
			while (true) {
				int guessedOrdinal = (min + max) / 2;
				int guessedIndex = idPerSortPosition[guessedOrdinal];
				String guessedTerm = get(guessedIndex);
				int cmp = collator.compare(term, guessedTerm);
				if (cmp == 0)
					return guessedIndex; // found
				if (cmp < 0)
					max = guessedOrdinal - 1;
				else
					min = guessedOrdinal + 1;
				if (max < min)
					return NO_TERM; // not found
			}
		}

		// Use the available term index.
		Integer index = termIndex.get(term);
		if (index != null)
			return index;
		if (!indexMode)
			return NO_TERM; // term not found
		index = termIndex.size();
		termIndex.put(term, index);
		return index;
	}

	@Override
	public synchronized void buildTermIndex() {
		if (termIndexBuilt)
			return;
		for (int i = 0; i < numberOfTerms(); i++) {
			termIndex.put(get(i), i);
		}
		termIndexBuilt = true;
	}

	@Override
	public void clear() {
		if (!indexMode)
			throw new RuntimeException("Cannot clear, not in index mode");
		termIndex.clear();
		termIndexBuilt = true;
	}

	private void read(File termsFile) {
		termIndex.clear();
		try {
			try (RandomAccessFile termsFileFp = new RandomAccessFile(termsFile, "r")) {
				try (FileChannel termsFileChannel = termsFileFp.getChannel()) {

					long fileLength = termsFile.length();
					long fileMapStart = 0;
					long fileMapLength = Math.min(Integer.MAX_VALUE, fileLength);
					MappedByteBuffer buf = termsFileChannel.map(MapMode.READ_ONLY, fileMapStart, fileMapLength);
					int n = buf.getInt();
					fileMapStart += BYTES_PER_INT;
					IntBuffer ib = buf.asIntBuffer();
					numberOfTerms = n;

					termStringBlocks = new ArrayList<>();
					if (useBlockBasedTermsFile) {
						// New format, multiple blocks of term strings if necessary,
						// so term strings may total over 2 GB.

						// Read the term string offsets and string data block
						int currentTerm = 0;
						while (currentTerm < n) {

							int numTermsThisBlock = ib.get();
							currentTerm += numTermsThisBlock;
							int[] termStringOffsets = new int[numTermsThisBlock + 1];
							ib.get(termStringOffsets, 0, numTermsThisBlock); // term string offsets

							// Read term strings data
							int termStringsByteSize = termStringOffsets[numTermsThisBlock] = ib.get();
							fileMapStart += (numTermsThisBlock + 2) * BYTES_PER_INT;
							ByteBuffer termStringBuf = termsFileChannel.map(MapMode.READ_ONLY, fileMapStart, termStringsByteSize);
							termStringBlocks.add(new TermStringBlock(termStringBuf, numTermsThisBlock, termStringOffsets));

							// Re-map a new part of the file before we read the next block.
							// (and before we read the sort buffers)
							fileMapStart += termStringsByteSize;
							fileMapLength = Math.min(Integer.MAX_VALUE, fileLength - fileMapStart);
							if (fileMapLength > 0) {
								buf = termsFileChannel.map(MapMode.READ_ONLY, fileMapStart, fileMapLength);
								ib = buf.asIntBuffer();
							}
						}

					} else {
						// Old format, single term strings block.
						// Causes problems when term strings total over 2 GB.

						int numTermsThisBlock = n;
						int[] termStringOffsets = new int[numTermsThisBlock + 1];
						ib.get(termStringOffsets); // term string offsets

						// Read term strings data
						int termStringsByteSize = ib.get(); // data block size
						fileMapStart += (numTermsThisBlock + 2) * BYTES_PER_INT;
						ByteBuffer termStringBuf = termsFileChannel.map(MapMode.READ_ONLY, fileMapStart, termStringsByteSize);
						termStringBlocks.add(new TermStringBlock(termStringBuf, numTermsThisBlock, termStringOffsets));

						fileMapStart += termStringsByteSize;
						buf = termsFileChannel.map(MapMode.READ_ONLY, fileMapStart, fileLength - fileMapStart);
						ib = buf.asIntBuffer();
					}

					if (indexMode) {
						termIndexBuilt = false;
						buildTermIndex(); // We need to find id for term quickly while indexing
						//terms = null; // useless in index mode because we can't add to it, and we don't need it anyway
					} else {
						termIndexBuilt = false; // termIndex hasn't been filled yet

						// Read the sort order arrays
						sortPositionPerId = new int[n];
						sortPositionPerIdInsensitive = new int[n];
						ib.position(ib.position() + n); // Advance past unused sortPos -> id array (left in there for file compatibility)
						ib.get(sortPositionPerId);
						ib.position(ib.position() + n); // Advance past unused sortPos -> id array (left in there for file compatibility)
						ib.get(sortPositionPerIdInsensitive);

						// Invert sortPositionPerId[] array, so we can later do a binary search through our
						// terms to find a specific one. (only needed to deserialize sort/group criteria from URL)
						idPerSortPosition = new int[n];
						for (int i = 0; i < n; i++) {
							idPerSortPosition[sortPositionPerId[i]] = i;
						}
					}
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String get(Integer index) {
		assert index >= 0 && index < numberOfTerms : "Term index out of range (" + index + ", numterms = " + numberOfTerms + ")";

		// See what term strings block this term is in
		int blockStart = 0;
		for (TermStringBlock bl: termStringBlocks) {
			if (index < blockStart + bl.numberOfTerms)
				return bl.get(index - blockStart);
			blockStart += bl.numberOfTerms();
		}
		throw new RuntimeException("Term index out of range, not caught before!");
	}

	@Override
	public void write(File termsFile) {
		if (!indexMode)
			throw new RuntimeException("Term.write(): not in index mode!");

		try {
			// Open the terms file
			try (RandomAccessFile raf = new RandomAccessFile(termsFile, "rw")) {
				try (FileChannel fc = raf.getChannel()) {
					int n = termIndex.size();

					// Fill the terms[] array
					final String[] terms = new String[n];
					long termStringsByteSize = 0;
					for (Map.Entry<String, Integer> entry: termIndex.entrySet()) {
						terms[entry.getValue()] = entry.getKey();
						termStringsByteSize += entry.getKey().getBytes(DEFAULT_CHARSET).length;
					}

					// Calculate the file length and map the file
					MappedByteBuffer buf;
					IntBuffer ib;
					if (!useBlockBasedTermsFile) {
						long fileLength = 2 * BYTES_PER_INT + (n + 1) * BYTES_PER_INT + termStringsByteSize + NUM_SORT_BUFFERS * BYTES_PER_INT * n;
						fc.truncate(fileLength); // truncate if necessary
						buf = fc.map(MapMode.READ_WRITE, (long) 0, fileLength);
						buf.putInt(n); // Start with the number of terms
						ib = buf.asIntBuffer();

						// Terms file is small enough to fit in a single byte array.
						// Use the old code.

						// Calculate byte offsets for all the terms and fill data array
						int currentOffset = 0;
						int[] termStringOffsets = new int[n + 1];
						byte[] termStrings = new byte[(int)termStringsByteSize];
						for (int i = 0; i < n; i++) {
							termStringOffsets[i] = currentOffset;
							byte[] termBytes = terms[i].getBytes(DEFAULT_CHARSET);
							System.arraycopy(termBytes, 0, termStrings, currentOffset, termBytes.length);
							currentOffset += termBytes.length;
						}
						termStringOffsets[n] = currentOffset;

						// Write offset and data arrays to file
						ib.put(termStringOffsets);
						ib.put((int)termStringsByteSize); // size of the data block to follow
						buf.position(buf.position() + BYTES_PER_INT + BYTES_PER_INT * termStringOffsets.length); // advance past offsets array
						buf.put(termStrings);
						ib = buf.asIntBuffer();
					} else {
						long fileMapStart = 0, fileMapLength = Integer.MAX_VALUE;
						buf = fc.map(MapMode.READ_WRITE, fileMapStart, fileMapLength);
						buf.putInt(n); // Start with the number of terms      //@4
						ib = buf.asIntBuffer();
						long fileLength = BYTES_PER_INT;

						// Terms file is too large to fit in a single byte array.
						// Use the new code.
						int currentTerm = 0;
						long bytesLeftToWrite = termStringsByteSize;
						int[] termStringOffsets = new int[n];
						while (currentTerm < n) {
							int firstTermInBlock = currentTerm;
							int blockSize = (int)Math.min(bytesLeftToWrite, maxBlockSize);

							// Calculate byte offsets for all the terms and fill data array
							int currentOffset = 0;
							byte[] termStrings = new byte[blockSize];
							while (currentTerm < n) {
								termStringOffsets[currentTerm] = currentOffset;
								byte[] termBytes = terms[currentTerm].getBytes(DEFAULT_CHARSET);
								if (currentOffset + termBytes.length > blockSize) {
									// Block is full. Write it and continue with next block.
									break;
								}
								System.arraycopy(termBytes, 0, termStrings, currentOffset, termBytes.length);
								currentOffset += termBytes.length;
								currentTerm++;
								bytesLeftToWrite -= termBytes.length;
							}

							// Write offset and data arrays to file
							int numTermsThisBlock = currentTerm - firstTermInBlock;

							long blockSizeBytes = 2 * BYTES_PER_INT + numTermsThisBlock * BYTES_PER_INT + currentOffset;

							ib.put(numTermsThisBlock); //@4
							ib.put(termStringOffsets, firstTermInBlock, numTermsThisBlock); //@4 * numTermsThisBlock
							ib.put(currentOffset); // include the offset after the last term at position termStringOffsets[n]
							                       // (doubles as the size of the data block to follow) //@4
							int newPosition = buf.position() + BYTES_PER_INT * (2 + numTermsThisBlock);
							buf.position(newPosition); // advance past offsets array
							buf.put(termStrings, 0, currentOffset); //@blockSize (max. maxBlockSize)
							ib = buf.asIntBuffer();
							fileLength += blockSizeBytes;

							// Re-map a new part of the file before we write the next block.
							// (and eventually, the sort buffers, see below)
							fileMapStart += buf.position();
							buf = fc.map(MapMode.READ_WRITE, fileMapStart, fileMapLength);
							ib = buf.asIntBuffer();
						}

						// Determine total file length (by adding the sort buffer byte length to the
						// running total) and truncate the file if necessary
						// (we can do this now, even though we still have to write the sort buffers,
						// because we know how large the file will eventually be)
						fileLength += NUM_SORT_BUFFERS * BYTES_PER_INT * n;
						fc.truncate(fileLength);
					}

					// Write the case-sensitive sort order
					// Because termIndex is a SortedMap, values are returned in key-sorted order.
					// In other words, the index numbers are in order of sorted terms, so the id
					// for 'aardvark' comes before the id for 'ape', etc.
					int i = 0;
					sortPositionPerId = new int[n];
					Integer[] insensitive = new Integer[n];
					for (int id: termIndex.values()) {
						sortPositionPerId[id] = i;
						insensitive[i] = id; // fill this so we can re-sort later, faster b/c already partially sorted
						i++;
					}
					ib.put(new int[n]); // NOT USED ANYMORE, JUST FOR FILE COMPATIBILITY
					ib.put(sortPositionPerId);

					// Now, sort case-insensitively and write those arrays as well
					Arrays.sort(insensitive, new Comparator<Integer>() {
						@Override
						public int compare(Integer a, Integer b) {
							return collatorInsensitive.compare(terms[a], terms[b]);
						}
					});
					// Copy into the sortPositionPerIdInsensitive array, making sure that
					// identical values get identical sort positions!
					sortPositionPerIdInsensitive = new int[n];
					int sortPos = 0;
					for (i = 0; i < n; i++) {
						if (i == 0 || collatorInsensitive.compare(terms[insensitive[i - 1]], terms[insensitive[i]]) != 0) {
							// Not identical to previous value: gets its own sort position.
							// If a value is identical to the previous one, it gets the same sort position.
							sortPos = i;
						}
						sortPositionPerIdInsensitive[insensitive[i]] = sortPos;
					}
					ib.put(new int[n]); // NOT USED ANYMORE, JUST FOR FILE COMPATIBILITY
					ib.put(sortPositionPerIdInsensitive);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int numberOfTerms() {
		return numberOfTerms;
	}

	@Override
	public void toSortOrder(int[] tokenId, int[] sortOrder, boolean sensitive) {
		if (sensitive) {
			for (int i = 0; i < tokenId.length; i++) {
				if (tokenId[i] == NO_TERM)
					sortOrder[i] = NO_TERM;
				else
					sortOrder[i] = sortPositionPerId[tokenId[i]];
			}
		} else {
			for (int i = 0; i < tokenId.length; i++) {
				if (tokenId[i] == NO_TERM)
					sortOrder[i] = NO_TERM;
				else
					sortOrder[i] = sortPositionPerIdInsensitive[tokenId[i]];
			}
		}
	}

	@Override
	public int compareSortPosition(int tokenId1, int tokenId2, boolean sensitive) {
		if (sensitive) {
			return sortPositionPerId[tokenId1] - sortPositionPerId[tokenId2];
		}
		return sortPositionPerIdInsensitive[tokenId1] - sortPositionPerIdInsensitive[tokenId2];
	}

	@Override
	public int idToSortPosition(int id, boolean sensitive) {
		return sensitive ? sortPositionPerId[id] : sortPositionPerIdInsensitive[id];
	}

	@Override
	protected void setBlockBasedFile(boolean useBlockBasedTermsFile) {
		this.useBlockBasedTermsFile = useBlockBasedTermsFile;
	}

	public void setMaxBlockSize(int maxBlockSize) {
		this.maxBlockSize = maxBlockSize;
	}

}
