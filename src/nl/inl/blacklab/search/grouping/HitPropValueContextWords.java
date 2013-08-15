package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.search.Searcher;
import nl.inl.util.ArrayUtil;

public class HitPropValueContextWords extends HitPropValueContext {
	int[] valueTokenId;

	int[] valueSortOrder;

	boolean sensitive;

	public HitPropValueContextWords(Searcher searcher, String fieldPropName, int[] value, boolean sensitive) {
		super(searcher, fieldPropName);
		this.valueTokenId = value;
		this.sensitive = sensitive;
		valueSortOrder = new int[value.length];
		terms.toSortOrder(value, valueSortOrder, sensitive);
	}

	@Override
	public int compareTo(Object o) {
		return ArrayUtil.compareArrays(valueSortOrder, ((HitPropValueContextWords) o).valueSortOrder);
	}

	@Override
	public int hashCode() {
		int result = 0;
		for (int v: valueSortOrder) {
			result ^= ((Integer) v).hashCode();
		}
		return result;
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		for (int v: valueTokenId) {
			String word = v < 0 ? "" : terms.get(v);
			if (word.length() > 0) {
				if (b.length() > 0)
					b.append(" ");
				b.append(word);
			}
		}
		return b.toString();
	}

	public static HitPropValue deserialize(Searcher searcher, String info) {
		String[] strIds = info.split(SERIALIZATION_SEPARATOR);
		String fieldPropName = strIds[0];
		boolean sensitive = strIds[1].equals("1");
		int[] ids = new int[strIds.length - 2];
		for (int i = 2; i < strIds.length; i++) {
			ids[i - 2] = Integer.parseInt(strIds[i]);
		}
		return new HitPropValueContextWords(searcher, fieldPropName, ids, sensitive);
	}

	@Override
	public String serialize() {
		StringBuilder b = new StringBuilder();
		for (int v: valueTokenId) {
			if (b.length() > 0)
				b.append(SERIALIZATION_SEPARATOR);
			b.append(v);
		}
		return "cws:" + fieldPropName + SERIALIZATION_SEPARATOR + (sensitive ? "1" : "0") + SERIALIZATION_SEPARATOR + b.toString();
	}
}
