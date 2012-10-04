/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import nl.inl.util.Utilities;

/**
 * Keeps a first-come-first-serve list of unique terms.
 */
public class Terms {
	ArrayList<String> terms = new ArrayList<String>();

	Map<String, Integer> termIndex = new HashMap<String, Integer>();

	private boolean indexMode;

	/**
	 * How much to reserve at the end of mapped file for writing
	 */
	private int writeMapReserve = 1000000; // 1M

	public Terms(boolean indexMode) {
		this.indexMode = indexMode;
	}

	public Terms(File termsFile, boolean indexMode) {
		this.indexMode = indexMode;
		if (termsFile.exists())
			read(termsFile);
	}

	public int indexOf(String term) {
		Integer index = termIndex.get(term);
		if (index != null)
			return index;
		index = termIndex.size();
		termIndex.put(term, index);
		if (!indexMode)
			terms.add(term);
		return index;
	}

	public void clear() {
		terms.clear();
		termIndex.clear();
	}

	private void read(File termsFile) {
		termIndex.clear();
		terms.clear();
		try {
			RandomAccessFile raf = new RandomAccessFile(termsFile, "r");
			FileChannel fc = raf.getChannel();
			MappedByteBuffer buf = fc.map(MapMode.READ_ONLY, 0, termsFile.length());
			try {
				int n = buf.getInt();

				if (!indexMode) {
					// Fill terms with nulls so we can set each term as we read it
					terms.ensureCapacity(n);
					for (int i = 0; i < n; i++) {
						terms.add(null);
					}
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
						terms.set(id, str);
					}
				}
			} finally {
				fc.close();
				raf.close();

				// Unmap buffer to prevent file lock
				Utilities.cleanDirectBufferHack(buf);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void write(File termsFile) {
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

					if (buf.remaining() < 8 + strBuf.length) {
						// Create new direct buffer with extra room
						int p = buf.position();
						bufOffset += p;

						// Unmap buffer to prevent file lock
						Utilities.cleanDirectBufferHack(buf);

						buf = fc.map(MapMode.READ_WRITE, bufOffset, writeMapReserve);
					}

					buf.putInt(id);
					buf.putInt(strBuf.length);
					buf.put(strBuf, 0, strBuf.length);
				}
			} finally {
				fc.close();
				raf.close();

				// Unmap buffer to prevent file lock
				Utilities.cleanDirectBufferHack(buf);

			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String get(Integer integer) {
		return terms.get(integer);
	}
}
