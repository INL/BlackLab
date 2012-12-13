package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.util.Utilities;

public class HitPropValueContextWords extends HitPropValue {
	int[] value;

	private Terms terms;

	public HitPropValueContextWords(Terms terms, int[] value) {
		this.value = value;
		this.terms = terms;
	}

	@Override
	public int compareTo(Object o) {
		return Utilities.compareArrays(value, ((HitPropValueContextWords)o).value);
	}

	@Override
	public int hashCode() {
		int result = 0;
		for (int v: value) {
			result ^= ((Integer)v).hashCode();
		}
		return result;
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		for (int v: value) {
			String word = terms.getFromSortPosition(v);
			if (word.length() > 0) {
				if (b.length() > 0)
					b.append(" ");
				b.append(word);
			}
		}
		return b.toString();
	}
}
