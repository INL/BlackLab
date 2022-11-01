package nl.inl.blacklab.codec;

/**
 * How the tokens in a document are encoded in the tokens file.
 * This allows us to add alternative encodings over time, e.g. to deal with
 * sparse annototions, use variable-length token ids, etc.
 */
enum TokensCodec {
    /** Simplest possible encoding, one 4-byte integer per token. */
    INT_PER_TOKEN((byte) 1),

    /** All our tokens have the same value. Stores only that value (as Integer). */
    ALL_TOKENS_THE_SAME((byte) 2),

    /** Like INT_PER_TOKEN, but only stores a short (16 bits) per token */
    SHORT_PER_TOKEN((byte) 3),

    /* Like INT_PER_TOKEN, but only stores a byte (8 bits) per token */
    BYTE_PER_TOKEN((byte) 4);

    /** How we'll write this encoding to the tokens index file. */
    byte code;

    TokensCodec(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static TokensCodec fromCode(byte code) {
        for (TokensCodec t: values()) {
            if (t.code == code)
                return t;
        }
        throw new IllegalArgumentException("Unknown tokens codec: " + code);
    }
}
