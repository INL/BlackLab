package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.forwardindex.Terms;

public class HitPropValueContextWord extends HitPropValue {
	int value;

	private Terms terms;

	public HitPropValueContextWord(Terms terms, int value) {
		this.value = value;
		this.terms = terms;
	}

	@Override
	public int compareTo(Object o) {
		int a = value, b = ((HitPropValueContextWord)o).value;
		return a == b ? 0 : (a < b ? -1 : 1);
	}

	@Override
	public int hashCode() {
		return ((Integer)value).hashCode();
	}

	@Override
	public String toString() {
		return terms.getFromSortPosition(value);
	}
}
