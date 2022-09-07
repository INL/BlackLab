package nl.inl.blacklab.codec;

import java.nio.charset.StandardCharsets;

/** A codec for blocks in the content store that performs no compression but just stores UTF-8 data as-is. */
public class ContentStoreBlockCodecUncompressed implements ContentStoreBlockCodec {

    public static final ContentStoreBlockCodec INSTANCE = new ContentStoreBlockCodecUncompressed();

    private static final Decoder DECODER = (block, offset, length) -> new String(block, offset, length,
            StandardCharsets.UTF_8);

    private static final Encoder ENCODER = new Encoder() {
        @Override
        public byte[] encode(String block, int offset, int length) {
            return block.substring(offset, offset + length).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public int encode(String block, int offset, int length, byte[] encoded, int encodedOffset, int encodedMaxLength) {
            byte[] bytes = encode(block, offset, length);
            if (bytes.length <= encodedMaxLength) {
                // This fits in the buffer; copy it
                System.arraycopy(bytes, 0, encodedOffset, encodedOffset, bytes.length);
            }
            // return the actual length; the called can compare to the max it provided to know if we were succesful
            return bytes.length;
        }
    };

    private ContentStoreBlockCodecUncompressed() { }

    @Override
    public Decoder createDecoder() {
        return DECODER;
    }

    @Override
    public Encoder createEncoder() {
        return ENCODER;
    }

    public byte getCode() {
        return 0;
    }
}
