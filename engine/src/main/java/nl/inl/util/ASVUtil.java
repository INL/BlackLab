package nl.inl.util;

/**
 * Utility methods to efficiently encode/decode numbers to/from still-comparable strings.
 *
 * This exists because we want to avoid allocating many objects when encoding
 * millions of numbers (for approximate sort values, see HitProperty).
 *
 * For now we simply encode to padded decimal number strings to ensure they can still be
 * compared using a collator. If we sort by numbers a lot, we should think about a more
 * efficient encoding.
 */
public class ASVUtil {

    public static final int RECOMMENDED_MAX_VALUE_LENGTH = 12;

    public static final int MAX_VALUE_LENGTH = 20;

    public static String abbreviate(String value) {
        return value.length() <= RECOMMENDED_MAX_VALUE_LENGTH ? value :
                value.substring(0, RECOMMENDED_MAX_VALUE_LENGTH);
    }

    public static String encode(int value) {
        if (value < 0)
            throw new IllegalArgumentException("Value may not be negative");
        return String.format("%010d", value);
    }

    public static int decodeInt(String value) {
        return Integer.parseInt(value);
    }

    public static String encode(long value) {
        if (value < 0)
            throw new IllegalArgumentException("Value may not be negative");
        return String.format("%019d", value);
    }

    public static long decodeLong(String value) {
        return Long.parseLong(value);
    }

    public static String encodeYear(int year) {
        if (year < 0 || year > 3000)
            throw new IllegalArgumentException("Year must be between 0 and 3000, not " + year);
        return String.format("%04d", year);
    }

    public static int decodeYear(String year) {
        if (year.length() != 4)
            throw new IllegalArgumentException("Year must be 4 characters long, not " + year);
        return Integer.parseInt(year);
    }
}
