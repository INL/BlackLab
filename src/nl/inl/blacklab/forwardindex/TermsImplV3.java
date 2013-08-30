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
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;

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
	/** Number of sort buffers we store in the terms file (case-sensitive/insensitive and inverted buffers for both as well) */
	private static final int NUM_SORT_BUFFERS = 4;

	/** Number of bytes per int */
	private static final int BYTES_PER_INT = Integer.SIZE / Byte.SIZE;

	protected static final Logger logger = Logger.getLogger(TermsImplV3.class);

	/** Search mode only: the terms, by index number. */
	String[] terms;

	/** The sorting position for each index number. Inverse of idPerSortPosition[]
	 *  array. Only valid when indexMode == false. */
	int[] sortPositionPerId;

	/** The case-insensitive sorting position for each index number. Inverse of idPerSortPosition[]
	 *  array. Only valid when indexMode == false. */
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

	public TermsImplV3(boolean indexMode, Collator collator) {
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
			this.termIndex = new TreeMap<String, Integer>(this.collator);
		} else {
			// Search mode: create a HashMap so insertion is O(1) instead of O(n log n).
			// We already have the sort order, so TreeMap is not necessary here.
			this.termIndex = new HashMap<String, Integer>();
		}
		termIndexBuilt = true;
	}

	public TermsImplV3(boolean indexMode, Collator coll, File termsFile) {
		this(indexMode, coll);
		if (termsFile.exists())
			read(termsFile);
	}

	@Override
	public int indexOf(String term) {

		if (!termIndexBuilt) {
			// We havent' filled termIndex based on terms[] yet.
			// Do so now. (so the first call to this method might be
			// slow in search mode, but it's only used to deserialize
			// HitPropValueContext*, which doesn't happen a lot)
			buildTermIndex();
		}

		Integer index = termIndex.get(term);
		if (index != null)
			return index;
		if (!indexMode)
			return -1; // term not found
		index = termIndex.size();
		termIndex.put(term, index);
		return index;
	}

	@Override
	public synchronized void buildTermIndex() {
		if (termIndexBuilt)
			return;
		for (int i = 0; i < terms.length; i++) {
			termIndex.put(terms[i], i);
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
			RandomAccessFile raf = new RandomAccessFile(termsFile, "r");
			FileChannel fc = raf.getChannel();
			MappedByteBuffer buf = fc.map(MapMode.READ_ONLY, 0, termsFile.length());
			try {
				int n = buf.getInt();

				// Read the term string offsets and string data block
				int[] termStringOffsets = new int[n + 1];
				IntBuffer ib = buf.asIntBuffer();
				ib.get(termStringOffsets);
				int termStringsByteSize = ib.get();
				buf.position(buf.position() + BYTES_PER_INT + BYTES_PER_INT * termStringOffsets.length);
				byte[] termStrings = new byte[termStringsByteSize];
				buf.get(termStrings);
				ib = buf.asIntBuffer();

				// Now instantiate String objects from the offsets and byte data
				terms = new String[n];
				for (int id = 0; id < n; id++) {
					int offset = termStringOffsets[id];
					int length = termStringOffsets[id + 1] - offset;
					String str = new String(termStrings, offset, length, "utf-8");

					// We need to find term for id while searching
					terms[id] = str;
				}
				if (indexMode) {
					buildTermIndex(); // We need to find id for term while indexing
					terms = null; // useless in index mode because we can't add to it, and we don't need it anyway
				} else {
					termIndexBuilt = false; // termIndex hasn't been filled yet

					// Read the sort order arrays
					sortPositionPerId = new int[n];
					sortPositionPerIdInsensitive = new int[n];
					ib.position(ib.position() + n); // Advance past unused sortPos -> id array (left in there for file compatibility)
					ib.get(sortPositionPerId);
					ib.position(ib.position() + n); // Advance past unused sortPos -> id array (left in there for file compatibility)
					ib.get(sortPositionPerIdInsensitive);
				}

			} finally {
				fc.close();
				raf.close();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void write(File termsFile) {
		if (!indexMode)
			throw new RuntimeException("Term.write(): not in index mode!");

		try {
			// Open the terms file
			RandomAccessFile raf = new RandomAccessFile(termsFile, "rw");
			FileChannel fc = raf.getChannel();
			int n = termIndex.size();

			// Fill the terms[] array
			terms = new String[n];
			int termStringsByteSize = 0;
			for (Map.Entry<String, Integer> entry: termIndex.entrySet()) {
				terms[entry.getValue()] = entry.getKey();
				termStringsByteSize += entry.getKey().getBytes("utf-8").length;
			}

			// Calculate the file length and map the file
			long fileLength = 2 * BYTES_PER_INT + (n + 1) * BYTES_PER_INT + termStringsByteSize + NUM_SORT_BUFFERS * BYTES_PER_INT * n;
			fc.truncate(fileLength); // truncate if necessary
			MappedByteBuffer buf = fc.map(MapMode.READ_WRITE, 0, fileLength);
			buf.putInt(n); // Start with the number of terms
			try {
				// Calculate byte offsets for all the terms and fill data array
				int currentOffset = 0;
				int[] termStringOffsets = new int[n + 1];
				byte[] termStrings = new byte[termStringsByteSize];
				for (int i = 0; i < n; i++) {
					termStringOffsets[i] = currentOffset;
					byte[] termBytes = terms[i].getBytes("utf-8");
					System.arraycopy(termBytes, 0, termStrings, currentOffset, termBytes.length);
					currentOffset += termBytes.length;
				}
				termStringOffsets[n] = currentOffset;

				// Write offset and data arrays to file
				IntBuffer ib = buf.asIntBuffer();
				ib.put(termStringOffsets);
				ib.put(termStringsByteSize); // size of the data block to follow
				buf.position(buf.position() + BYTES_PER_INT + BYTES_PER_INT * termStringOffsets.length); // advance past offsets array
				buf.put(termStrings);
				ib = buf.asIntBuffer();

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

			} finally {
				fc.close();
				raf.close();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String get(Integer integer) {
		return terms[integer];
	}

	@Override
	public int numberOfTerms() {
		return sortPositionPerId.length;
	}

	@Override
	public void toSortOrder(int[] tokenId, int[] sortOrder, boolean sensitive) {
		if (sensitive) {
			for (int i = 0; i < tokenId.length; i++) {
				sortOrder[i] = sortPositionPerId[tokenId[i]];
			}
		} else {
			for (int i = 0; i < tokenId.length; i++) {
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

}
