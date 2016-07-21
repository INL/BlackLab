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
package nl.inl.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Utilities to do with input/output (streams, writers/reader)
 */
public class IoUtil {

	private IoUtil() {
	}

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

	/**
	 * Read text from an input stream.
	 * @param is the input stream
	 * @param encoding the character encoding to use
	 * @return the text read
	 * @throws IOException
	 */
	public static String readStream(InputStream is, String encoding) throws IOException {
		BufferedReader reader = makeBuffered(new InputStreamReader(is, encoding));
		StringBuilder b = new StringBuilder();
		while (true) {
			String line = reader.readLine();
			if (line == null)
				break;
			b.append(line);
		}
		return b.toString();
	}

}
