/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.util;

import java.io.BufferedReader;
import java.io.Reader;

/**
 * Utilities to do with input/output (streams, writers/reader)
 */
public class IoUtil {

	/**
	 * Wraps the specified reader in a BufferedReader for efficient and convenient access.
	 *
	 * Does nothing if the reader is already a BufferedReader.
	 *
	 * @param reader
	 *            the reader to wrap
	 * @return the wrapped reader
	 */
	public static BufferedReader makeBuffered(Reader reader) {
		if (reader instanceof BufferedReader)
			return (BufferedReader) reader;
		return new BufferedReader(reader);
	}

}
