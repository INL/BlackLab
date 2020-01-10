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

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;

/**
 * InputStream that detects and skips BOM in UTF-8 (and other Unicode encodings)
 * files.
 *
 * Taken from
 * http://stackoverflow.com/questions/1835430/byte-order-mark-screws-up-file-reading-in-java
 * which was adapted from Google Data API Client library (Apache License).
 */
public class UnicodeStream extends InputStream {

    private static final int BOM_SIZE = 4;

    // The steam we're wrapping
    private InputStream stream;

    // Encoding of the character set, either detected through the BOM or a default value.
    private Charset encoding;

    /**
     * Construct UnicodeReader, a Reader that skips the BOM in Unicode files (if
     * present).
     *
     * @param in Input stream.
     * @param defaultEncoding Default encoding to be used if BOM is not found, or
     *            <code>null</code> to use system default encoding.
     * @throws IOException If an I/O error occurs.
     */
    public UnicodeStream(InputStream in, String defaultEncoding) throws IOException {
        this(in, Charset.forName(defaultEncoding));
    }

    /**
     * Construct InputStream, a Reader that detects and skips the BOM in Unicode
     * files (if present).
     *
     * @param in Input stream.
     * @param defaultEncoding Default encoding to be used if BOM is not found, or
     *            <code>null</code> to use system default encoding.
     * @throws IOException If an I/O error occurs.
     */
    public UnicodeStream(InputStream in, Charset defaultEncoding) throws IOException {
        byte[] bom = new byte[BOM_SIZE];
        int unread;
        PushbackInputStream pushbackStream = new PushbackInputStream(in, BOM_SIZE);
        int n = pushbackStream.read(bom, 0, bom.length);

        // Read ahead four bytes and check for BOM marks.
        if ((bom[0] == (byte) 0xEF) && (bom[1] == (byte) 0xBB) && (bom[2] == (byte) 0xBF)) {
            encoding = Charset.forName("UTF-8");
            unread = n - 3;
        } else if ((bom[0] == (byte) 0xFE) && (bom[1] == (byte) 0xFF)) {
            encoding = Charset.forName("UTF-16BE");
            unread = n - 2;
        } else if ((bom[0] == (byte) 0xFF) && (bom[1] == (byte) 0xFE)) {
            encoding = Charset.forName("UTF-16LE");
            unread = n - 2;
        } else if ((bom[0] == (byte) 0x00) && (bom[1] == (byte) 0x00) && (bom[2] == (byte) 0xFE)
                && (bom[3] == (byte) 0xFF)) {
            encoding = Charset.forName("UTF-32BE");
            unread = n - 4;
        } else if ((bom[0] == (byte) 0xFF) && (bom[1] == (byte) 0xFE) && (bom[2] == (byte) 0x00)
                && (bom[3] == (byte) 0x00)) {
            encoding = Charset.forName("UTF-32LE");
            unread = n - 4;
        } else {
            encoding = defaultEncoding;
            unread = n;
        }

        // Unread bytes if necessary and skip BOM marks.
        if (unread > 0) {
            pushbackStream.unread(bom, (n - unread), unread);
        } else if (unread < -1) {
            pushbackStream.unread(bom, 0, 0);
        }

        // Use given encoding.
        if (encoding == null) {
            encoding = Charset.defaultCharset();
        }
        stream = pushbackStream;
    }

    public Charset getEncoding() {
        return encoding;
    }

    @Override
    public int read(byte[] cbuf, int off, int len) throws IOException {
        return stream.read(cbuf, off, len);
    }

    @Override
    public int read(byte[] cbuf) throws IOException {
        return stream.read(cbuf);
    }

    @Override
    public int read() throws IOException {
        return stream.read();
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }

    @Override
    public int available() throws IOException {
        return stream.available();
    }

    @Override
    public synchronized void mark(int readlimit) {
        stream.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
        return stream.markSupported();
    }

    @Override
    public synchronized void reset() throws IOException {
        stream.reset();
    }

    @Override
    public long skip(long n) throws IOException {
        return stream.skip(n);
    }

}
