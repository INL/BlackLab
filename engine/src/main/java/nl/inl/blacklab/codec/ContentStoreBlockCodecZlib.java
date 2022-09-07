package nl.inl.blacklab.codec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** A codec for blocks in the content store that performs no compression but just stores UTF-8 data as-is. */
public class ContentStoreBlockCodecZlib implements ContentStoreBlockCodec {

    public static final ContentStoreBlockCodec INSTANCE = new ContentStoreBlockCodecZlib();

    /** Uncompressed blocks probably shouldn't get larger than this. */
    private static final int MAX_UNCOMPRESSED_BLOCK_SIZE = 10_000;

    /** We probably won't achieve a better compression factor than this. */
    private static final int MAX_COMPRESSION_FACTOR = 6;

    private ContentStoreBlockCodecZlib() { }

    @Override
    public Decoder createDecoder() {
        return new Decoder() {

            Inflater inflater = new Inflater();

            byte[] zipbuf = new byte[MAX_UNCOMPRESSED_BLOCK_SIZE];

            /** Have we reallocated zipbuf already? Next time, just fail. */
            boolean zipbufReallocated = false;

            @Override
            public String decode(byte[] buffer, int offset, int length) throws IOException {
                while (true) {
                    inflater.reset();
                    inflater.setInput(buffer, offset, length);
                    int resultLength = 0;
                    try {
                        resultLength = inflater.inflate(zipbuf);
                    } catch (DataFormatException e) {
                        throw new IOException(e);
                    }
                    if (resultLength <= 0) {
                        throw new IOException("Error, inflate returned " + resultLength);
                    }
                    if (inflater.finished()) {
                        // We're done; return the result.
                        return new String(zipbuf, 0, resultLength, StandardCharsets.UTF_8);
                    } else {
                        // This shouldn't happen because our max block size prevents it
                        if (!zipbufReallocated) {
                            // Try growing the zip buffer, hoping that will fix it
                            zipbufReallocated = true;
                            zipbuf = new byte[Math.max(zipbuf.length * 2, length * MAX_COMPRESSION_FACTOR)];
                        } else {
                            throw new IOException(
                                    "Unzip buffer size " + zipbuf.length + " insufficient even after reallocation");
                        }
                    }
                }
            }
        };
    }

    @Override
    public Encoder createEncoder() {
        return new Encoder() {

            Deflater deflater = new Deflater();

            byte[] zipbuf = new byte[MAX_UNCOMPRESSED_BLOCK_SIZE];

            /** Have we reallocated zipbuf already? Next time, just fail. */
            boolean zipbufReallocated = false;

            @Override
            public byte[] encode(String block, int offset, int length) throws IOException {
                while (true) {
                    deflater.reset();
                    byte[] input = block.substring(offset, offset + length).getBytes(StandardCharsets.UTF_8);
                    deflater.setInput(input);
                    deflater.finish();
                    int compressedDataLength = deflater.deflate(zipbuf, 0, zipbuf.length, Deflater.FULL_FLUSH);
                    if (compressedDataLength <= 0) {
                        throw new IOException("Error, deflate returned " + compressedDataLength);
                    }
                    if (compressedDataLength < zipbuf.length) {
                        // Succesfully compressed.
                        byte[] result = new byte[compressedDataLength];
                        System.arraycopy(zipbuf, 0, result, 0, compressedDataLength);
                        return result;
                    } else {
                        // We ran out of space in the buffer.
                        if (!zipbufReallocated) {
                            // Try growing the zip buffer, hoping that will fix it
                            zipbufReallocated = true;
                            zipbuf = new byte[Math.max(zipbuf.length * 2, length * MAX_COMPRESSION_FACTOR)];
                        } else {
                            throw new IOException(
                                    "Error, deflate returned size of zipbuf (" + zipbuf.length +
                                            "), this indicates insufficient space");
                        }
                    }
                }
            }
        };
    }

    public byte getCode() {
        return 1;
    }
}
