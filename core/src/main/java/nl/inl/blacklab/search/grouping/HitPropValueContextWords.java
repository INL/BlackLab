package nl.inl.blacklab.search.grouping;

import java.util.Arrays;

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
		return Arrays.hashCode(valueSortOrder);
	}

	public static HitPropValue deserialize(Hits hits, String info) {
		String[] parts = PropValSerializeUtil.splitParts(info);
		String fieldName = hits.settings().concordanceField();
		String propName = parts[0];
		boolean sensitive = parts[1].equalsIgnoreCase("s");
		int[] ids = new int[parts.length - 2];
		Terms termsObj = hits.getSearcher().getForwardIndex(ComplexFieldUtil.propertyField(fieldName, propName)).getTerms();
		for (int i = 2; i < parts.length; i++) {
			int tokenId;
			if (parts[i].length() == 0)
				tokenId = Terms.NO_TERM; // no token
			else
				tokenId = termsObj.indexOf(parts[i]);
			ids[i - 2] = tokenId;
		}
		return new HitPropValueContextWords(hits, propName, ids, sensitive);
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		for (int v: valueTokenId) {
			String word = v < 0 ? "-" : terms.get(v);
			if (word.length() > 0) {
				if (b.length() > 0)
					b.append(" ");
				b.append(word);
			}
		}
		return b.toString();
	}

	@Override
	public String serialize() {
		String[] parts = new String[valueTokenId.length + 3];
		parts[0] = "cws";
		parts[1] = propName;
		parts[2] = (sensitive ? "s" : "i");
		for (int i = 0; i < valueTokenId.length; i++) {
			int v = valueTokenId[i];
			if (v < 0)
				parts[i + 3] = ""; // no token
			else
				parts[i + 3] = terms.get(v);
		}
		return PropValSerializeUtil.combineParts(parts);
	}
}
