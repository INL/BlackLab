package nl.inl.blacklab.codec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import nl.inl.util.SimpleResourcePool;

/** A codec for blocks in the content store that performs no compression but just stores UTF-8 data as-is. */
public class ContentStoreBlockCodecZlib implements ContentStoreBlockCodec {

    public static final ContentStoreBlockCodec INSTANCE = new ContentStoreBlockCodecZlib();

    /** How large is the buffer allowed to get when automatically reallocating? */
    private static final int MAX_ALLOWABLE_BUFFER_SIZE = 100_000;

    private static final int STARTING_ENCODE_BUFFER_SIZE = 3125;

    private static final int STARTING_DECODE_BUFFER_SIZE = 12500;

    private static final int ZIPBUF_GROW_FACTOR = 2;

    private static final int MAX_FREE_POOL_SIZE = 20;

    private final SimpleResourcePool<Encoder> encoderPool;

    private final SimpleResourcePool<Decoder> decoderPool;

    private ContentStoreBlockCodecZlib() {
        encoderPool = new SimpleResourcePool<>(MAX_FREE_POOL_SIZE) {
            @Override
            public Encoder createResource() {
                return createEncoder();
            }
        };
        decoderPool = new SimpleResourcePool<>(MAX_FREE_POOL_SIZE) {
            @Override
            public Decoder createResource() {
                return createDecoder();
            }
        };
    }

    @Override
    public Encoder getEncoder() {
        return encoderPool.acquire();
    }

    @Override
    public Decoder getDecoder() {
        return decoderPool.acquire();
    }

    public Decoder createDecoder() {
        return new Decoder() {

            final Inflater inflater = new Inflater();

            byte[] zipbuf = new byte[STARTING_DECODE_BUFFER_SIZE];

            @Override
            public void close() {
                decoderPool.release(this);
            }

            @Override
            public String decode(byte[] buffer, int offset, int length) throws IOException {
                while (true) {
                    int resultLength = decodeToBytes(buffer, offset, length, zipbuf, 0, zipbuf.length);
                    if (resultLength > 0) {
                        // We're done; return the result.
                        return new String(zipbuf, 0, resultLength, StandardCharsets.UTF_8);
                    } else {
                        // Try growing the zip buffer, hoping that will fix it
                        if (zipbuf.length > MAX_ALLOWABLE_BUFFER_SIZE)
                            throw new IOException("Error, could not decode input of length " + length +
                                    " even with largest buffer (" + zipbuf.length + ")");
                        zipbuf = new byte[zipbuf.length * ZIPBUF_GROW_FACTOR];
                    }
                }
            }

            @Override
            public int decodeToBytes(byte[] buffer, int offset, int length, byte[] decoded, int decodedOffset, int decodedMaxLength) throws IOException {
                inflater.reset();
                inflater.setInput(buffer, offset, length);
                try {
                    int resultLength = inflater.inflate(decoded, decodedOffset, decodedMaxLength);
                    if (resultLength <= 0) {
                        throw new IOException("Error, inflate returned " + resultLength);
                    }
                    if (inflater.finished())
                        return resultLength;
                    return -1;
                } catch (DataFormatException e) {
                    throw new IOException(e);
                }
            }
        };
    }

    public Encoder createEncoder() {
        return new Encoder() {

            final Deflater deflater = new Deflater();

            byte[] zipbuf = new byte[STARTING_ENCODE_BUFFER_SIZE];

            @Override
            public void close() {
                encoderPool.release(this);
            }

            @Override
            public int encode(String input, int offset, int length, byte[] encoded, int encodedOffset, int encodedMaxLength) throws IOException {
                deflater.reset();
                byte[] inputBytes = input.substring(offset, offset + length).getBytes(StandardCharsets.UTF_8);
                deflater.setInput(inputBytes);
                deflater.finish();
                int compressedDataLength = deflater.deflate(encoded, encodedOffset, encodedMaxLength, Deflater.FULL_FLUSH);
                if (compressedDataLength <= 0) {
                    throw new IOException("Error, deflate returned " + compressedDataLength);
                }
                return compressedDataLength;
            }

            @Override
            public byte[] encode(String input, int offset, int length) throws IOException {
                while (true) {
                    int compressedDataLength = encode(input, offset, length, zipbuf, 0, zipbuf.length);
                    if (compressedDataLength < zipbuf.length) {
                        // Return in a new buffer.
                        byte[] result = new byte[compressedDataLength];
                        System.arraycopy(zipbuf, 0, result, 0, compressedDataLength);
                        return result;
                    }
                    // Try again with a larger buffer
                    if (zipbuf.length > MAX_ALLOWABLE_BUFFER_SIZE)
                        throw new IOException("Error, could not encode input of length " + length +
                                " even with largest buffer (" + zipbuf.length + ")");
                    zipbuf = new byte[zipbuf.length * ZIPBUF_GROW_FACTOR];
                }
            }
        };
    }

    public byte getCode() {
        return 1;
    }
}
