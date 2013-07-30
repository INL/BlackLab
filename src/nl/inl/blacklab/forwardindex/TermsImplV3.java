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
import java.util.Map;
import java.util.SortedMap;
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

	/** The index numbers, by sort order. Only valid when indexMode == false. */
	int[] idPerSortPosition;

	/** The sorting position for each index number. Inverse of idPerSortPosition[]
	 *  array. Only valid when indexMode == false. */
	int[] sortPositionPerId;

	/** The index numbers, by case-insensitive sort order. Only valid when indexMode == false. */
	int[] idPerSortPositionInsensitive;

	/** The case-insensitive sorting position for each index number. Inverse of idPerSortPosition[]
	 *  array. Only valid when indexMode == false. */
	int[] sortPositionPerIdInsensitive;

	/**
	 * Mapping from term to its unique index number. We use a SortedMap because we wish to
	 * store the sorted index numbers later (to speed up sorting). Only valid in indexMode.
	 */
	SortedMap<String, Integer> termIndex;

	/** If true, we're indexing data and adding terms. If false, we're searching and just retrieving terms. */
	private boolean indexMode;

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
		this.collator = collator;
		this.collatorInsensitive = (Collator)collator.clone();
		collatorInsensitive.setStrength(Collator.PRIMARY);

		// Create a SortedMap based on the specified Collator.

		this.termIndex = new TreeMap<String, Integer>(this.collator);
	}

	public TermsImplV3(boolean indexMode, Collator coll, File termsFile) {
		this(indexMode, coll);
		if (termsFile.exists())
			read(termsFile);
	}

	@Override
	public int indexOf(String term) {
		if (!indexMode)
			throw new UnsupportedOperationException("Cannot call indexOf in search mode!");
		Integer index = termIndex.get(term);
		if (index != null)
			return index;
		index = termIndex.size();
		termIndex.put(term, index);
		return index;
	}

	@Override
	public void clear() {
		termIndex.clear();
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
					if (indexMode) {
						// We need to find id for term while indexing
						termIndex.put(str, id);
					}
					// We need to find term for id while searching
					terms[id] = str;
				}

				if (!indexMode) {
					// Read the sort order arrays
					idPerSortPosition = new int[n];
					sortPositionPerId = new int[n];
					idPerSortPositionInsensitive = new int[n];
					sortPositionPerIdInsensitive = new int[n];
					ib.get(idPerSortPosition);
					ib.get(sortPositionPerId);
					ib.get(idPerSortPositionInsensitive);
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
			throw new UnsupportedOperationException("Term.write(): not in index mode!");

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
				idPerSortPosition = new int[n];
				sortPositionPerId = new int[n];
				Integer[] insensitive = new Integer[n];
				for (int id: termIndex.values()) {
					idPerSortPosition[i] = id;
					sortPositionPerId[id] = i;
					insensitive[i] = id; // fill this so we can re-sort later, faster b/c already partially sorted
					i++;
				}
				ib.put(idPerSortPosition);
				ib.put(sortPositionPerId);

				// Now, sort case-insensitively and write those arrays as well
				Arrays.sort(insensitive, new Comparator<Integer>() {
					@Override
					public int compare(Integer a, Integer b) {
						return collatorInsensitive.compare(terms[a], terms[b]);
					}
				});
				idPerSortPositionInsensitive = new int[n];
				sortPositionPerIdInsensitive = new int[n];
				for (i = 0; i < n; i++) {
					idPerSortPositionInsensitive[i] = insensitive[i];
					sortPositionPerIdInsensitive[insensitive[i]] = i;
				}
				ib.put(idPerSortPositionInsensitive);
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
		return terms[integer]; //.get(integer);
	}

	@Override
	public int sortPositionToId(int sortPosition) {
		return idPerSortPosition[sortPosition]; //.get(integer);
	}

	@Override
	public int idToSortPosition(int id) {
		return sortPositionPerId[id]; //.get(integer);
	}

	@Override
	public int sortPositionToIdInsensitive(int sortPosition) {
		return idPerSortPositionInsensitive[sortPosition]; //.get(integer);
	}

	@Override
	public int idToSortPositionInsensitive(int id) {
		return sortPositionPerIdInsensitive[id]; //.get(integer);
	}

	@Override
	public String getFromSortPosition(int sortPosition) {
		if (sortPosition < 0) {
			// This can happen, for example, when sorting on right context when the hit is
			// at the end of the document.
			return "";
		}
		return terms[idPerSortPosition[sortPosition]];
	}

	@Override
	public int numberOfTerms() {
		return idPerSortPosition.length;
	}

}
