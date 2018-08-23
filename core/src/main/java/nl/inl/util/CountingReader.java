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
/**
 *
 */
package nl.inl.util;

import java.io.IOException;
import java.io.Reader;

/**
 * A Reader decorator that keeps track of the number of characters read.
 */
public class CountingReader extends Reader {
    /**
     * The Reader we're decorating
     */
    private Reader reader_;

    /**
     * Character count
     */
    private int charsRead;

    /**
     * Last count reported
     */
    private int lastReported;

    /**
     * Constructor
     *
     * @param reader the Reader we're decorating
     */
    public CountingReader(Reader reader) {
        reader_ = reader;
        charsRead = lastReported = 0;
    }

    @Override
    public void close() throws IOException {
        reader_.close();
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int bytesReadThisTime = reader_.read(cbuf, off, len);
        if (bytesReadThisTime > 0)
            charsRead += bytesReadThisTime;
        return bytesReadThisTime;
    }

    public int getCharsRead() {
        lastReported = charsRead;
        return charsRead;
    }

    public int getCharsReadSinceLastCall() {
        int n = charsRead - lastReported;
        lastReported = charsRead;
        return n;
    }
}
