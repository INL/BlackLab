package nl.inl.blacklab.contentstore;

import java.io.ByteArrayOutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

/**
 * Text content, either as bytes or as a String.
 */
public class TextContent {

    private String str;

    private byte[] bytes;

    private int offset;

    private int length;

    private Charset charset;

    public TextContent(String str) {
        if (str == null)
            throw new IllegalArgumentException("str == null");
        this.str = str;
    }

    public TextContent(StringBuilder content) {
        str = content.toString();
    }

    public TextContent(byte[] bytes, int offset, int length, Charset charset) {
        if (bytes == null)
            throw new IllegalArgumentException("bytes == null");
        if (offset < 0 || length < 0 || offset + length > bytes.length)
            throw new IllegalArgumentException(
                    "illegal values for offset and length: " + offset + ", " + length + " (bytes.length = "
                            + bytes.length + ")");
        this.bytes = bytes;
        this.offset = offset;
        this.length = length;
        this.charset = charset;
    }

    public TextContent(ByteArrayOutputStream cmdiBuffer, Charset charset) {
        bytes = cmdiBuffer.toByteArray();
        offset = 0;
        length = bytes.length;
        this.charset = charset;
    }

    public boolean isString() {
        return str != null;
    }

    public String getString() {
        if (str == null)
            throw new IllegalStateException("No string available, use getBytes()");
        return str;
    }

    public byte[] getBytes() {
        if (bytes == null)
            throw new IllegalStateException("No bytes available, use getString()");
        return bytes;
    }

    public int getOffset() {
        if (bytes == null)
            throw new IllegalStateException("No offset available, use getString()");
        return offset;
    }

    public int getLength() {
        if (bytes == null)
            return str.length();
        return length;
    }

    public boolean isEmpty() {
        return getLength() == 0;
    }

    public Charset getCharset() {
        if (bytes == null)
            throw new IllegalStateException("No charset available, use getString()");
        return charset;
    }

    /**
     * Append this text content to a string builder.
     * @param builder where to add our content
     */
    public void appendToStringBuilder(StringBuilder builder) {
        if (isString()) {
            builder.append(str);
        } else {
            CharsetDecoder cd = charset.newDecoder();
            ByteBuffer in = ByteBuffer.wrap(bytes, offset, length);
            CharBuffer out = CharBuffer.allocate(1024);
            while (in.hasRemaining()) {
                cd.decode(in, out, true);
                builder.append(out.array(), 0, out.position());
                ((Buffer)out).position(0);
            }
        }
    }

    @Override
    public String toString() {
        if (str != null)
            return str;
        else
            return new String(bytes, offset, length, charset);
    }
}
