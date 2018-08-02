package nl.inl.util;

public final class ArrayUtil {

    private ArrayUtil() {
    }

    /**
     * Compare two arrays of ints by comparing each element in succession.
     *
     * The first difference encountered determines the result. If the arrays are of
     * different length but otherwise equal, the longest array will be ordered after
     * the shorter.
     *
     * @param a first array
     * @param b second array
     * @return 0 if equal, negative if a &lt; b, positive if a &gt; b
     */
    public static int compareArrays(int[] a, int[] b) {
        int n = a.length;
        if (b.length < n)
            n = b.length;
        for (int i = 0; i < n; i++) {
            if (a[i] != b[i]) {
                return a[i] - b[i];
            }
        }
        if (a.length == b.length) {
            // Arrays are exactly equal
            return 0;
        }
        if (n == a.length) {
            // Array b is longer than a; sort it after a
            return -1;
        }
        // a longer than b
        return 1;
    }

}
