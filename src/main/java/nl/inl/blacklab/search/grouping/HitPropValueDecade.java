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
		int decade;
		try {
			decade = Integer.parseInt(info);
		} catch (NumberFormatException e) {
			decade = 0;
		}
		return new HitPropValueDecade(decade);
	}

	@Override
	public String serialize() {
		return PropValSerializeUtil.combineParts("dec", "" + value);
	}
}
