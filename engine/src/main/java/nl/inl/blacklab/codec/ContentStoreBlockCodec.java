package nl.inl.blacklab.codec;

import java.io.IOException;

/** A codec for blocks in the content store. */
interface ContentStoreBlockCodec {

    interface Encoder {
        byte[] encode(String block, int offset, int length) throws IOException;
    }

    interface Decoder {
        String decode(byte[] buffer, int offset, int length) throws IOException;
    }

    static ContentStoreBlockCodec fromCode(byte code) {
        switch (code) {
        case 0:
            return ContentStoreBlockCodecUncompressed.INSTANCE;
        case 1:
            return ContentStoreBlockCodecZlib.INSTANCE;
        default:
            throw new IllegalArgumentException("Unknown block codec with code " + code);
        }
    }

    Encoder createEncoder();

    Decoder createDecoder();

    byte getCode();
}
