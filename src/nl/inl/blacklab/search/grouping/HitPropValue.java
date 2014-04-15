package nl.inl.blacklab.search.grouping;

import java.text.Collator;
import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.search.Searcher;
import nl.inl.util.StringUtil;

import org.apache.log4j.Logger;

/**
 * A concrete value of a HitProperty of a Hit
 *
 * Implements <code>Comparable&lt;Object&gt;</code> as opposed to something more specific
 * for performance reasons (preventing lots of runtime type checking during
 * sorting of large results sets)
 */
public abstract class HitPropValue implements Comparable<Object> {
	protected static final Logger logger = Logger.getLogger(HitPropValue.class);

	final static String SERIALIZATION_SEPARATOR = "|";

	final static String SERIALIZATION_SEPARATOR_ESC_REGEX = StringUtil.escapeRegexCharacters(SERIALIZATION_SEPARATOR);

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

	/**
	 * Convert the String representation of a HitPropValue back into the HitPropValue
	 * @param searcher our searcher object (for context word related HitPropValues)
	 * @param serialized the serialized object
	 * @return the HitPropValue object, or null if it could not be deserialized
	 */
	public static HitPropValue deserialize(Searcher searcher, String serialized) {

		if (serialized.contains(","))
			return HitPropValueMultiple.deserialize(searcher, serialized);

		String[] parts = serialized.split(":", 2);
		String type = parts[0].toLowerCase();
		String info = parts[1];
		List<String> types = Arrays.asList("cwo", "cws", "dec", "int", "str"/*, "mul"*/);
		int typeNum = types.indexOf(type);
		switch (typeNum) {
		case 0:
			return HitPropValueContextWord.deserialize(searcher, info);
		case 1:
			return HitPropValueContextWords.deserialize(searcher, info);
		case 2:
			return HitPropValueDecade.deserialize(info);
		case 3:
			return HitPropValueInt.deserialize(info);
		case 4:
			return HitPropValueString.deserialize(info);
		/*case 5:
			return HitPropValueMultiple.deserialize(searcher, info);*/
		}
		logger.debug("Unknown HitPropValue '" + type + "'");
		return null;
	}

	/**
	 * Convert the object to a String representation, for use in e.g. URLs.
	 * @return the serialized object
	 */
	public abstract String serialize();
}
