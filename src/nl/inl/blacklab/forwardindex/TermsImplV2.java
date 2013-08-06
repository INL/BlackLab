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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.text.Collator;
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
 * This version of the class stores the terms in a less efficient way
 * and does not support case-insensitive sorting/grouping. Version 3 does.
 *
 * NOTE: version 2 of the forward index will be phased out soon. Re-index
 * your data if you're still using it!
 */
class TermsImplV2 extends Terms {
	protected static final Logger logger = Logger.getLogger(TermsImplV2.class);

	final static int INT_SIZE = Integer.SIZE / Byte.SIZE;

	/** The terms, by index number. Only valid when indexMode == false. */
	String[] terms;

	/** The index numbers, by sort order. Only valid when indexMode == false. */
	int[] idPerSortPosition;

	/** The sorting position for each index number. Inverse of idPerSortPosition[]
	 *  array. Only valid when indexMode == false. */
	int[] sortPositionPerId;

	/**
	 * Mapping from term to its unique index number. We use a SortedMap because we wish to
	 * store the sorted index numbers later (to speed up sorting). Only valid in indexMode.
	 */
	SortedMap<String, Integer> termIndex;

	/** If true, we're indexing data and adding terms. If false, we're searching and just retrieving terms. */
	private boolean indexMode;

	/**
	 * How much to reserve at the end of mapped file for writing
	 */
	private int writeMapReserve = 1000000; // 1M

	/**
	 * Collator to use for string comparisons
	 */
	final Collator collator;

	public TermsImplV2(boolean indexMode, Collator collator) {
		this.indexMode = indexMode;
		if (collator == null)
			collator = Collator.getInstance();
		this.collator = collator;

		// Create a SortedMap based on the specified Collator.

		this.termIndex = new TreeMap<String, Integer>(this.collator);
	}

	public TermsImplV2(boolean indexMode, Collator coll, File termsFile) {
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

				if (!indexMode) {
					terms = new String[n];
				}

				// Now read terms and fill appropriate structure.
				byte[] strBuf = new byte[100];
				for (int i = 0; i < n; i++) {
					int id = buf.getInt();
					int len = buf.getInt();
					if (len > strBuf.length) {
						strBuf = new byte[len];
					}
					buf.get(strBuf, 0, len);
					String str = new String(strBuf, 0, len, "utf-8");
					if (indexMode) {
						// We need to find id for term while indexing
						termIndex.put(str, id);
					} else {
						// We need to find term for id while searching
						terms[id] = str;
					}
				}

				if (!indexMode) {

					idPerSortPosition = new int[n];
					sortPositionPerId = new int[n];

					// Read the term sort order
					for (int i = 0; i < n; i++) {
						int termIndexNumber = buf.getInt();
						idPerSortPosition[i] = termIndexNumber;
						sortPositionPerId[termIndexNumber] = i;
					}

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
			RandomAccessFile raf = new RandomAccessFile(termsFile, "rw");
			FileChannel fc = raf.getChannel();
			long fl = termsFile.length() + writeMapReserve;
			MappedByteBuffer buf = fc.map(MapMode.READ_WRITE, 0, fl);
			int n = termIndex.size();
			buf.putInt(n);
			long bufOffset = 0;
			try {
				for (Map.Entry<String, Integer> e : termIndex.entrySet()) {
					Integer id = e.getValue();
					String term = e.getKey();
					byte[] strBuf = term.getBytes("utf-8");

					if (buf.remaining() < INT_SIZE * 2 + strBuf.length) {
						// Create new direct buffer with extra room
						int p = buf.position();
						bufOffset += p;

						buf = fc.map(MapMode.READ_WRITE, bufOffset, writeMapReserve);
					}

					buf.putInt(id);
					buf.putInt(strBuf.length);
					buf.put(strBuf, 0, strBuf.length);
				}

				// Write the sort order
				// Because termIndex is a SortedMap, values are returned in key-sorted order.
				// In other words, the index numbers are in order of sorted terms, so the id
				// for 'aardvark' comes before the id for 'ape', etc.
				for (int id: termIndex.values()) {

					if (buf.remaining() < INT_SIZE) {
						// Create new direct buffer with extra room
						int p = buf.position();
						bufOffset += p;

						buf = fc.map(MapMode.READ_WRITE, bufOffset, writeMapReserve);
					}

					buf.putInt(id);
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
	public String get(Integer integer) {
		return terms[integer]; //.get(integer);
	}

	@Override
	public int numberOfTerms() {
		return idPerSortPosition.length;
	}

	@Override
	public int idToSortPosition(int id, boolean sensitive) {
		// Incorrect, but v2 doesn't support insensitive
		return sortPositionPerId[id];
	}

}
