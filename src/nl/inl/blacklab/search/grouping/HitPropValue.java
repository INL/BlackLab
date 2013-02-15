package nl.inl.blacklab.search.grouping;

import java.text.Collator;
import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.forwardindex.Terms;
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

	public static HitPropValue deserialize(Terms terms, String serialized) {

		String[] parts = serialized.split(":", 2);
		String type = parts[0], info = parts[1];
		List<String> types = Arrays.asList("cwo", "cws", "dec", "int", "mul", "str");
		int typeNum = types.indexOf(type);
		switch (typeNum) {
		case 0:
			return HitPropValueContextWord.deserialize(terms, info);
		case 1:
			return HitPropValueContextWords.deserialize(terms, info);
		case 2:
			return HitPropValueDecade.deserialize(info);
		case 3:
			return HitPropValueInt.deserialize(info);
		case 4:
			return HitPropValueMultiple.deserialize(terms, info);
		case 5:
			return HitPropValueString.deserialize(info);
		}
		throw new RuntimeException("Unknown HitPropValue type");
	}

	public abstract String serialize();
}
