package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.forwardindex.Terms;

public class HitPropValueContextWord extends HitPropValueContext {
	int valueTokenId;

	int valueSortOrder;

	boolean sensitive;

	public HitPropValueContextWord(Terms terms, int value, boolean sensitive) {
		super(terms);
		this.valueTokenId = value;
		this.sensitive = sensitive;
		valueSortOrder = value < 0 ? value : terms.idToSortPosition(value, sensitive);
	}

	@Override
	public int compareTo(Object o) {
		int a = valueSortOrder, b = ((HitPropValueContextWord)o).valueSortOrder;
		return a == b ? 0 : (a < b ? -1 : 1);
	}

	@Override
	public int hashCode() {
		return ((Integer)valueSortOrder).hashCode();
	}

	@Override
	public String toString() {
		return terms.get(valueTokenId);
	}

	public static HitPropValue deserialize(Terms terms, String info) {
		String[] strIds = info.split(",,");
		boolean sensitive = strIds[0].equals("1");
		return new HitPropValueContextWord(terms, Integer.parseInt(strIds[1]), sensitive);
	}

	@Override
	public String serialize() {
		return "cwo:" + (sensitive ? "1" : "0") + ",," + valueTokenId;
	}
}
