package nl.inl.util;

public class Sort {
    public interface Sortable {
        public int compare(int a, int b);
        public void swap(int a, int b);        
    }

    // Based on the java default implementation of int sorts
    public static void sort(Sortable sortable, int off, int len) {
        // Insertion sort on smallest arrays
        if (len < 7) {
            for (int i = off; i < len + off; i++) {
                for (int j = i; j > off && sortable.compare(j - 1, j) > 0; j--) {
                    sortable.swap(j, j - 1);
                }
            }
            return;
        }

        // Choose a partition element, v
        int m = off + (len >> 1); // Small arrays, middle element
        if (len > 7) {
            int l = off;
            int n = off + len - 1;
            if (len > 40) { // Big arrays, pseudomedian of 9
                int s = len / 8;
                l = med3(sortable, l, l + s, l + 2 * s);
                m = med3(sortable, m - s, m, m + s);
                n = med3(sortable, n - 2 * s, n - s, n);
            }
            m = med3(sortable, l, m, n); // Mid-size, med of 3
        }

        // Establish Invariant: v* (<v)* (>v)* v*
        int a = off, b = a, c = off + len - 1, d = c;
        while (true) {
            while (b <= c && sortable.compare(b, m) <= 0) {
                if (sortable.compare(b, m) == 0) {
                    sortable.swap(a, b);
                    m = a;
                    a++;
                }
                b++;
            }
            while (c >= b && sortable.compare(c, m) >= 0) {
                if (sortable.compare(c, m) == 0) {
                    sortable.swap(c, d);
                    m = d;
                    d--;
                }
                c--;
            }
            if (b > c) {
                break;
            }
            sortable.swap(b++, c--);
        }

        // Swap partition elements back to middle
        int s, n = off + len;
        s = Math.min(a - off, b - a);
        vecswap(sortable, off, b - s, s);
        s = Math.min(d - c, n - d - 1);
        vecswap(sortable, b, n - s, s);

        // Recursively sort non-partition-elements
        if ((s = b - a) > 1) {
            sort(sortable, off, s);
        }
        if ((s = d - c) > 1) {
            sort(sortable, n - s, s);
        }
    }

    private static int med3(Sortable sortable, int a, int b, int c) {
        return sortable.compare(a, b) < 0 ? (sortable.compare(b, c) < 0 ? b : sortable.compare(a, c) < 0 ? c : a)
                : sortable.compare(b, c) > 0 ? b : sortable.compare(a, c) > 0 ? c : a;
    }

    private static void vecswap(Sortable sortable, int a, int b, int n) {
        for (int i = 0; i < n; i++, a++, b++) {
            sortable.swap(a, b);
        }
    }
}
