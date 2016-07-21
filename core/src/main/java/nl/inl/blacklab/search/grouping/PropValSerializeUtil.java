package nl.inl.blacklab.search.grouping;

import nl.inl.util.StringUtil;

/**
 * Takes care of serializing/deserializing
 * Hit/DocProperties and -Values with proper escaping.
 */
public class PropValSerializeUtil {

	private final static String PART_SEPARATOR = ":";

	private final static String PART_SEPARATOR_ESC_REGEX = StringUtil.escapeRegexCharacters(PART_SEPARATOR);

	private final static String MULTIPLE_SEPARATOR = ",";

	private final static String MULTIPLE_SEPARATOR_ESC_REGEX = StringUtil.escapeRegexCharacters(MULTIPLE_SEPARATOR);

	private PropValSerializeUtil() {
	}

	public static String escapePart(String part) {
		return part.replace("$", "$DL").replace(",", "$CM").replace(":", "$CL");
	}

	public static String unescapePart(String partEscaped) {
		return partEscaped.replace("$CL", ":").replace("$CM", ",").replace("$DL", "$");
	}

	public static String combineParts(String... parts) {
		StringBuilder b = new StringBuilder();
		for (String part: parts) {
			if (b.length() > 0)
				b.append(PART_SEPARATOR);
			b.append(escapePart(part));
		}
		return b.toString();
	}

	public static String[] splitPartFirstRest(String partsCombined) {
		String[] parts = partsCombined.split(PART_SEPARATOR_ESC_REGEX, 2);
		for (int i = 0; i < parts.length; i++) {
			parts[i] = unescapePart(parts[i]);
		}
		return parts;
	}

	public static String[] splitParts(String partsCombined) {
		String[] parts = partsCombined.split(PART_SEPARATOR_ESC_REGEX, -1);
		for (int i = 0; i < parts.length; i++) {
			parts[i] = unescapePart(parts[i]);
		}
		return parts;
	}

	public static String combineMultiple(String... values) {
		StringBuilder b = new StringBuilder();
		for (String value: values) {
			if (b.length() > 0)
				b.append(MULTIPLE_SEPARATOR);
			b.append(value);
		}
		return b.toString();
	}

	public static String[] splitMultiple(String valueCombined) {
		return valueCombined.split(MULTIPLE_SEPARATOR_ESC_REGEX, -1);
	}

	public static boolean isMultiple(String serialized) {
		return serialized.contains(PropValSerializeUtil.MULTIPLE_SEPARATOR);
	}


}
