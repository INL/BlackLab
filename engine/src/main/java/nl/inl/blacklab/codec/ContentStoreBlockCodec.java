package nl.inl.blacklab.codec;

/** A codec for blocks in the content store. */
interface ContentStoreBlockCodec {

    static ContentStoreBlockCodec fromCode(byte code) {
        switch (code) {
        case 0:
            return ContentStoreBlockCodecUncompressed.INSTANCE;
        default:
            throw new IllegalArgumentException("Unknown block codec with code " + code);
        }
    }

    public byte[] compress(String block);

    public String decompress(byte[] block, int offset, int length);

    byte getCode();
}
