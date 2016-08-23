package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.Hits;

public class HitPropValueContextWord extends HitPropValueContext {
	int valueTokenId;

	int valueSortOrder;

	boolean sensitive;

	public HitPropValueContextWord(Hits hits, String propName, int value, boolean sensitive) {
		super(hits, propName);
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

	public static HitPropValue deserialize(Hits hits, String info) {
		String[] parts = PropValSerializeUtil.splitParts(info);
		String fieldName = hits.settings().concordanceField();
		String propName = parts[0];
		boolean sensitive = parts[1].equalsIgnoreCase("s");
		Terms termsObj = hits.getSearcher().getForwardIndex(ComplexFieldUtil.propertyField(fieldName, propName)).getTerms();
		int id;
		if (parts[2].length() == 0)
			id = Terms.NO_TERM; // no token
		else
			id = termsObj.indexOf(parts[2]);
		return new HitPropValueContextWord(hits, propName, id, sensitive);
	}

	@Override
	public String toString() {
		return valueTokenId < 0 ? "-" : terms.get(valueTokenId);
	}

	@Override
	public String serialize() {
		String token;
		if (valueTokenId < 0)
			token = ""; // no token
		else
			token = terms.get(valueTokenId);
		return PropValSerializeUtil.combineParts(
			"cwo", propName,
			(sensitive ? "s" : "i"),
			token);
	}
}
