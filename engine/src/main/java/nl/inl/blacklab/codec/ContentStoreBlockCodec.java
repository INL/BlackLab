package nl.inl.blacklab.codec;

/** A codec for blocks in the content store. */
interface ContentStoreBlockCodec {

    interface Encoder {
        byte[] encode(String block, int offset, int length);
    }

    interface Decoder {
        String decode(byte[] buffer, int offset, int length);
    }

    static ContentStoreBlockCodec fromCode(byte code) {
        switch (code) {
        case 0:
            return ContentStoreBlockCodecUncompressed.INSTANCE;
        default:
            throw new IllegalArgumentException("Unknown block codec with code " + code);
        }
    }

    Encoder createEncoder();

    Decoder createDecoder();

    byte getCode();
}
