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

import java.util.Arrays;
import java.util.List;


/**
 * Some utility functions for dealing with complex field names.
 */
public class ComplexFieldUtil {

	/** Use the old field naming scheme?
	 * (for compatibility with old indices; will be removed eventually) */
	private static boolean oldFieldNames = true;

	public static final String FORWARD_INDEX_ID_FIELD_NAME = "fiid";

	public static final String CONTENT_ID_FIELD_NAME = "cid";

	public static final String LENGTH_TOKENS_FIELD_NAME = "length_tokens";

	public static final String DEFAULT_MAIN_PROP_NAME = "word";

	public static final String START_TAG_PROP_NAME = "starttag";

	public static final String END_TAG_PROP_NAME = "endtag";

	/** What are the names of the bookkeeping subfields (i.e. content id, forward index id, etc.) */
	public final static List<String> BOOKKEEPING_SUBFIELDS = Arrays.asList(
		CONTENT_ID_FIELD_NAME,
		FORWARD_INDEX_ID_FIELD_NAME,
		LENGTH_TOKENS_FIELD_NAME
	);

	public static String contentIdField(String baseName) {
		return bookkeepingField(baseName, CONTENT_ID_FIELD_NAME);
	}

	public static String forwardIndexIdField(String baseName) {
		return bookkeepingField(baseName, FORWARD_INDEX_ID_FIELD_NAME);
	}

	public static String lengthTokensField(String baseName) {
		return bookkeepingField(baseName, LENGTH_TOKENS_FIELD_NAME);
	}

	public static String startTagPropertyField(String baseName) {
		return propertyField(baseName, START_TAG_PROP_NAME);
	}

	public static String endTagPropertyField(String baseName) {
		return propertyField(baseName, END_TAG_PROP_NAME);
	}

	/** Are we using the old field names? */
	public static boolean usingOldFieldNames() {
		return oldFieldNames;
	}

	/** Set what field name separators to use.
	 * @param noSpecialChars if true, use only standard identifier characters for the separators. If false, use special chars %, @, #.
	 */
	public static void setFieldNameSeparators(boolean noSpecialChars) {
		setFieldNameSeparators(noSpecialChars, false);
	}

	/** Set what field name separators to use.
	 * @param avoidSpecialChars if true, use only standard identifier characters for the separators. If false, use special chars %, @, #.
	 * @param oldVersion if true, use the old naming scheme.
	 */
	public static void setFieldNameSeparators(boolean avoidSpecialChars, boolean oldVersion) {
		oldFieldNames = oldVersion;
		if (oldFieldNames) {
			PROP_SEP = "__";
			ALT_SEP = "_ALT_";
			BOOKKEEPING_SEP = PROP_SEP;
			MAIN_PROPERTY_NAMELESS = true;
		} else {
			if (avoidSpecialChars) {
				// Avoid using special characters in fieldnames, in case
				// this clashes with other Lucene-based software (such as e.g. Solr)
				PROP_SEP = "_PR_";
				ALT_SEP = "_AL_";
				BOOKKEEPING_SEP = "_BK_";
			} else {
				// Lucene doesn't have any restrictions on characters in field names;
				// use the short ones.
				PROP_SEP = "%";
				ALT_SEP = "@";
				BOOKKEEPING_SEP = "#";
			}
			MAIN_PROPERTY_NAMELESS = true; // TODO: make this work with value false!
		}
		ALT_SEP_LEN = ALT_SEP.length();
		PROP_SEP_LEN = PROP_SEP.length();
		BOOKKEEPING_SEP_LEN = BOOKKEEPING_SEP.length();
	}

	/**
	 * Is the main property of the field (the one containing word forms and character positions)
	 * nameless, or does it have a property name like the other properties (e.g. "word" or "wf")?
	 * In the old scheme, they were nameless, in the new scheme not.
	 */
	public static boolean MAIN_PROPERTY_NAMELESS;

	/**
	 * String used to separate the base field name (say, contents) and the field property (pos,
	 * lemma, etc.)
	 */
	public static String PROP_SEP;

	/**
	 * String used to separate the field/property name (say, contents_lemma) and the alternative
	 * (e.g. "s" for case-sensitive)
	 */
	public static String ALT_SEP;

	/**
	 * String used to separate the field/property name (say, contents_lemma) and the alternative
	 * (e.g. "s" for case-sensitive)
	 */
	public static String BOOKKEEPING_SEP;

	/** Length of the ALT separator */
	static int ALT_SEP_LEN;

	/** Length of the PROP separator */
	static int PROP_SEP_LEN;

	/** Length of the BOOKKEEPING separator */
	static int BOOKKEEPING_SEP_LEN;

	static {
		// Default: use old field naming scheme
		setFieldNameSeparators(true, true);
	}

	/**
	 * Construct a complex field bookkeeping subfield name.
	 *
	 * @param baseName
	 *            the base field name
	 * @param propertyName
	 *            the property name, or null if this is bookkeeping for the whole complex field
	 * @param bookkeeping
	 *            name of the bookkeeping value
	 * @return the combined complex field name
	 */
	public static String bookkeepingField(String baseName, String propertyName, String bookkeeping) {
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

		if (bookkeeping == null || bookkeeping.length() == 0)
			return baseAndProp;
		return baseAndProp + BOOKKEEPING_SEP + bookkeeping;
	}

	/**
	 * Construct a complex field bookkeeping subfield name.
	 *
	 * @param baseName
	 *            the base field name
	 * @param bookkeeping
	 *            name of the bookkeeping value
	 * @return the combined complex field name
	 */
	public static String bookkeepingField(String baseName, String bookkeeping) {
		return bookkeepingField(baseName, null, bookkeeping);
	}

	/**
	 * Construct a complex field property name.
	 *
	 * @param baseName
	 *            the base field name
	 * @param propertyName
	 *            the property name
	 * @param alternative
	 *            the alternative name
	 * @return the combined complex field name
	 */
	public static String propertyField(String baseName, String propertyName, String alternative) {
		String baseAndProp = "";
		boolean propGiven = propertyName != null && propertyName.length() > 0;
		if (!propGiven && !MAIN_PROPERTY_NAMELESS)
			throw new RuntimeException("Must specify a property name");
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
	 * Construct a complex field property name.
	 *
	 * @param baseName
	 *            the base field name
	 * @param propertyName
	 *            the property name
	 * @return the combined complex field name
	 */
	public static String propertyField(String baseName, String propertyName) {
		return propertyField(baseName, propertyName, null);
	}

	/**
	 * Construct a complex field name from its component parts.
	 *
	 * @param baseName
	 *            the base field name
	 * @param propertyName
	 *            the property name
	 * @return the combined complex field name
	 * @deprecated replace with either propertyField or bookkeepingField
	 */
	@Deprecated
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
	 * @deprecated replace with either propertyField or bookkeepingField
	 */
	@Deprecated
	public static String fieldName(String baseName, String propertyName, String alternative) {
		return propertyField(baseName, propertyName, alternative);
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
	 * @deprecated Not used anywhere, superseded by getNameComponents()
	 */
	@Deprecated
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
	 * Gets the different components of a complex field property (alternative) name.
	 *
	 * @param fieldName
	 *   the Lucene index field name, with possibly a property and/or alternative added
	 * @return an array of size 1-4, containing the field name, and optionally the property name,
	 *   alternative name and bookkeeping field name.
	 *
	 *  Property name may be null if this is a main bookkeeping field.
	 *  Alternative name may be null if this is a bookkeeping field or if it indicates
	 *  the main property (not an alternative).
	 */
	public static String[] getNameComponents(String fieldName) {

		/*
		Field names can be one of the following:

		(1) Property:              base%prop (e.g. word, lemma, pos)
		(2) Main bookkeeping:      base#cid
		(3) Property bookkeeping:  base%prop#fiid
		(4) Property alternative:  base%prop@alt

		Old style:

		(A) Main property:          base       (implicitly, "word")
		(1) Additional property:    base__prop (e.g. lemma, pos)
		(B) Main bookkeeping:       base__fiid
		(3) Property bookkeeping:   base__prop__fiid
		(4) Property alternative:   base__prop_ALT_alt
		(C) Main prop alternative:  base_ALT_alt
		*/

		String baseName, propName, altName, bookkeepingName;

		int propSepPos = fieldName.indexOf(PROP_SEP);
		int altSepPos = fieldName.indexOf(ALT_SEP);
		int bookkeepingSepPos = oldFieldNames && propSepPos >= 0 ? fieldName.indexOf(BOOKKEEPING_SEP, propSepPos + 1) : fieldName.indexOf(BOOKKEEPING_SEP);

		// Strip off property and possible alternative
		if (propSepPos >= 0) {
			// Property given (1/3/4)
			baseName = fieldName.substring(0, propSepPos);
			int afterPropSepPos = propSepPos + PROP_SEP_LEN;

			if (altSepPos >= 0) {
				// Property and alternative given (4)
				propName = fieldName.substring(afterPropSepPos, altSepPos);
				altName = fieldName.substring(altSepPos + ALT_SEP_LEN);
				return new String[] {baseName, propName, altName};
			}

			// Maybe it's a bookkeeping field?
			if (bookkeepingSepPos >= 0 && bookkeepingSepPos > propSepPos) {
				// Property plus bookkeeping subfield given. (3)
				propName = fieldName.substring(afterPropSepPos, bookkeepingSepPos);
				bookkeepingName = fieldName.substring(bookkeepingSepPos + BOOKKEEPING_SEP_LEN);
				return new String[] {baseName, propName, null, bookkeepingName};
			}

			// Plain property, no alternative or bookkeeping (1)
			// NOTE: if old style, it might be bookkeeping anyway! Check for this
			propName = fieldName.substring(afterPropSepPos);
			if (oldFieldNames) {
				if (BOOKKEEPING_SUBFIELDS.contains(propName)) {
					// Old-style main bookkeeping field (B)
					return new String[] {baseName, null, null, propName};
				}
			}
			return new String[] {baseName, propName};
		}

		// No property given. Alternative?
		if (altSepPos >= 0) {
			// Old-style basename+altname (C)
			if (!MAIN_PROPERTY_NAMELESS)
				throw new RuntimeException("Basename+altname is not a valid field name! (" + fieldName + ")");
			baseName = fieldName.substring(0, altSepPos);
			altName = fieldName.substring(altSepPos + ALT_SEP_LEN);
			return new String[] {baseName, "", altName};
		}

		if (bookkeepingSepPos >= 0) {
			// Main bookkeeping field (2)
			baseName = fieldName.substring(0, bookkeepingSepPos);
			altName = fieldName.substring(bookkeepingSepPos + BOOKKEEPING_SEP_LEN);
			return new String[] {baseName, null, null, altName};
		}

		// Just the base name given (A).
		// This means it's either a metadata field, or it's an old-style
		// nameless main property.
		return new String[] {fieldName};
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
		pos = fieldName.indexOf(BOOKKEEPING_SEP);
		if (pos >= 0) {
			return fieldName.substring(0, pos);
		}
		pos = fieldName.indexOf(ALT_SEP);
		if (pos >= 0) {
			if (!MAIN_PROPERTY_NAMELESS)
				throw new RuntimeException("Illegal field name: " + fieldName);
			return fieldName.substring(0, pos);
		}
		if (!MAIN_PROPERTY_NAMELESS)
			throw new RuntimeException("Already a base name: " + fieldName);
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
	 * @deprecated use getNameComponents
	 */
	@Deprecated
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
	 * @deprecated use getNameComponents
	 */
	@Deprecated
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
		return fieldName.endsWith(altSuffix);
//		int pos = fieldName.indexOf(altSuffix);
//		if (pos < 0)
//			return false; // Not found
//
//		// Should occur at the end
//		return pos + altSuffix.length() == fieldName.length();
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
	 * @deprecated use getNameComponents()
	 */
	@Deprecated
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

	public static String mainPropertyField(String fieldName) {
		return propertyField(fieldName, MAIN_PROPERTY_NAMELESS ? "" : DEFAULT_MAIN_PROP_NAME);
	}

	public static String mainPropLuceneName() {
		return MAIN_PROPERTY_NAMELESS ? "" : DEFAULT_MAIN_PROP_NAME;
	}

}
