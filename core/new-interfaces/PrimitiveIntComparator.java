package nl.inl.blacklab.interfaces;

import java.util.Comparator;

/**
 * Integer comparator that works for primitive ints as well.
 * 
 * Used e.g. for sorting, where we want to avoid unnecessary (un)boxing.
 */
public interface PrimitiveIntComparator extends Comparator<Integer> {
	int compare(int a, int b);
	
	@Override
	default int compare(Integer a, Integer b) {
		return compare((int)a, (int)b);
	}
}