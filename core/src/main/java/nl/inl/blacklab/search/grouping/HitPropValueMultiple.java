package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.search.Hits;
import nl.inl.util.ArrayUtil;

public class HitPropValueMultiple extends HitPropValue {
	HitPropValue[] value;

	public HitPropValueMultiple(HitPropValue[] value) {
		this.value = value;
	}

	@Override
	public int compareTo(Object o) {
		return ArrayUtil.compareArrays(value, ((HitPropValueMultiple)o).value);
	}

	@Override
	public int hashCode() {
		int result = 0;
		for (HitPropValue v: value) {
			result ^= v.hashCode();
		}
		return result;
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
	public String serialize() {
		String[] valuesSerialized = new String[value.length];
		for (int i = 0; i < value.length; i++) {
			valuesSerialized[i] = value[i].serialize();
		}
		return PropValSerializeUtil.combineMultiple(valuesSerialized);
	}
}
