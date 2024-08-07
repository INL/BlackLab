package nl.inl.blacklab.indexers.config.saxon;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolve xi:includes using a composite Reader. */
class XIncludeResolverReader implements XIncludeResolver {

    private final File baseDir;

    private final Supplier<Reader> supplier;

    public XIncludeResolverReader(Supplier<Reader> supplier, File curDir) {
        this.supplier = supplier;
        this.baseDir = curDir;
    }

    @Override
    public Reader getDocumentReader() {
        final Reader reader = supplier.get();

        return new Reader() {

            /** Buffer length. Chosen large enough that it should be able to contain any tags encountered. */
            public static final int BUFFER_LENGTH = 1024;

            /** Buffered chars. Buffer wraps around. */
            private char[] buffer = new char[BUFFER_LENGTH];

            /** Index of first buffered char */
            private int start = 0;

            /** Index after last buffered char */
            private int end = 0;

            /** If not null, we've encountered an xi:include and should read from this
             *  as soon as our current buffer is empty. */
            private Reader innerReader;

            private boolean isBufferEmpty() {
                return start == end;
            }

            private boolean isBufferFull() {
                return (end + 1) % buffer.length == start;
            }

            /** Add one character to our buffer */
            private void addToBuffer(char c) throws IOException {
                buffer[end] = c;
                end++;
                if (end == buffer.length)
                    end = 0;
            }

            /** Delete n characters from the end of the buffer */
            private void deleteFromBuffer(int n) {
                end -= n;
                if (end < 0)
                    end += buffer.length;
            }

            /** Ensure there's at least one character available in our buffer */
            private boolean ensureBuffered() throws IOException {
                if (!isBufferEmpty())
                    return true;
                int c = reader.read();
                if (c < 0)
                    return false;
                addToBuffer((char) c);
                if (c == '<') {
                    // Make sure we buffer the entire tag
                    int tagStart = end - 1;
                    while (!isBufferFull()) {
                        c = reader.read();
                        if (c < 0)
                            break;
                        addToBuffer((char) c);
                        if (c == '>')
                            break;
                    }
                    checkXInclude(tagStart);
                }
                return true;
            }

            Pattern xIncludeTag = Pattern.compile("<xi:include\\s+href=\"([^\"]+)\"\\s*/>");

            public void checkXInclude(int tagStart) {
                String s;
                if (end >= tagStart) {
                    s = new String(buffer, tagStart, end - tagStart);
                } else {
                    s = new String(buffer, tagStart, buffer.length - tagStart) +
                        new String(buffer, 0, end);
                }
                Matcher m = xIncludeTag.matcher(s);
                if (m.matches()) {
                    String href = m.group(1);
                    try {
                        File f = new File(baseDir, href);
                        // Make sure we're not trying to break out of the base directory
                        if (!f.getCanonicalPath().startsWith(baseDir.getCanonicalPath())) {
                            throw new RuntimeException("XInclude file " + f + " is not in the base directory " + baseDir);
                        }
                        innerReader = new FileReader(f, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    // Remove the XInclude tag from the buffer
                    deleteFromBuffer(end - tagStart + (end < tagStart ? buffer.length : 0));
                }
            }

            @Override
            public int read() throws IOException {
                if (isBufferEmpty() && innerReader == null) {
                    // Empty buffer and no xi:include to read from
                    if (!ensureBuffered()) {
                        // We're done.
                        return -1;
                    }
                }
                if (isBufferEmpty() && innerReader != null) {
                    // Empty buffer and we've encountered an xi:include tag;
                    // read from the included file
                    int c = innerReader.read();
                    if (c >= 0) {
                        return c;
                    }
                    // Done with the included file; resume with the main file
                    innerReader.close();
                    innerReader = null;
                    if (!ensureBuffered()) {
                        // We're done.
                        return -1;
                    }
                }
                // Return first char from buffer
                char c = buffer[start];
                start++;
                if (start == buffer.length)
                    start = 0;
                return c;
            }

            @Override
            public int read(char[] chars, int offset, int length) throws IOException {
                int charsRead = 0;
                while (charsRead < length) {
                    int c = read();
                    if (c < 0)
                        return charsRead == 0 ? c : charsRead;
                    chars[offset] = (char)c;
                    offset++;
                    charsRead++;
                }
                return charsRead;
            }

            @Override
            public void close() throws IOException {
                if (innerReader != null)
                    innerReader.close();
                reader.close();
            }
        };
    }

    @Override
    public boolean anyXIncludesFound() {
        return true;
    }
}
