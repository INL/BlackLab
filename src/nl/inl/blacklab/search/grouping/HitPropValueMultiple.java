package nl.inl.blacklab.search.grouping;

import nl.inl.util.Utilities;

public class HitPropValueMultiple extends HitPropValue {
	HitPropValue[] value;

	public HitPropValueMultiple(HitPropValue[] value) {
		this.value = value;
	}

	@Override
	public int compareTo(Object o) {
		return Utilities.compareArrays(value, ((HitPropValueMultiple)o).value);
	}

	@Override
	public int hashCode() {
		int result = 0;
		for (HitPropValue v: value) {
			result ^= v.hashCode();
		}
		return result;
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		for (HitPropValue v: value) {
			if (b.length() > 0)
				b.append(",");
			b.append(v.toString());
		}
		return "HitPropValueMultiple[" + b.toString() + "]";
	}
}
