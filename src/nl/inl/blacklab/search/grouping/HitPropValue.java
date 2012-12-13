package nl.inl.blacklab.search.grouping;

import java.text.Collator;

import nl.inl.util.StringUtil;

/**
 * A concrete value of a HitProperty of a Hit
 *
 * Implements <code>Comparable&lt;Object&gt;</code> as opposed to something more specific
 * for performance reasons (preventing lots of runtime type checking during
 * sorting of large results sets)
 */
public abstract class HitPropValue implements Comparable<Object> {

	/**
	 * Collator to use for string comparison while sorting/grouping
	 */
	static Collator collator = StringUtil.getDefaultCollator();

	@Override
	public abstract int compareTo(Object o);

	@Override
	public abstract int hashCode();

	@Override
	public boolean equals(Object obj) {
		return compareTo(obj) == 0;
	}

	@Override
	public abstract String toString();

}
