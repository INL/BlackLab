package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.search.Searcher;

public class HitPropValueContextWord extends HitPropValueContext {
	int valueTokenId;

	int valueSortOrder;

	boolean sensitive;

	public HitPropValueContextWord(Searcher searcher, String fieldPropName, int value, boolean sensitive) {
		super(searcher, fieldPropName);
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
		return valueTokenId < 0 ? "" : terms.get(valueTokenId);
	}

	public static HitPropValue deserialize(Searcher searcher, String info) {
		String[] strIds = info.split(SERIALIZATION_SEPARATOR);
		String fieldPropName = strIds[0];
		boolean sensitive = strIds[1].equals("1");
		return new HitPropValueContextWord(searcher, fieldPropName, Integer.parseInt(strIds[2]), sensitive);
	}

	@Override
	public String serialize() {
		return "cwo:" + fieldPropName + SERIALIZATION_SEPARATOR + (sensitive ? "1" : "0") + SERIALIZATION_SEPARATOR + valueTokenId;
	}
}
