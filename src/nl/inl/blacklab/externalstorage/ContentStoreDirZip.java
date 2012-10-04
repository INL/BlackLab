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

import java.io.File;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Variant of ContentStoreDirUtf8 that also compresses each block using GZIP. Achieves around 4x
 * compression of XML data, depending on block size
 */
public class ContentStoreDirZip extends ContentStoreDirUtf8 {

	/**
	 * Takes care of compression
	 */
	Deflater compresser = new Deflater();

	/**
	 * Takes care of decompression
	 */
	Inflater decompresser = new Inflater();

	/**
	 * The buffer to use for (de)compressing the data
	 */
	byte[] zipbuf;

	public ContentStoreDirZip(File dir) {
		this(dir, false);
	}

	public ContentStoreDirZip(File dir, boolean create) {
		super(dir, create);
		setZipBufferSize();
	}

	@Override
	public void close() {
		compresser.end();
		decompresser.end();
		super.close();
	}

	@Override
	protected void setStoreType() {
		setStoreType("utf8zip", "1");
	}

	private void setZipBufferSize() {
		zipbuf = new byte[newEntryBlockSizeCharacters * 2]; // just to be safe
	}

	@Override
	public void setBlockSizeCharacters(int size) {
		super.setBlockSizeCharacters(size);
		setZipBufferSize();
	}

	@Override
	protected byte[] encodeBlock(String block) {
		byte[] encoded = super.encodeBlock(block);
		compresser.reset();
		compresser.setInput(encoded);
		compresser.finish();
		int compressedDataLength = compresser.deflate(zipbuf);
		if (compressedDataLength <= 0) {
			throw new RuntimeException("Error, deflate returned " + compressedDataLength);
		}
		return Arrays.copyOfRange(zipbuf, 0, compressedDataLength);
	}

	@Override
	protected String decodeBlock(byte[] buf, int offset, int length) {
		try {
			// unzip block
			decompresser.reset();
			decompresser.setInput(buf, offset, length);
			int resultLength = decompresser.inflate(zipbuf);
			if (resultLength <= 0) {
				throw new RuntimeException("Error, inflate returned " + resultLength);
			}
			return super.decodeBlock(zipbuf, 0, resultLength);
		} catch (DataFormatException e) {
			throw new RuntimeException(e);
		}
	}

}
