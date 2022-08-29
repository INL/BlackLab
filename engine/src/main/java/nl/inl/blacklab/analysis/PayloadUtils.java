package nl.inl.blacklab.analysis;

import org.apache.lucene.util.BytesRef;

public class PayloadUtils {

    // Annotations to be stored in a forward index start with a byte indicating whether a value is primary or secondary.

    /** First payload byte for primary token values (original value, to be used for concordances) */
    static final byte BYTE_PRIMARY = 1;

    /** First payload byte for secondary token values (e.g. synonym, lowercased, stemmed, etc.) */
    static final byte BYTE_SECONDARY = 0;

    /** BytesRef to a single {@link #BYTE_PRIMARY} */
    private static final BytesRef BYTES_REF_PRIMARY = new BytesRef(new byte[] { BYTE_PRIMARY });

    /** BytesRef to a single {@link #BYTE_SECONDARY} */
    private static final BytesRef BYTES_REF_SECONDARY = new BytesRef(new byte[] { BYTE_SECONDARY });

    /**
     * Prepend a single byte to a BytesRef.
     *
     * @param b byte to prepend
     * @param payload value to prepend to
     * @return prepended value
     */
    public static BytesRef prependToPayload(byte b, BytesRef payload) {
        if (payload == null) {
            if (b == BYTE_PRIMARY)
                return BYTES_REF_PRIMARY;
            else
                return BYTES_REF_SECONDARY;
        } else {
            byte[] newPayload = new byte[payload.length + 1];
            newPayload[0] = b;
            System.arraycopy(payload.bytes, payload.offset, newPayload, 1, payload.length);
            return new BytesRef(newPayload);
        }
    }

    /**
     * Get first byte from a BytesRef.
     *
     * Used to distinguish between primary (for concordances) and secondary token values
     * (e.g. synonyms, lowercased, stemmed, etc.).
     *
     * @param payload where to get the first byte from
     * @return first byte
     */
    public static byte getFirstByte(BytesRef payload) {
        return payload.length > 0 ? payload.bytes[payload.offset] : -1;
    }

    /**
     * Strip first byte from a BytesRef.
     *
     * @param payload value to strip first byte from
     * @return new BytesRef value
     */
    public static BytesRef stripFirstByte(BytesRef payload) {
        byte[] newPayload = new byte[payload.length - 1];
        System.arraycopy(payload.bytes, payload.offset + 1, newPayload, 0, payload.length - 1);
        return new BytesRef(newPayload);
    }
}
