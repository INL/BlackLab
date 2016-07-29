package nl.inl.blacklab.search.grouping;


public class HitPropValueDecade extends HitPropValueInt {

	public HitPropValueDecade(int value) {
		super(value);
	}

	public static HitPropValue deserialize(String info) {
		if (info.equals("unknown"))
			return new HitPropValueDecade(HitPropertyDocumentDecade.UNKNOWN_VALUE);
		int decade;
		try {
			decade = Integer.parseInt(info);
		} catch (NumberFormatException e) {
			decade = 0;
		}
		return new HitPropValueDecade(decade);
	}

	@Override
	public String toString() {
		if (value == HitPropertyDocumentDecade.UNKNOWN_VALUE)
			return "unknown";
		int year = value - value % 10;
		return year + "-" + (year + 9);
	}

	@Override
	public String serialize() {
		if (value == HitPropertyDocumentDecade.UNKNOWN_VALUE)
			return "unknown";
		return PropValSerializeUtil.combineParts("dec", "" + value);
	}
}
