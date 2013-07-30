package nl.inl.util;

public class ArrayUtil {

	/**
	 * Compare two arrays of ints by comparing each element in succession.
	 *
	 * The first difference encountered determines the result. If the
	 * arrays are of different length but otherwise equal, the longest
	 * array will be ordered after the shorter.
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

	/**
	 * Compare two arrays by comparing each element in succession.
	 *
	 * The first difference encountered determines the result. If the
	 * arrays are of different length but otherwise equal, the longest
	 * array will be ordered after the shorter.
	 *
	 * If the elements of the array are themselves arrays, this function
	 * is called recursively.
	 *
	 * @param a first array
	 * @param b second array
	 * @return 0 if equal, negative if a &lt; b, positive if a &gt; b
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static int compareArrays(Object[] a, Object[] b) {
		int n = a.length;
		if (b.length < n)
			n = b.length;
		for (int i = 0; i < n; i++) {
			int cmp;
	
			// Figure out how to compare these two elements
			if (a[i] instanceof int[]) {
				// Use int array compare
				cmp = compareArrays((int[])a[i], (int[])b[i]);
			} else if (a[i] instanceof Object[]) {
				// Use Object array compare
				cmp = compareArrays((Object[])a[i], (Object[])b[i]);
			} else if (a[i] instanceof Comparable) {
				// Assume Comparable and use that
				cmp = ((Comparable)a[i]).compareTo(b[i]);
			} else {
				throw new RuntimeException("Cannot compare objects of type " + a[i].getClass());
			}
	
			// Did that decide the comparison?
			if (cmp != 0) {
				return cmp; // yep, done
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
