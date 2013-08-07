package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.util.ArrayUtil;

public class HitPropValueContextWords extends HitPropValueContext {
	int[] valueTokenId;

	int[] valueSortOrder;

	boolean sensitive;

	public HitPropValueContextWords(Terms terms, int[] value, boolean sensitive) {
		super(terms);
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

	public static HitPropValue deserialize(Terms terms, String info) {
		String[] strIds = info.split(",,");
		boolean sensitive = strIds[0].equals("1");
		int[] ids = new int[strIds.length - 1];
		for (int i = 1; i < strIds.length; i++) {
			ids[i] = Integer.parseInt(strIds[i + 1]);
		}
		return new HitPropValueContextWords(terms, ids, sensitive);
	}

	@Override
	public String serialize() {
		StringBuilder b = new StringBuilder();
		for (int v: valueTokenId) {
			if (b.length() > 0)
				b.append(",,");
			b.append(v);
		}
		return "cws:" + (sensitive ? "1" : "0") + ",," + b.toString();
	}
}
