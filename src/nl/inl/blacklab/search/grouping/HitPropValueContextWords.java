package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.forwardindex.Terms;
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
		String[] parts = info.split(SERIALIZATION_SEPARATOR_ESC_REGEX);
		String fieldPropName = parts[0];
		boolean sensitive = parts[1].equals("s");
		int[] ids = new int[parts.length - 2];
		Terms termsObj = searcher.getForwardIndex(fieldPropName).getTerms();
		for (int i = 2; i < parts.length; i++) {
			ids[i - 2] = termsObj.indexOf(parts[i]);
		}
		return new HitPropValueContextWords(searcher, fieldPropName, ids, sensitive);
	}

	@Override
	public String serialize() {
		StringBuilder b = new StringBuilder();
		for (int v: valueTokenId) {
			if (b.length() > 0)
				b.append(SERIALIZATION_SEPARATOR);
			b.append(terms.get(v));
		}
		return "cws:" + fieldPropName + SERIALIZATION_SEPARATOR + (sensitive ? "s" : "i") + SERIALIZATION_SEPARATOR + b.toString();
	}
}
