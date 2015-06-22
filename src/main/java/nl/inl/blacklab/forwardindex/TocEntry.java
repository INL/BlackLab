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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Table of contents entry; stored in docs.dat */
class TocEntry implements Comparable<TocEntry> {
	/** token offset in tokens.dat */
	public long offset;

	/** number of tokens in document */
	public int length;

	/** was this entry deleted? (remove in next compacting run) */
	public boolean deleted;

	public TocEntry(long offset, int length, boolean deleted) {
		super();
		this.offset = offset;
		this.length = length;
		this.deleted = deleted;
	}

	/**
	 * Convert TOC entry to a string for storing in the TOC file
	 *
	 * @param d
	 *            where to serialize to
	 * @throws IOException
	 */
	public void serialize(DataOutput d) throws IOException {
		d.writeLong(offset);
		d.writeInt(length);
		d.writeByte(deleted ? 1 : 0);
	}

	/**
	 * Convert string representation back into a TOC entry.
	 *
	 * @param d
	 *            where to read from
	 * @return new TocEntry
	 * @throws IOException
	 */
	public static TocEntry deserialize(DataInput d) throws IOException {
		long offset = d.readLong();
		int length = d.readInt();
		boolean deleted = d.readByte() != 0;
		return new TocEntry(offset, length, deleted);
	}

	/**
	 * Compare this entry to another (for sorting).
	 * @param o the entry to compare with
	 * @return the comparison result
	 */
	@Override
	public int compareTo(TocEntry o) {
		return (int) (offset - o.offset);
	}
}