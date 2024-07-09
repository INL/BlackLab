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

    /** If not null: text content as string. bytes and chars will be null in this case */
    private String str;

    /** If not null: bytes buffer for text content (use offset and length as well). str and chars will be null. */
    private byte[] bytes;

    /** If not null: chars buffer for text content (use offset and length as well). str and bytes will be null. */
    private char[] chars;

    /** For chars and bytes: start offset of text content */
    private int offset;

    /** For chars and bytes: length of text content (in chars or bytes) */
    private int length;

    /** For bytes: charset to use */
    private Charset bytesCharset;

    public TextContent(String str) {
        if (str == null)
            throw new IllegalArgumentException("str == null");
        this.str = str;
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
        this.bytesCharset = charset;
    }

    public TextContent(char[] chars) {
        if (chars == null)
            throw new IllegalArgumentException("chars == null");
        this.chars = chars;
        this.offset = 0;
        this.length = chars.length;
    }

    public TextContent(char[] chars, int offset, int length) {
        if (chars == null)
            throw new IllegalArgumentException("chars == null");
        if (offset < 0 || length < 0 || offset + length > chars.length)
            throw new IllegalArgumentException(
                    "illegal values for offset and length: " + offset + ", " + length + " (bytes.length = "
                            + bytes.length + ")");
        this.chars = chars;
        this.offset = offset;
        this.length = length;
    }

    public TextContent(ByteArrayOutputStream cmdiBuffer, Charset charset) {
        bytes = cmdiBuffer.toByteArray();
        offset = 0;
        length = bytes.length;
        this.bytesCharset = charset;
    }

    private int getLength() {
        if (str != null)
            return str.length();
        return length;
    }

    public boolean isEmpty() {
        return getLength() == 0;
    }

    /**
     * Append this text content to a string builder.
     * @param builder where to add our content
     */
    public void appendToStringBuilder(StringBuilder builder) {
        if (str != null) {
            builder.append(str);
        } else if (chars != null) {
            builder.append(chars, offset, length);
        } else {
            CharsetDecoder cd = bytesCharset.newDecoder();
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
        else if (chars != null)
            return new String(chars, offset, length);
        else
            return new String(bytes, offset, length, bytesCharset);
    }
}
