/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.index.complex;

/**
 * Some utility functions for dealing with complex field names.
 */
public class ComplexFieldUtil {
	/**
	 * String used to separate the base field name (say, contents) and the field property (pos,
	 * lemma, etc.)
	 */
	static final String PROP_SEP = "__";

	/**
	 * String used to separate the field/property name (say, contents_lemma) and the alternative
	 * (e.g. "s" for case-sensitive)
	 */
	static final String ALT_SEP = "_ALT_";

	/** Length of the ALT separator */
	final static int ALT_SEP_LEN = ALT_SEP.length();

	/** Length of the PROP separator */
	final static int PROP_SEP_LEN = PROP_SEP.length();

	/**
	 * Construct a complex field name from its component parts.
	 *
	 * @param baseName
	 *            the base field name
	 * @param propertyName
	 *            the property name
	 * @return the combined complex field name
	 */
	public static String fieldName(String baseName, String propertyName) {
		return fieldName(baseName, propertyName, null);
	}

	/**
	 * Construct a complex field name from its component parts.
	 *
	 * @param baseName
	 *            the base field name
	 * @param propertyName
	 *            the property name
	 * @param alternative
	 *            the alternative name
	 * @return the combined complex field name
	 */
	public static String fieldName(String baseName, String propertyName, String alternative) {
		String baseAndProp = "";
		boolean propGiven = propertyName != null && propertyName.length() > 0;
		if (baseName == null || baseName.length() == 0) {
			if (propGiven) {
				baseAndProp = propertyName;
			} else
				throw new RuntimeException("Must specify a base name, a property name or both");
		} else {
			baseAndProp = baseName + (propGiven ? PROP_SEP + propertyName : "");
		}

		if (alternative == null || alternative.length() == 0)
			return baseAndProp;
		return baseAndProp + ALT_SEP + alternative;
	}

	/**
	 * Split a complex field name into its component parts: base name, property name, and
	 * alternative. The last two are both optional; either or both may be present.
	 *
	 * Example: the name "contents__lemma_ALT_s" will be split into "contents", "lemma" and "s".
	 *
	 * @param fieldName
	 *            the field name
	 * @return the component parts base name, property name and alternative. Missing parts will be
	 *         empty strings.
	 */
	public static String[] split(String fieldName) {
		int p = fieldName.indexOf(PROP_SEP);
		int a = fieldName.indexOf(ALT_SEP);
		if (p < 0) {
			// No property
			if (a < 0) {
				// No alternative
				return new String[] { fieldName, "", "" };
			}
			return new String[] { fieldName.substring(0, a), "",
					fieldName.substring(a + ALT_SEP_LEN) };
		}

		// Property
		if (a < 0) {
			// No alternative
			return new String[] { fieldName.substring(0, p), fieldName.substring(p + PROP_SEP_LEN),
					"" };
		}

		// Alternative
		if (p > a)
			throw new RuntimeException("Malformed field name, PROP separator after ALT separator");

		return new String[] { fieldName.substring(0, p), fieldName.substring(p + PROP_SEP_LEN, a),
				fieldName.substring(a + ALT_SEP_LEN) };
	}

	/**
	 * Gets the base complex field name from a Lucene index field name. So "contents" and
	 * "contents__pos" would both yield "contents".
	 *
	 * @param fieldName
	 *            the Lucene index field name, with possibly a property added
	 * @return the base complex field name
	 */
	public static String getBaseName(String fieldName) {
		// Strip off property and possible alternative
		int pos = fieldName.indexOf(PROP_SEP);
		if (pos >= 0) {
			return fieldName.substring(0, pos);
		}
		pos = fieldName.indexOf(ALT_SEP);
		if (pos >= 0) {
			return fieldName.substring(0, pos);
		}
		return fieldName;
	}

	/**
	 * Get the "alternative" part of the complex field property. An alternative of a property is,
	 * for example, a case-sensitive version of the property. For the alternatives "contents_ALT_s"
	 * or "contents__lemma_ALT_s" this method produces "s".
	 *
	 * @param fieldName
	 *            the field plus property plus alternative name we want to get the alternative from
	 * @return the alternative name
	 */
	public static String getAlternative(String fieldName) {
		int pos = fieldName.lastIndexOf(ALT_SEP);
		if (pos < 0)
			return "";
		return fieldName.substring(pos + ALT_SEP_LEN);
	}

	/**
	 * Get the "property" part of the complex field property. For the field name "contents_lemma" or
	 * "contents__lemma_ALT_s" this method produces "lemma".
	 *
	 * @param fieldName
	 *            the field plus property plus alternative name we want to get the alternative from
	 * @return the alternative name
	 */
	public static String getProperty(String fieldName) {
		int pos = fieldName.indexOf(PROP_SEP);
		if (pos < 0)
			return ""; // No property

		// See if there's an alternative we need to get rid of
		int posA = fieldName.lastIndexOf(ALT_SEP);
		if (posA < 0)
			return fieldName.substring(pos + PROP_SEP_LEN);
		return fieldName.substring(pos + PROP_SEP_LEN, posA); // Strip off alternative
	}

	/**
	 * Checks if the given fieldName actually points to an alternative property (for example, a
	 * case-sensitive version of a property).
	 *
	 * Example: the fieldName "contents__lemma_ALT_s" indicates the "s" alternative of the "lemma"
	 * property of the "contents" complex field.
	 *
	 * @param fieldName
	 *            the full fieldname, possibly including a property and/or alternative
	 * @param alternative
	 *            the alternative to test for
	 * @return true if the fieldName indicates the specified alternative
	 */
	public static boolean isAlternative(String fieldName, String alternative) {
		if (alternative.length() == 0) {
			// No alternative, therefore no alternative separator
			return fieldName.indexOf(ALT_SEP) < 0;
		}

		// We're looking for an alternative
		String altSuffix = ALT_SEP + alternative;
		int pos = fieldName.indexOf(altSuffix);
		if (pos < 0)
			return false; // Not found

		// Should occur at the end
		return pos + altSuffix.length() == fieldName.length();
	}

	/**
	 * Checks if the given fieldName actually points to the specified property (for example, the
	 * lemma or the POS of the original token).
	 *
	 * Example: the fieldName "contents__lemma" indicates the "lemma" property of the "contents"
	 * complex field.
	 *
	 * Field names may also include an "alternative", for example a case-sensitive version. For
	 * example, "contents__lemma_ALT_s" is the "s" alternative of the "lemma" property. However,
	 * alternatives are not tested for in this method, just the property name.
	 *
	 * @param fieldName
	 *            the full fieldname, possibly including a property and/or alternative
	 * @param propName
	 *            the property to test for
	 * @return true if the fieldName indicates the specified alternative
	 */
	public static boolean isProperty(String fieldName, String propName) {
		if (propName.length() == 0) {
			// No property, therefore no property separator
			return fieldName.indexOf(PROP_SEP) < 0;
		}

		// We're looking for a property, either at the end of the fieldName or right before the ALT
		// separator
		String firstBit = PROP_SEP + propName;
		int pos = fieldName.indexOf(firstBit);
		if (pos < 0)
			return false; // Not found
		int endOfFirstBit = pos + firstBit.length();

		// Should occur at the end, or should be followed by ALT separator
		return fieldName.length() == endOfFirstBit
				|| fieldName.length() >= endOfFirstBit + ALT_SEP_LEN
				&& fieldName.substring(endOfFirstBit, endOfFirstBit + ALT_SEP_LEN).equals(ALT_SEP);
	}

}
