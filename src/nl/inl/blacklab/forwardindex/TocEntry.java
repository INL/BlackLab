/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.forwardindex;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Table of contents entry; stored in docs.dat */
class TocEntry {
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
}