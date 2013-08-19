package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.forwardindex.Terms;
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
		String[] parts = info.split(SERIALIZATION_SEPARATOR_ESC_REGEX);
		String fieldPropName = parts[0];
		boolean sensitive = parts[1].equals("s");
		Terms termsObj = searcher.getForwardIndex(fieldPropName).getTerms();
		int id = termsObj.indexOf(parts[2]);
		return new HitPropValueContextWord(searcher, fieldPropName, id, sensitive);
	}

	@Override
	public String serialize() {
		String token = terms.get(valueTokenId);
		return "cwo:" + fieldPropName + SERIALIZATION_SEPARATOR + (sensitive ? "s" : "i") + SERIALIZATION_SEPARATOR + token;
	}
}
