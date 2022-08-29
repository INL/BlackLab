package nl.inl.blacklab.analysis;

import org.apache.lucene.util.BytesRef;

public class PayloadUtils {

    // Annotations to be stored in a forward index start with a byte indicating whether a value is primary or secondary.

    /** First payload byte for primary token values (original value, to be used for concordances) */
    static final byte BYTE_PRIMARY = 1;

    /** First payload byte for secondary token values (e.g. synonym, lowercased, stemmed, etc.) */
    static final byte BYTE_SECONDARY = 0;

    /** If this is the first byte of the payload-including-is-primary, the next byte indicates whether or not it is
        primary or not. If the first byte is anything else, it is always primary. */
    static final byte BYTE_PRIMARY_STATUS_FOLLOWS = 127;

    /** BytesRef to a single {@link #BYTE_PRIMARY} */
    private static final BytesRef BYTES_REF_PRIMARY = new BytesRef(new byte[] { BYTE_PRIMARY_STATUS_FOLLOWS, BYTE_PRIMARY });

    /** BytesRef to a single {@link #BYTE_SECONDARY} */
    private static final BytesRef BYTES_REF_SECONDARY = new BytesRef(new byte[] { BYTE_PRIMARY_STATUS_FOLLOWS, BYTE_SECONDARY });

    /**
     * Make sure this payload indicates whether or not this is a primary token value.
     *
     * The logic is:
     * - if the first byte is 127, the next byte indicates whether this is a primary value (1) or not (0).
     * - if the first byte is NOT 127, this is a primary value and no bytes have been prepended here.
     *
     * @param isPrimary whether this is a primary value or not
     * @param payload value to prepend to (may be null if there was no paylaod)
     * @return new payload value (may be null if there was no payload and this is a primary value)
     */
    public static BytesRef addIsPrimary(boolean isPrimary, BytesRef payload) {
        if (payload == null) {
            // There is no payload.
            if (isPrimary) {
                // Primary is the default, so no payload is fine.
                return null;
            } else {
                // Payload only indicates that it is secondary.
                return BYTES_REF_SECONDARY;
            }
        } else {
            // There's an existing payload.
            if (isPrimary && payload.bytes[payload.offset] != BYTE_PRIMARY_STATUS_FOLLOWS) {
                // This is a primary value (the default), and the first byte of the existing payload
                // is not our magic byte saying that the primary status follows. The existing payload is
                // fine as-is (no indicator always means it is a primary value).
                return payload;
            } else {
                // We need to prepend two bytes to indicate whether or not this is a primary value.
                // The first is 127 to indicate that a primary status follows, the second indicates whether
                // this is a primary value.
                byte[] newPayload = new byte[payload.length + 2];
                newPayload[0] = BYTE_PRIMARY_STATUS_FOLLOWS;
                newPayload[1] = isPrimary ? BYTE_PRIMARY : BYTE_SECONDARY;
                System.arraycopy(payload.bytes, payload.offset, newPayload, 2, payload.length);
                return new BytesRef(newPayload);
            }
        }
    }

    /**
     * Check if payload indicates a primary value or not, and whether or not bytes were prepended.
     *
     * Used to distinguish between primary (for concordances) and secondary token values
     * (e.g. synonyms, lowercased, stemmed, etc.).
     *
     * @param payload payload to check (or null if there is no payload)
     * @return whether this is a primary value or not
     */
    public static boolean isPrimaryValue(BytesRef payload) {
        if (payload == null || payload.length == 0 || payload.bytes[payload.offset] != BYTE_PRIMARY_STATUS_FOLLOWS)
            return true;
        return payload.bytes[payload.offset + 1] == BYTE_PRIMARY;
    }

    /**
     * Does this payload contain a 2-byte "is primary value" indicator?
     *
     * @param payload payload value with optional indicator
     * @return true if there's a 2-byte indicator at the start, false if not
     */
    public static boolean containsIsPrimaryValueIndicator(BytesRef payload) {
        if (payload == null || payload.length == 0 || payload.bytes[payload.offset] != BYTE_PRIMARY_STATUS_FOLLOWS)
            return false;
        return true;
    }

    /**
     * Strip the "is primary value" indicator from the payload, if there was one.
     *
     * @param payload value to optionally strip from
     * @return resulting BytesRef value
     */
    public static BytesRef stripIsPrimaryValue(BytesRef payload) {
        if (payload == null || payload.length == 0 || payload.bytes[payload.offset] != BYTE_PRIMARY_STATUS_FOLLOWS)
            return payload;
        byte[] newPayload = new byte[payload.length - 2];
        System.arraycopy(payload.bytes, payload.offset + 2, newPayload, 0, payload.length - 2);
        return new BytesRef(newPayload);
    }
}
