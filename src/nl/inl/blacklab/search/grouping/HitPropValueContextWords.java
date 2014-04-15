package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.Hits;
import nl.inl.util.ArrayUtil;

public class HitPropValueContextWords extends HitPropValueContext {
	int[] valueTokenId;

	int[] valueSortOrder;

	boolean sensitive;

	public HitPropValueContextWords(Hits hits, String propName, int[] value, boolean sensitive) {
		super(hits, propName);
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

	public static HitPropValue deserialize(Hits hits, String info) {
		String[] parts = info.split(SERIALIZATION_SEPARATOR_ESC_REGEX);
		String fieldName = hits.getConcordanceFieldName();
		String propName = parts[0];
		boolean sensitive = parts[1].equalsIgnoreCase("s");
		int[] ids = new int[parts.length - 2];
		Terms termsObj = hits.getSearcher().getForwardIndex(ComplexFieldUtil.propertyField(fieldName, propName)).getTerms();
		for (int i = 2; i < parts.length; i++) {
			int tokenId;
			if (parts[i].length() == 0)
				tokenId = -1; // no token
			else
				tokenId = termsObj.indexOf(parts[i]);
			ids[i - 2] = tokenId;
		}
		return new HitPropValueContextWords(hits, propName, ids, sensitive);
	}

	@Override
	public String serialize() {
		StringBuilder b = new StringBuilder();
		for (int v: valueTokenId) {
			if (b.length() > 0)
				b.append(SERIALIZATION_SEPARATOR);
			String token;
			if (v < 0)
				token = ""; // no token
			else
				token = terms.get(v);
			b.append(token);
		}
		return "cws:" + propName + SERIALIZATION_SEPARATOR + (sensitive ? "s" : "i") + SERIALIZATION_SEPARATOR + b.toString();
	}
}
