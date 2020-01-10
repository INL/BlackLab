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
package nl.inl.blacklab.contentstore;

import java.io.File;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.util.SimpleResourcePool;

/**
 * Variant of ContentStoreDirUtf8 that also compresses each block using GZIP.
 * Achieves around 4x compression of XML data, depending on block size
 */
public class ContentStoreDirZip extends ContentStoreDirUtf8 {

    /** Size of the (de)compressor and zipbuf pools */
    private static final int POOL_SIZE = 10;
    
    private SimpleResourcePool<Deflater> compresserPool;

    private SimpleResourcePool<Inflater> decompresserPool;

    private SimpleResourcePool<byte[]> zipbufPool;

    /**
     * @param dir content store dir
     */
    public ContentStoreDirZip(File dir) {
        this(dir, false);
    }

    /**
     * @param dir content store dir
     * @param create if true, create a new content store
     */
    public ContentStoreDirZip(File dir, boolean create) {
        super(dir, create);
        compresserPool = new SimpleResourcePool<Deflater>(POOL_SIZE) {
            @Override
            public Deflater createResource() {
                return new Deflater();
            }

            @Override
            public void destroyResource(Deflater resource) {
                resource.end();
            }
        };
        decompresserPool = new SimpleResourcePool<Inflater>(POOL_SIZE) {
            @Override
            public Inflater createResource() {
                return new Inflater();
            }

            @Override
            public void destroyResource(Inflater resource) {
                resource.end();
            }
        };
        zipbufPool = new SimpleResourcePool<byte[]>(POOL_SIZE) {
            @Override
            public byte[] createResource() {
                return new byte[newEntryBlockSizeCharacters * 3];
            }
        };
    }

    @Override
    public void close() {
        compresserPool.close();
        decompresserPool.close();
        zipbufPool.close();
        super.close();
    }

    @Override
    protected void setStoreType() {
        setStoreType("utf8zip", "1");
    }

    private void zipBufferSizeChanged() {
        zipbufPool.clear();
    }

    @Override
    public void setBlockSizeCharacters(int size) {
        super.setBlockSizeCharacters(size);
        zipBufferSizeChanged();
    }

    @Override
    protected byte[] encodeBlock(String block) {
        byte[] encoded = super.encodeBlock(block);
        Deflater compresser = compresserPool.acquire();
        byte[] zipbuf = zipbufPool.acquire();
        try {
            compresser.reset();
            compresser.setInput(encoded);
            compresser.finish();
            int compressedDataLength = compresser.deflate(zipbuf);
            if (compressedDataLength <= 0)
                throw new BlackLabRuntimeException("Error, deflate returned " + compressedDataLength);
            if (compressedDataLength == zipbuf.length)
                throw new BlackLabRuntimeException("Error, deflate returned size of zipbuf, this indicates insufficient space");
            return Arrays.copyOfRange(zipbuf, 0, compressedDataLength);
        } finally {
            compresserPool.release(compresser);
            zipbufPool.release(zipbuf);
        }
    }

    @Override
    protected String decodeBlock(byte[] buf, int offset, int length) {
        try {
            // unzip block
            Inflater decompresser = decompresserPool.acquire();
            byte[] zipbuf = zipbufPool.acquire();
            try {
                decompresser.reset();
                decompresser.setInput(buf, offset, length);
                int resultLength = decompresser.inflate(zipbuf);
                if (resultLength <= 0)
                    throw new BlackLabRuntimeException("Error, inflate returned " + resultLength);
                if (!decompresser.finished()) {
                    // This shouldn't happen because our max block size prevents it
                    throw new BlackLabRuntimeException("Unzip buffer size insufficient");
                }
                return super.decodeBlock(zipbuf, 0, resultLength);
            } finally {
                decompresserPool.release(decompresser);
                zipbufPool.release(zipbuf);
            }
        } catch (DataFormatException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

}
