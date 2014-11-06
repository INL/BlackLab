package nl.inl.blacklab.search.grouping;


public class HitPropValueDecade extends HitPropValueInt {
	public HitPropValueDecade(int value) {
		super(value);
	}

	@Override
	public String toString() {
		int year = value - value % 10;
		return year + "-" + (year + 9);
	}

	public static HitPropValue deserialize(String info) {
		return new HitPropValueDecade(Integer.parseInt(info));
	}

	@Override
	public String serialize() {
		return PropValSerializeUtil.combineParts("dec", "" + value);
	}
}
