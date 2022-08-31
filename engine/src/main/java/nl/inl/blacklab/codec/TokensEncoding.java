package nl.inl.blacklab.codec;

/**
 * How the tokens in a document are encoded in the tokens file.
 * This allows us to add alternative encodings over time, e.g. to deal with
 * sparse annototions, use variable-length token ids, etc.
 */
enum TokensEncoding {
    /**
     * Simplest possible encoding, one 4-byte integer per token.
     */
    INT_PER_TOKEN((byte) 1),

    /**
     * All our tokens have the same value. Stores only that value.
     */
    ALL_TOKENS_THE_SAME((byte) 2);

    /** How we'll write this encoding to the tokens index file. */
    byte code;

    TokensEncoding(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static TokensEncoding fromCode(byte code) {
        for (TokensEncoding t: values()) {
            if (t.code == code)
                return t;
        }
        throw new IllegalArgumentException("Unknown tokens encoding: " + code);
    }
}
