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

import nl.inl.blacklab.search.indexstructure.ComplexFieldDesc;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.indexstructure.PropertyDesc;


/**
 * Some utility functions for dealing with complex field names.
 */
public class ComplexFieldUtil {

	public static final String FORWARD_INDEX_ID_BOOKKEEP_NAME = "fiid";

	private static final String CONTENT_ID_BOOKKEEP_NAME = "cid";

	private static final String LENGTH_TOKENS_BOOKKEEP_NAME = "length_tokens";

	private static final String DEFAULT_MAIN_PROP_NAME = "word";

	public static final String SENSITIVE_ALT_NAME = "s";

	private static final String DEFAULT_MAIN_ALT_NAME = SENSITIVE_ALT_NAME;

	public static final String INSENSITIVE_ALT_NAME = "i";

	public static final String CASE_INSENSITIVE_ALT_NAME = "ci";

	public static final String DIACRITICS_INSENSITIVE_ALT_NAME = "di";

	public static final String START_TAG_PROP_NAME = "starttag";

	public static final String END_TAG_PROP_NAME = "endtag";

	/** Property name for the spaces and punctuation between words */
	public static final String PUNCTUATION_PROP_NAME = "punct";

	/** Property name for lemma/headword (optional, not every input format will have this) */
	public static final String LEMMA_PROP_NAME = "lemma";

	/** Property name for part of speech (optional, not every input format will have this) */
	public static final String PART_OF_SPEECH_PROP_NAME = "pos";

	/**
	 * String used to separate the base field name (say, contents) and the field property (pos,
	 * lemma, etc.)
	 */
	static String PROP_SEP;

	/**
	 * String used to separate the field/property name (say, contents_lemma) and the alternative
	 * (e.g. "s" for case-sensitive)
	 */
	static String ALT_SEP;

	/**
	 * String used to separate the field/property name (say, contents_lemma) and the alternative
	 * (e.g. "s" for case-sensitive)
	 */
	static String BOOKKEEPING_SEP;

	/** Length of the ALT separator */
	static int ALT_SEP_LEN;

	/** Length of the PROP separator */
	static int PROP_SEP_LEN;

	/** Length of the BOOKKEEPING separator */
	static int BOOKKEEPING_SEP_LEN;

	static {
		// Default: use new field naming scheme.
		setFieldNameSeparators(false);
	}

	/** What are the names of the bookkeeping subfields (i.e. content id, forward index id, etc.) */
	private final static List<String> BOOKKEEPING_SUBFIELDS = Arrays.asList(
		CONTENT_ID_BOOKKEEP_NAME,
		FORWARD_INDEX_ID_BOOKKEEP_NAME,
		LENGTH_TOKENS_BOOKKEEP_NAME
	);

	public enum BookkeepFieldType {
		CONTENT_ID,
		FORWARD_INDEX_ID,
		LENGTH_TOKENS
	}

	public static boolean isBookkeepingSubfield(String bookkeepName) {
		return BOOKKEEPING_SUBFIELDS.contains(bookkeepName);
	}

	public static BookkeepFieldType whichBookkeepingSubfield(String bookkeepName) {
		switch(BOOKKEEPING_SUBFIELDS.indexOf(bookkeepName)) {
		case 0:
			return BookkeepFieldType.CONTENT_ID;
		case 1:
			return BookkeepFieldType.FORWARD_INDEX_ID;
		case 2:
			return BookkeepFieldType.LENGTH_TOKENS;
		default:
			throw new RuntimeException();
		}
	}

	public static String contentIdField(String fieldName) {
		return bookkeepingField(fieldName, CONTENT_ID_BOOKKEEP_NAME);
	}

	public static String forwardIndexIdField(String propFieldName) {
		return bookkeepingField(propFieldName, FORWARD_INDEX_ID_BOOKKEEP_NAME);
	}

	public static String forwardIndexIdField(IndexStructure structure, String fieldName) {
		String propName = structure.getComplexFieldDesc(fieldName).getMainProperty().getName();
		return forwardIndexIdField(propertyField(fieldName, propName));
	}

	public static String lengthTokensField(String fieldName) {
		return bookkeepingField(fieldName, LENGTH_TOKENS_BOOKKEEP_NAME);
	}

	public static String startTagPropertyField(String fieldName) {
		return propertyField(fieldName, START_TAG_PROP_NAME);
	}

	public static String endTagPropertyField(String fieldName) {
		return propertyField(fieldName, END_TAG_PROP_NAME);
	}

	/** Are we using the old field names?
	 * @return true if we are, false if not
	 * @deprecated always returns false
	 */
	@Deprecated
	public static boolean usingOldFieldNames() {
		return false; //oldFieldNames;
	}

	/** Set what field name separators to use.
	 * @param avoidSpecialChars if true, use only standard identifier characters for the separators. If false, use special chars %, @, #.
	 */
	public static void setFieldNameSeparators(boolean avoidSpecialChars) {
		if (avoidSpecialChars) {
			// Avoid using special characters in fieldnames, in case
			// this clashes with other Lucene-based software (such as e.g. Solr)
			PROP_SEP = "_PR_";
			ALT_SEP = "_AL_";
			BOOKKEEPING_SEP = "_BK_";
		} else {
			// Lucene doesn't have any restrictions on characters in field names;
			// use the short, symbolic ones.
			PROP_SEP = "%";
			ALT_SEP = "@";
			BOOKKEEPING_SEP = "#";
		}

		ALT_SEP_LEN = ALT_SEP.length();
		PROP_SEP_LEN = PROP_SEP.length();
		BOOKKEEPING_SEP_LEN = BOOKKEEPING_SEP.length();
	}

	/** Set what field name separators to use.
	 *
	 * Used for backwards compatibility; will eventually be removed.
	 *
	 * @param avoidSpecialChars if true, use only standard identifier characters for the separators. If false, use special chars %, @, #.
	 * @param oldVersion if true, use the old naming scheme.
	 * @deprecated use version that takes a single parameter
	 */
	@Deprecated
	public static void setFieldNameSeparators(boolean avoidSpecialChars, boolean oldVersion) {

		if (oldVersion) {
			throw new RuntimeException("Your index was created with an old BlackLab version and cannot be opened with this version. Please re-index your data, or use a BlackLab version from before August 2014.");
		}

		setFieldNameSeparators(avoidSpecialChars);
	}

	/**
	 * Construct a complex field bookkeeping subfield name.
	 *
	 * @param fieldName
	 *            the base field name
	 * @param propName
	 *            the property name, or null if this is bookkeeping for the whole complex field
	 * @param bookkeepName
	 *            name of the bookkeeping value
	 * @return the combined complex field name
	 */
	public static String bookkeepingField(String fieldName, String propName, String bookkeepName) {
		String fieldPropName = "";
		boolean propGiven = propName != null && propName.length() > 0;
		if (fieldName == null || fieldName.length() == 0) {
			if (propGiven) {
				fieldPropName = propName;
			} else
				throw new RuntimeException("Must specify a base name, a property name or both: " + fieldName + ", " + propName + ", " + bookkeepName);
		} else {
			fieldPropName = fieldName + (propGiven ? PROP_SEP + propName : "");
		}

		if (bookkeepName == null || bookkeepName.length() == 0)
			return fieldPropName;
		return fieldPropName + BOOKKEEPING_SEP + bookkeepName;
	}

	/**
	 * Construct a complex field bookkeeping subfield name.
	 *
	 * @param fieldName
	 *            the base field name
	 * @param bookkeepName
	 *            name of the bookkeeping value
	 * @return the combined complex field name
	 */
	public static String bookkeepingField(String fieldName, String bookkeepName) {
		return bookkeepingField(fieldName, null, bookkeepName);
	}

	/**
	 * Construct a complex field property name.
	 *
	 * @param fieldName
	 *            the base field name
	 * @param propName
	 *            the property name
	 * @param altName
	 *            the alternative name
	 * @return the combined complex field name
	 */
	public static String propertyField(String fieldName, String propName, String altName) {
		String fieldPropName = "";
		boolean propGiven = propName != null && propName.length() > 0;
		if (!propGiven) {
			throw new RuntimeException("Must specify a property name");
		}
		if (fieldName == null || fieldName.length() == 0) {
			if (propGiven) {
				fieldPropName = propName;
			} else
				throw new RuntimeException("Must specify a base name, a property name or both");
		} else {
			fieldPropName = fieldName + (propGiven ? PROP_SEP + propName : "");
		}

		if (altName == null || altName.length() == 0) {
			return fieldPropName;
		}
		return fieldPropName + ALT_SEP + altName;
	}

	/**
	 * Construct a complex field property name.
	 *
	 * @param fieldName
	 *            the base field name
	 * @param propName
	 *            the property name
	 * @return the combined complex field name
	 */
	public static String propertyField(String fieldName, String propName) {
		return propertyField(fieldName, propName, null);
	}

	/**
	 * Construct a property alternative name from a field property name
	 * @param fieldPropName the field property name
	 * @param altName the alternative name
	 * @return the field property alternative name
	 */
	public static String propertyAlternative(String fieldPropName, String altName) {
		if (altName == null || altName.length() == 0) {
			throw new RuntimeException("Must specify an alternative name");
		}
		return fieldPropName + ALT_SEP + altName;
	}

	/**
	 * Construct a complex field name from its component parts.
	 *
	 * @param fieldName
	 *            the base field name
	 * @param propName
	 *            the property name
	 * @return the combined complex field name
	 * @deprecated replace with either propertyField or bookkeepingField
	 */
	@Deprecated
	public static String fieldName(String fieldName, String propName) {
		return fieldName(fieldName, propName, null);
	}

	/**
	 * Construct a complex field name from its component parts.
	 *
	 * @param fieldName
	 *            the base field name
	 * @param propName
	 *            the property name
	 * @param altName
	 *            the alternative name
	 * @return the combined complex field name
	 * @deprecated replace with either propertyField or bookkeepingField
	 */
	@Deprecated
	public static String fieldName(String fieldName, String propName, String altName) {
		return propertyField(fieldName, propName, altName);
	}

	/**
	 * Split a complex field name into its component parts: base name, property name, and
	 * alternative. The last two are both optional; either or both may be present.
	 *
	 * Example: the name "contents%lemma@s" will be split into "contents", "lemma" and "s".
	 *
	 * @param fieldPropAltName
	 *            the field name
	 * @return the component parts base name, property name and alternative. Missing parts will be
	 *         empty strings.
	 * @deprecated Not used anywhere, superseded by getNameComponents()
	 */
	@Deprecated
	public static String[] split(String fieldPropAltName) {
		int p = fieldPropAltName.indexOf(PROP_SEP);
		int a = fieldPropAltName.indexOf(ALT_SEP);
		if (p < 0) {
			// No property
			if (a < 0) {
				// No alternative
				return new String[] { fieldPropAltName, "", "" };
			}
			return new String[] { fieldPropAltName.substring(0, a), "",
					fieldPropAltName.substring(a + ALT_SEP_LEN) };
		}

		// Property
		if (a < 0) {
			// No alternative
			return new String[] { fieldPropAltName.substring(0, p), fieldPropAltName.substring(p + PROP_SEP_LEN),
					"" };
		}

		// Alternative
		if (p > a)
			throw new RuntimeException("Malformed field name, PROP separator after ALT separator");

		return new String[] { fieldPropAltName.substring(0, p), fieldPropAltName.substring(p + PROP_SEP_LEN, a),
				fieldPropAltName.substring(a + ALT_SEP_LEN) };
	}

	/**
	 * Gets the different components of a complex field property (alternative) name.
	 *
	 * @param luceneFieldName
	 *   the Lucene index field name, with possibly a property and/or alternative added
	 * @return an array of size 1-4, containing the field name, and optionally the property name,
	 *   alternative name and bookkeeping field name.
	 *
	 *  Property name may be null if this is a main bookkeeping field.
	 *  Alternative name may be null if this is a bookkeeping field or if it indicates
	 *  the main property (not an alternative).
	 */
	public static String[] getNameComponents(String luceneFieldName) {

		/*
		Field names can be one of the following:

		(1) Property:              base%prop (e.g. word, lemma, pos)
		(2) Main bookkeeping:      base#cid
		(3) Property bookkeeping:  base%prop#fiid
		(4) Property alternative:  base%prop@alt

		(Old style, now unsupported:)

		(A) Main property:          base       (implicitly, "word")
		(1) Additional property:    base__prop (e.g. lemma, pos)
		(B) Main bookkeeping:       base__fiid
		(3) Property bookkeeping:   base__prop__fiid
		(4) Property alternative:   base__prop_ALT_alt
		(C) Main prop alternative:  base_ALT_alt
		*/

		String baseName, propName, altName, bookkeepingName;

		int propSepPos = luceneFieldName.indexOf(PROP_SEP);
		int altSepPos = luceneFieldName.indexOf(ALT_SEP);
		int bookkeepingSepPos;
//		if (oldFieldNames && propSepPos >= 0)
//			bookkeepingSepPos = luceneFieldName.indexOf(BOOKKEEPING_SEP, propSepPos + 1);
//		else
			bookkeepingSepPos = luceneFieldName.indexOf(BOOKKEEPING_SEP);

		// Strip off property and possible alternative
		if (propSepPos >= 0) {
			// Property given (1/3/4)
			baseName = luceneFieldName.substring(0, propSepPos);
			int afterPropSepPos = propSepPos + PROP_SEP_LEN;

			if (altSepPos >= 0) {
				// Property and alternative given (4)
				propName = luceneFieldName.substring(afterPropSepPos, altSepPos);
				altName = luceneFieldName.substring(altSepPos + ALT_SEP_LEN);
				return new String[] {baseName, propName, altName};
			}

			// Maybe it's a bookkeeping field?
			if (bookkeepingSepPos >= 0 && bookkeepingSepPos > propSepPos) {
				// Property plus bookkeeping subfield given. (3)
				propName = luceneFieldName.substring(afterPropSepPos, bookkeepingSepPos);
				bookkeepingName = luceneFieldName.substring(bookkeepingSepPos + BOOKKEEPING_SEP_LEN);
				return new String[] {baseName, propName, null, bookkeepingName};
			}

			// Plain property, no alternative or bookkeeping (1)
			propName = luceneFieldName.substring(afterPropSepPos);
			return new String[] {baseName, propName};
		}

		// No property given. Alternative?
		if (altSepPos >= 0) {
			throw new RuntimeException("Basename+altname is not a valid field name! (" + luceneFieldName + ")");
		}

		if (bookkeepingSepPos >= 0) {
			// Main bookkeeping field (2)
			baseName = luceneFieldName.substring(0, bookkeepingSepPos);
			altName = luceneFieldName.substring(bookkeepingSepPos + BOOKKEEPING_SEP_LEN);
			return new String[] {baseName, null, null, altName};
		}

		// Just the base name given (A).
		// This means it's a metadata field.
		return new String[] {luceneFieldName};
	}

	/**
	 * Gets the base complex field name from a Lucene index field name. So "contents" and
	 * "contents%pos" would both yield "contents".
	 *
	 * @param luceneFieldName
	 *            the Lucene index field name, with possibly a property added
	 * @return the base complex field name
	 */
	public static String getBaseName(String luceneFieldName) {
		// Strip off property and possible alternative
		int pos = luceneFieldName.indexOf(PROP_SEP);
		if (pos >= 0) {
			return luceneFieldName.substring(0, pos);
		}
		pos = luceneFieldName.indexOf(BOOKKEEPING_SEP);
		if (pos >= 0) {
			return luceneFieldName.substring(0, pos);
		}
		pos = luceneFieldName.indexOf(ALT_SEP);
		if (pos >= 0) {
			throw new RuntimeException("Illegal field name: " + luceneFieldName);
		}
		return luceneFieldName;
	}

	/**
	 * Get the "alternative" part of the complex field property. An alternative of a property is,
	 * for example, a case-sensitive version of the property. For the alternatives "contents@s"
	 * or "contents%lemma@s" this method produces "s".
	 *
	 * @param fieldPropAltName
	 *            the field plus property plus alternative name we want to get the alternative from
	 * @return the alternative name
	 * @deprecated use getNameComponents
	 */
	@Deprecated
	public static String getAlternative(String fieldPropAltName) {
		int pos = fieldPropAltName.lastIndexOf(ALT_SEP);
		if (pos < 0)
			return "";
		return fieldPropAltName.substring(pos + ALT_SEP_LEN);
	}

	/**
	 * Get the "property" part of the complex field property. For the field name "contents_lemma" or
	 * "contents%lemma@s" this method produces "lemma".
	 *
	 * @param fieldPropAltName
	 *            the field plus property plus alternative name we want to get the alternative from
	 * @return the alternative name
	 * @deprecated use getNameComponents
	 */
	@Deprecated
	public static String getProperty(String fieldPropAltName) {
		int pos = fieldPropAltName.indexOf(PROP_SEP);
		if (pos < 0)
			return ""; // No property

		// See if there's an alternative we need to get rid of
		int posA = fieldPropAltName.lastIndexOf(ALT_SEP);
		if (posA < 0)
			return fieldPropAltName.substring(pos + PROP_SEP_LEN);
		return fieldPropAltName.substring(pos + PROP_SEP_LEN, posA); // Strip off alternative
	}

	/**
	 * Checks if the given fieldName actually points to an alternative property (for example, a
	 * case-sensitive version of a property).
	 *
	 * Example: the fieldName "contents%lemma@s" indicates the "s" alternative of the "lemma"
	 * property of the "contents" complex field.
	 *
	 * @param fieldPropAltName
	 *            the full fieldname, possibly including a property and/or alternative
	 * @param altName
	 *            the alternative to test for
	 * @return true if the fieldName indicates the specified alternative
	 */
	public static boolean isAlternative(String fieldPropAltName, String altName) {
		if (altName.length() == 0) {
			// No alternative, therefore no alternative separator
			return fieldPropAltName.indexOf(ALT_SEP) < 0;
		}

		// We're looking for an alternative
		String altSuffix = ALT_SEP + altName;
		return fieldPropAltName.endsWith(altSuffix);
	}

	/**
	 * Checks if the given fieldName actually points to the specified property (for example, the
	 * lemma or the POS of the original token).
	 *
	 * Example: the fieldName "contents%lemma" indicates the "lemma" property of the "contents"
	 * complex field.
	 *
	 * Field names may also include an "alternative", for example a case-sensitive version. For
	 * example, "contents%lemma@s" is the "s" alternative of the "lemma" property. However,
	 * alternatives are not tested for in this method, just the property name.
	 *
	 * @param fieldPropAltName
	 *            the full fieldname, possibly including a property and/or alternative
	 * @param propName
	 *            the property to test for
	 * @return true if the fieldName indicates the specified alternative
	 * @deprecated use getNameComponents()
	 */
	@Deprecated
	public static boolean isProperty(String fieldPropAltName, String propName) {
		if (propName.length() == 0) {
			// No property, therefore no property separator
			return fieldPropAltName.indexOf(PROP_SEP) < 0;
		}

		// We're looking for a property, either at the end of the fieldName or right before the ALT
		// separator
		String firstBit = PROP_SEP + propName;
		int pos = fieldPropAltName.indexOf(firstBit);
		if (pos < 0)
			return false; // Not found
		int endOfFirstBit = pos + firstBit.length();

		// Should occur at the end, or should be followed by ALT separator
		return fieldPropAltName.length() == endOfFirstBit
				|| fieldPropAltName.length() >= endOfFirstBit + ALT_SEP_LEN
				&& fieldPropAltName.substring(endOfFirstBit, endOfFirstBit + ALT_SEP_LEN).equals(ALT_SEP);
	}

	public static String mainPropertyField(IndexStructure structure, String fieldName) {
		ComplexFieldDesc cf = structure.getComplexFieldDesc(fieldName);
		PropertyDesc pr = cf.getMainProperty();
		return propertyField(fieldName, pr.getName());
	}

	public static String mainPropertyOffsetsField(IndexStructure structure, String fieldName) {
		ComplexFieldDesc cf = structure.getComplexFieldDesc(fieldName);
		PropertyDesc pr = cf.getMainProperty();
		return propertyField(fieldName, pr.getName(), pr.offsetsAlternative());
	}

	public static String getDefaultMainPropName() {
		return DEFAULT_MAIN_PROP_NAME;
	}

	/**
	 * @return false
	 * @deprecated main property can't be nameless anymore; always returns false
	 */
	@Deprecated
	public static boolean isMainPropertyNameless() {
		return false;
	}

	public static String getDefaultMainAlternativeName() {
		return DEFAULT_MAIN_ALT_NAME;
	}

	/**
	 * @return false
	 * @deprecated alternatives can't be nameless anymore; always returns false
	 */
	@Deprecated
	public static boolean isMainAlternativeNameless() {
		return false;
	}

	/**
	 * Are we using the naming scheme with longer separation codes with no special characters in them?
	 * @return true if we are, false if not
	 */
	public static boolean avoidSpecialCharsInFieldNames() {
		return PROP_SEP_LEN > 1;
	}

	/**
	 * Does this Lucene field name refer to a case-sensitive alternative?
	 *
	 * Case-sensitive alternatives are "s" (case- and diacritics-sensitive)
	 * and "di" (diacritics-insensitive but case-sensitive).
	 *
	 * @param fieldPropAltName Lucene field name including property and alt name
	 * @return true if the field name refers to a case-sensitive alternative
	 */
	public static boolean isCaseSensitive(String fieldPropAltName) {
		// both-sensitive or diacritics-insensitive
		return fieldPropAltName.endsWith(ALT_SEP + "s") || fieldPropAltName.endsWith(ALT_SEP + "di");
	}

	/**
	 * Does this Lucene field name refer to a diacritics-sensitive alternative?
	 *
	 * Diacritics-sensitive alternatives are "s" (case- and diacritics-sensitive)
	 * and "ci" (case-insensitive but diacritics-sensitive).
	 *
	 * @param fieldPropAltName Lucene field name including property and alt name
	 * @return true if the field name refers to a diacritics-sensitive alternative
	 */
	public static boolean isDiacriticsSensitive(String fieldPropAltName) {
		// both-sensitive or case-insensitive
		return fieldPropAltName.endsWith(ALT_SEP + "s") || fieldPropAltName.endsWith(ALT_SEP + "ci");
	}


}
