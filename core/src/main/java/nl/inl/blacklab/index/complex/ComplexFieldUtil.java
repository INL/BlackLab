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
import java.util.regex.Pattern;

import nl.inl.blacklab.search.indexmetadata.nint.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.nint.Annotation;
import nl.inl.blacklab.search.indexmetadata.nint.IndexMetadata;

/**
 * Some utility functions for dealing with complex field names.
 */
public final class ComplexFieldUtil {

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

    public static final String WORD_PROP_NAME = "word";

    /** Property name for the spaces and punctuation between words */
    public static final String PUNCTUATION_PROP_NAME = "punct";

    /**
     * Property name for lemma/headword (optional, not every input format will have
     * this)
     */
    public static final String LEMMA_PROP_NAME = "lemma";

    /**
     * Property name for part of speech (optional, not every input format will have
     * this)
     */
    public static final String PART_OF_SPEECH_PROP_NAME = "pos";

    /**
     * For properties combined in a single Lucene field, this is the separator
     * between the name prefix of an indexed value and the actual value of the
     * property
     */
    public static final String SUBPROPERTY_SEPARATOR = "\u001F";

    /**
     * Valid XML element names. Field and property names should generally conform to
     * this.
     */
    static final Pattern REGEX_VALID_XML_ELEMENT_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z0-9\\-_\\.]*");

    /**
     * String used to separate the base field name (say, contents) and the field
     * property (pos, lemma, etc.)
     */
    static final String PROP_SEP;

    /**
     * String used to separate the field/property name (say, contents_lemma) and the
     * alternative (e.g. "s" for case-sensitive)
     */
    static final String ALT_SEP;

    /**
     * String used to separate the field/property name (say, contents_lemma) and the
     * alternative (e.g. "s" for case-sensitive)
     */
    static final String BOOKKEEPING_SEP;

    /** Length of the ALT separator */
    static final int ALT_SEP_LEN;

    /** Length of the PROP separator */
    static final int PROP_SEP_LEN;

    /** Length of the BOOKKEEPING separator */
    static final int BOOKKEEPING_SEP_LEN;

    static {
        // Lucene doesn't have any restrictions on characters in field names;
        // use the short, symbolic ones.
        PROP_SEP = "%";
        ALT_SEP = "@";
        BOOKKEEPING_SEP = "#";
        ALT_SEP_LEN = ALT_SEP.length();
        PROP_SEP_LEN = PROP_SEP.length();
        BOOKKEEPING_SEP_LEN = BOOKKEEPING_SEP.length();
    }

    /**
     * What are the names of the bookkeeping subfields (i.e. content id, forward
     * index id, etc.)
     */
    private final static List<String> BOOKKEEPING_SUBFIELDS = Arrays.asList(
            CONTENT_ID_BOOKKEEP_NAME,
            FORWARD_INDEX_ID_BOOKKEEP_NAME,
            LENGTH_TOKENS_BOOKKEEP_NAME);

    private ComplexFieldUtil() {
    }

    public enum BookkeepFieldType {
        CONTENT_ID,
        FORWARD_INDEX_ID,
        LENGTH_TOKENS
    }

    public static boolean isBookkeepingSubfield(String bookkeepName) {
        return BOOKKEEPING_SUBFIELDS.contains(bookkeepName);
    }

    public static BookkeepFieldType whichBookkeepingSubfield(String bookkeepName) {
        switch (BOOKKEEPING_SUBFIELDS.indexOf(bookkeepName)) {
        case 0:
            return BookkeepFieldType.CONTENT_ID;
        case 1:
            return BookkeepFieldType.FORWARD_INDEX_ID;
        case 2:
            return BookkeepFieldType.LENGTH_TOKENS;
        default:
            throw new IllegalArgumentException("Unknown bookkeeping field: " + bookkeepName);
        }
    }

    public static String contentIdField(String fieldName) {
        return bookkeepingField(fieldName, CONTENT_ID_BOOKKEEP_NAME);
    }

    public static String forwardIndexIdField(String propFieldName) {
        return bookkeepingField(propFieldName, FORWARD_INDEX_ID_BOOKKEEP_NAME);
    }

    public static String forwardIndexIdField(IndexMetadata structure, String fieldName) {
        String propName = structure.annotatedFields().field(fieldName).annotations().main().name();
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

    /**
     * Construct a complex field bookkeeping subfield name.
     *
     * @param fieldName the base field name
     * @param propName the property name, or null if this is bookkeeping for the
     *            whole complex field
     * @param bookkeepName name of the bookkeeping value
     * @return the combined complex field name
     */
    public static String bookkeepingField(String fieldName, String propName, String bookkeepName) {
        String fieldPropName;
        boolean propGiven = propName != null && propName.length() > 0;
        if (fieldName == null || fieldName.length() == 0) {
            if (propGiven) {
                fieldPropName = propName;
            } else
                throw new IllegalArgumentException("Must specify a base name, a property name or both: " + fieldName
                        + ", " + propName + ", " + bookkeepName);
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
     * @param fieldName the base field name
     * @param bookkeepName name of the bookkeeping value
     * @return the combined complex field name
     */
    public static String bookkeepingField(String fieldName, String bookkeepName) {
        return bookkeepingField(fieldName, null, bookkeepName);
    }

    /**
     * Construct a complex field property name.
     *
     * @param fieldName the base field name
     * @param propName the property name
     * @param altName the alternative name
     * @return the combined complex field name
     */
    public static String propertyField(String fieldName, String propName, String altName) {
        String fieldPropName;
        boolean propGiven = propName != null && propName.length() > 0;
        if (!propGiven) {
            throw new IllegalArgumentException("Must specify a property name");
        }
        if (fieldName == null || fieldName.length() == 0) {
            fieldPropName = propName;
        } else {
            fieldPropName = fieldName + PROP_SEP + propName;
        }

        if (altName == null || altName.length() == 0) {
            return fieldPropName;
        }
        return fieldPropName + ALT_SEP + altName;
    }

    /**
     * Construct a complex field property name.
     *
     * @param fieldName the base field name
     * @param propName the property name
     * @return the combined complex field name
     */
    public static String propertyField(String fieldName, String propName) {
        return propertyField(fieldName, propName, null);
    }

    /**
     * Construct a property alternative name from a field property name
     * 
     * @param fieldPropName the field property name
     * @param altName the alternative name
     * @return the field property alternative name
     */
    public static String propertyAlternative(String fieldPropName, String altName) {
        if (altName == null || altName.length() == 0) {
            throw new IllegalArgumentException("Must specify an alternative name");
        }
        return fieldPropName + ALT_SEP + altName;
    }

    /**
     * Gets the different components of a complex field property (alternative) name.
     *
     * @param luceneFieldName the Lucene index field name, with possibly a property
     *            and/or alternative added
     * @return an array of size 1-4, containing the field name, and optionally the
     *         property name, alternative name and bookkeeping field name.
     *
     *         Property name may be null if this is a main bookkeeping field.
     *         Alternative name may be null if this is a bookkeeping field or if it
     *         indicates the main property (not an alternative).
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
                return new String[] { baseName, propName, altName };
            }

            // Maybe it's a bookkeeping field?
            if (bookkeepingSepPos >= 0 && bookkeepingSepPos > propSepPos) {
                // Property plus bookkeeping subfield given. (3)
                propName = luceneFieldName.substring(afterPropSepPos, bookkeepingSepPos);
                bookkeepingName = luceneFieldName.substring(bookkeepingSepPos + BOOKKEEPING_SEP_LEN);
                return new String[] { baseName, propName, null, bookkeepingName };
            }

            // Plain property, no alternative or bookkeeping (1)
            propName = luceneFieldName.substring(afterPropSepPos);
            return new String[] { baseName, propName };
        }

        // No property given. Alternative?
        if (altSepPos >= 0) {
            throw new IllegalArgumentException("Basename+altname is not a valid field name! (" + luceneFieldName + ")");
        }

        if (bookkeepingSepPos >= 0) {
            // Main bookkeeping field (2)
            baseName = luceneFieldName.substring(0, bookkeepingSepPos);
            altName = luceneFieldName.substring(bookkeepingSepPos + BOOKKEEPING_SEP_LEN);
            return new String[] { baseName, null, null, altName };
        }

        // Just the base name given (A).
        // This means it's a metadata field.
        return new String[] { luceneFieldName };
    }

    /**
     * Gets the base complex field name from a Lucene index field name. So
     * "contents" and "contents%pos" would both yield "contents".
     *
     * @param luceneFieldName the Lucene index field name, with possibly a property
     *            added
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
            throw new IllegalArgumentException("Illegal field name: " + luceneFieldName);
        }
        return luceneFieldName;
    }

    /**
     * Checks if the given fieldName actually points to an alternative property (for
     * example, a case-sensitive version of a property).
     *
     * Example: the fieldName "contents%lemma@s" indicates the "s" alternative of
     * the "lemma" property of the "contents" complex field.
     *
     * @param fieldPropAltName the full fieldname, possibly including a property
     *            and/or alternative
     * @param altName the alternative to test for
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

    public static String mainPropertyField(IndexMetadata structure, String fieldName) {
        AnnotatedField cf = structure.annotatedFields().field(fieldName);
        Annotation pr = cf.annotations().main();
        return propertyField(fieldName, pr.name());
    }

    public static String mainPropertyOffsetsField(IndexMetadata structure, String fieldName) {
        AnnotatedField cf = structure.annotatedFields().field(fieldName);
        Annotation pr = cf.annotations().main();
        return pr.offsetsSensitivity().luceneField();
    }

    public static String getDefaultMainPropName() {
        return DEFAULT_MAIN_PROP_NAME;
    }

    public static String getDefaultMainAlternativeName() {
        return DEFAULT_MAIN_ALT_NAME;
    }

    /**
     * Are we using the naming scheme with longer separation codes with no special
     * characters in them?
     * 
     * @return true if we are, false if not
     */
    public static boolean avoidSpecialCharsInFieldNames() {
        return PROP_SEP_LEN > 1;
    }

    /**
     * Does this Lucene field name refer to a case-sensitive alternative?
     *
     * Case-sensitive alternatives are "s" (case- and diacritics-sensitive) and "di"
     * (diacritics-insensitive but case-sensitive).
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

    /**
     * Is the specified name a valid XML element name?
     * 
     * Generally, field and property names should be valid XML element names, so we
     * don't have to sanitize them when generating output XML.
     * 
     * @param name name to check
     * @return true iff it's a valid XML element name
     */
    public static boolean isValidXmlElementName(String name) {
        return REGEX_VALID_XML_ELEMENT_NAME.matcher(name).matches();
    }

}
