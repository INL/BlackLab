package nl.inl.blacklab.search.grouping;

import java.util.Arrays;

import nl.inl.blacklab.search.Hits;

public class HitPropValueMultiple extends HitPropValue {
	HitPropValue[] value;

	public HitPropValueMultiple(HitPropValue[] value) {
		this.value = value;
	}

	@Override
	public int compareTo(Object o) {
		return compareHitPropValueArrays(value, ((HitPropValueMultiple)o).value);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(value);
	}

	public static HitPropValueMultiple deserialize(Hits hits, String info) {
		String[] strValues = PropValSerializeUtil.splitMultiple(info);
		HitPropValue[] values = new HitPropValue[strValues.length];
		int i = 0;
		for (String strValue: strValues) {
			values[i] = HitPropValue.deserialize(hits, strValue);
			i++;
		}
		return new HitPropValueMultiple(values);
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		for (HitPropValue v: value) {
			if (b.length() > 0)
				b.append(" / ");
			b.append(v.toString());
		}
		return b.toString();
	}

	@Override
	public String serialize() {
		String[] valuesSerialized = new String[value.length];
		for (int i = 0; i < value.length; i++) {
			valuesSerialized[i] = value[i].serialize();
		}
		return PropValSerializeUtil.combineMultiple(valuesSerialized);
	}

	/**
	 * Compare two arrays of HitPropValue objects, by comparing each
	 * one in succession.
	 *
	 * The first difference encountered determines the result. If the
	 * arrays are of different length but otherwise equal, the longest
	 * array will be ordered after the shorter.
	 *
	 * @param a first array
	 * @param b second array
	 * @return 0 if equal, negative if a &lt; b, positive if a &gt; b
	 */
	private static int compareHitPropValueArrays(HitPropValue[] a, HitPropValue[] b) {
		int n = a.length;
		if (b.length < n)
			n = b.length;
		for (int i = 0; i < n; i++) {
			// Does this element decide the comparison?
			int cmp = a[i].compareTo(b[i]);
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
