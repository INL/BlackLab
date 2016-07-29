package nl.inl.blacklab.search.grouping;


public class HitPropValueInt extends HitPropValue {
	int value;

	public int getValue() {
		return value;
	}

	public HitPropValueInt(int value) {
		this.value = value;
	}

	@Override
	public int compareTo(Object o) {
		return value - ((HitPropValueInt)o).value;
	}

	@Override
	public int hashCode() {
		return ((Integer)value).hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return value == ((HitPropValueInt)obj).value;
	}

	public static HitPropValue deserialize(String info) {
		int v;
		try {
			v = Integer.parseInt(info);
		} catch (NumberFormatException e) {
			v = 0;
		}
		return new HitPropValueInt(v);
	}

	@Override
	public String toString() {
		return value + "";
	}

	@Override
	public String serialize() {
		return PropValSerializeUtil.combineParts("int", "" + value);
	}

}
