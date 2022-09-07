package nl.inl.blacklab.codec;

import java.nio.charset.StandardCharsets;

/** A codec for blocks in the content store that performs no compression but just stores UTF-8 data as-is. */
public class ContentStoreBlockCodecUncompressed implements ContentStoreBlockCodec {

    public static final ContentStoreBlockCodec INSTANCE = new ContentStoreBlockCodecUncompressed();

    private static final Decoder DECODER = (block, offset, length) -> new String(block, offset, length,
            StandardCharsets.UTF_8);

    private static final Encoder ENCODER = (block, offset, length) -> block.substring(offset, offset + length)
            .getBytes(StandardCharsets.UTF_8);

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
