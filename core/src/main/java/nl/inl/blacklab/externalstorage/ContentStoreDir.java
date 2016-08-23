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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import nl.inl.util.ExUtil;

/**
 * Store string content by id in a directory of compound files with a TOC file. Quickly retrieve
 * (parts of) the string content.
 * @deprecated use ContentStoreDirZip
 */
@Deprecated
public class ContentStoreDir extends ContentStoreDirAbstract {
	private static final String CHAR_ENCODING = "UTF-16LE";

	private static final int BYTES_PER_CHAR = 2;

	private static final int BYTE_ORDER_MARK_SIZE = 0;

	/** Table of contents entry */
	static class TocEntry {
		/** id of the string */
		public int id;

		/** id of the file the string was stored in */
		public int fileId;

		/** byte offset into the file of the string */
		public long offset;

		/** length of the string (in bytes) */
		public int length;

		/** was this entry deleted? (remove in next compacting run) */
		public boolean deleted;

		public TocEntry(int id, int fileId, long offset, int length, boolean deleted) {
			super();
			this.id = id;
			this.fileId = fileId;
			this.offset = offset;
			this.length = length;
			this.deleted = deleted;
		}

		/**
		 * Convert TOC entry to a string for storing in the TOC file
		 *
		 * @return string representation of entry
		 */
		public String serialize() {
			return String.format("%d\t%d\t%d\t%d\t%d", id, fileId, offset, length, deleted ? 1 : 0);
		}

		/**
		 * Convert string representation back into a TOC entry.
		 *
		 * @param line
		 *            the line read from the TOC file
		 * @return new TocEntry
		 */
		public static TocEntry deserialize(String line) {
			String[] parts = line.trim().split("\t", -1);
			int id = Integer.parseInt(parts[0]);
			int fileId = Integer.parseInt(parts[1]);
			int offset = Integer.parseInt(parts[2]);
			int length = Integer.parseInt(parts[3]);
			boolean deleted = Integer.parseInt(parts[4]) != 0;
			return new TocEntry(id, fileId, offset, length, deleted);
		}
	}

	/**
	 * The TOC entries
	 */
	private Map<Integer, TocEntry> toc;

	/**
	 * The table of contents (TOC) file
	 */
	private File tocFile;

	/**
	 * Preferred size of data files. Note that the data files consist only of whole documents, so
	 * this size may be exceeded.
	 */
	private long dataFileSizeHint = 100000000; // 100M

	/**
	 * Next content ID.
	 */
	private int nextId = 1;

	/**
	 * File ID of the current file we're writing to.
	 */
	private int currentFileId = 1;

	/**
	 * Length of the file we're writing to.
	 */
	private long currentFileLength = 0;

	/**
	 * If we're writing content in chunks, this keeps track of how many bytes were already written.
	 * Used by store() to calculate the total content length.
	 */
	private int charsAlreadyWritten = 0;

	/**
	 * @param dir directory to use for the content store
	 * @deprecated use ContentStoreDirZip
	 */
	@Deprecated
	public ContentStoreDir(File dir) {
		this(dir, false);
	}

	/**
	 * @param dir directory to use for the content store
	 * @param create if true, create a new content store. Otherwise append to the existing one
	 * @deprecated use ContentStoreDirZip
	 */
	@Deprecated
	public ContentStoreDir(File dir, boolean create) {
		this.dir = dir;
		if (!dir.exists())
			dir.mkdir();
		tocFile = new File(dir, "toc.dat");
		toc = new HashMap<>();
		if (tocFile.exists())
			readToc();
		if (create) {
			clear();
			setStoreType("utf16", "1");
		}
	}

	/**
	 * Delete all content in the document store
	 */
	@Override
	public void clear() {
		// delete all data files and empty TOC
		for (Map.Entry<Integer, TocEntry> me : toc.entrySet()) {
			TocEntry e = me.getValue();
			File f = getContentFile(e.fileId);
			if (f.exists())
				f.delete();
		}
		toc.clear();
		currentFileId = 1;
		currentFileLength = 0;
		nextId = 1;
	}

	/**
	 * Read the table of contents from the file
	 */
	private void readToc() {
		try (BufferedReader f = new BufferedReader(new InputStreamReader(new FileInputStream(tocFile), DEFAULT_CHARSET))) {
			while (true) {
				String line = f.readLine();
				if (line == null)
					break;
				TocEntry e = TocEntry.deserialize(line);
				toc.put(e.id, e);
				if (e.fileId > currentFileId) {
					currentFileId = e.fileId;
					currentFileLength = 0;
				}
				if (e.fileId == currentFileId) {
					long endOfEntry = e.offset + e.length;
					if (endOfEntry > currentFileLength)
						currentFileLength = endOfEntry;
				}
				if (e.id + 1 > nextId)
					nextId = e.id + 1;
			}
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * Write the table of contents to the file
	 */
	@Override
	public void close() {
		try (PrintWriter f = new PrintWriter(new OutputStreamWriter(new FileOutputStream(tocFile), DEFAULT_CHARSET))) {
			for (Map.Entry<Integer, TocEntry> e : toc.entrySet()) {
				f.println(e.getValue().serialize());
			}
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * Indicate preferred maximum size of data files (defaults to 10M)
	 *
	 * @param dataFileSizeHint
	 */
	public void setDataFileSizeHint(long dataFileSizeHint) {
		this.dataFileSizeHint = dataFileSizeHint;
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
		try (Writer w = openCurrentStoreFile()) {
			if (content.length() > 0) {
				w.write(content);
				charsAlreadyWritten += content.length();
			}
		} catch (IOException e) {
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
	@Override
	public synchronized int store(String content) {
		try (Writer w = openCurrentStoreFile()) {
			if (content.length() > 0)
				w.write(content);
			int entryLengthChars = charsAlreadyWritten + content.length();
			TocEntry e = new TocEntry(nextId, currentFileId, currentFileLength,
					entryLengthChars * BYTES_PER_CHAR, false);
			nextId++;
			currentFileLength += e.length;
			toc.put(e.id, e);
			charsAlreadyWritten = 0;
			return e.id;
		} catch (IOException e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	private Writer openCurrentStoreFile() {
		try {
			if (currentFileLength > dataFileSizeHint) {
				currentFileId++;
				currentFileLength = 0;
			}
			File f = getContentFile(currentFileId);
			return new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(f, true)),
					CHAR_ENCODING);
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * Get a data File object, given the data file id.
	 *
	 * @param fileId
	 *            the data file id
	 * @return the File object
	 */
	private File getContentFile(int fileId) {
		File f = new File(dir, String.format("data%04d.dat", fileId));
		return f;
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
	 *            the starting points of the substrings (in characters)
	 * @param end
	 *            the end points of the substrings (in characters)
	 * @return the parts
	 */
	@Override
	public synchronized String[] retrieveParts(int contentId, int[] start, int[] end) {
		try {
			TocEntry e = toc.get(contentId);
			if (e == null || e.deleted)
				return null;

			int n = start.length;
			if (n != end.length)
				throw new IllegalArgumentException("start and end must be of equal length");
			String[] result = new String[n];

			File f = getContentFile(e.fileId);
			try (FileInputStream fileInputStream = new FileInputStream(f)) {
				try (FileChannel fileChannel = fileInputStream.getChannel()) {
					int charLength = e.length / BYTES_PER_CHAR; // take BOM size into account?
					for (int i = 0; i < n; i++) {
						if (start[i] == -1 && end[i] == -1) {
							// whole content
							start[i] = 0;
							end[i] = charLength;
						}
						if (start[i] < 0 || end[i] < 0) {
							throw new IllegalArgumentException("Illegal values, start = " + start[i]
									+ ", end = " + end[i]);
						}
						if (start[i] > charLength || end[i] > charLength) {
							throw new IllegalArgumentException("Value(s) out of range, start = " + start[i]
									+ ", end = " + end[i] + ", content length = " + charLength);
						}
						if (end[i] <= start[i]) {
							throw new IllegalArgumentException(
									"Tried to read empty or negative length snippet (from " + start[i]
											+ " to " + end[i] + ")");
						}
						long startOffsetBytes = e.offset + start[i] * BYTES_PER_CHAR
								+ BYTE_ORDER_MARK_SIZE;
						int bytesToRead = (end[i] - start[i]) * BYTES_PER_CHAR;
						ByteBuffer buffer = ByteBuffer.allocate(bytesToRead);
						int bytesRead = fileChannel.read(buffer, startOffsetBytes);
						if (bytesRead < bytesToRead)
							throw new RuntimeException("Not enough bytes read, " + bytesRead + " < "
									+ bytesToRead);
						result[i] = new String(buffer.array(), 0, bytesRead, CHAR_ENCODING);
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
	}

	@Override
	public Set<Integer> getDocIds() {
		return toc.keySet();
	}

	@Override
	public boolean isDeleted(int id) {
		return toc.get(id).deleted;
	}

	@Override
	public int getDocLength(int id) {
		return toc.get(id).length;
	}

	@Override
	public Set<Integer> idSet() {
		return toc.keySet();
	}

}
