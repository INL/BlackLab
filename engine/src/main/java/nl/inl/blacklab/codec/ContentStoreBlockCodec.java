package nl.inl.blacklab.codec;

import java.io.IOException;

/** A codec for blocks in the content store. */
interface ContentStoreBlockCodec {

    interface Encoder extends AutoCloseable {
        /** Encode and return a new byte buffer.
         *
         * @param input value to encode
         * @param offset starting offset in the value to encode
         * @param length number of characters from value to encode
         * @return encoded buffer
         */
        byte[] encode(String input, int offset, int length) throws IOException;

        /** Encode in provided buffer.
         *
         * @param input value to encode
         * @param offset starting offset in the value to encode
         * @param length number of characters from value to encode
         * @param encoded output buffer
         * @param encodedOffset where to start writing in the output buffer
         * @param encodedMaxLength maximum number of bytes to write in the output buffer
         * @return compressed data length; was succesful if LESS THAN encodedMaxLength
         */
        int encode(String input, int offset, int length, byte[] encoded, int encodedOffset, int encodedMaxLength) throws IOException;

        void close();
    }

    interface Decoder extends AutoCloseable {
        String decode(byte[] buffer, int offset, int length) throws IOException;

        void close();
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

    Encoder getEncoder();

    Decoder getDecoder();

    byte getCode();
}
