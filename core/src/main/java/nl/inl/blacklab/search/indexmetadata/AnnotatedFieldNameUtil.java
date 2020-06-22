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
package nl.inl.blacklab.search.indexmetadata;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Some utility functions for dealing with annotated field names.
 */
public final class AnnotatedFieldNameUtil {

    public static final String FORWARD_INDEX_ID_BOOKKEEP_NAME = "fiid";

    private static final String CONTENT_ID_BOOKKEEP_NAME = "cid";

    private static final String LENGTH_TOKENS_BOOKKEEP_NAME = "length_tokens";

    private static final String DEFAULT_MAIN_ANNOT_NAME = "word";

    public static final String TAGS_ANNOT_NAME = "starttag";

    public static final String WORD_ANNOT_NAME = "word";

    /** Annotation name for the spaces and punctuation between words */
    public static final String PUNCTUATION_ANNOT_NAME = "punct";

    /**
     * Annotation name for lemma/headword (optional, not every input format will have
     * this)
     */
    public static final String LEMMA_ANNOT_NAME = "lemma";

    /**
     * Subannotations are indexed in a separate field, prefixed with their main
     * annotation name, so a subannotation of "pos" named "number" will be indexed
     * in a field "pos_number". This is the separator used for this prefix.
     */
    public static final String SUBANNOTATION_FIELD_PREFIX_SEPARATOR = "_";
    
    /**
     * For annotations combined in a single Lucene field (the old way of indexing
     * subannotations), this was the separator between the name prefix of an indexed 
     * value and the actual value of the annotation
     */
    public static final String SUBANNOTATION_SEPARATOR = "\u001F";

    /**
     * Valid XML element names. Field and annotation names should generally conform to
     * this.
     */
    private static final Pattern REGEX_VALID_XML_ELEMENT_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z0-9\\-_\\.]*");

    /**
     * String used to separate the base field name (say, contents) and the field
     * annotation (pos, lemma, etc.)
     */
    static final String ANNOT_SEP;

    /**
     * String used to separate the field/annotation name (say, contents_lemma) and the
     * alternative (e.g. "s" for case-sensitive)
     */
    static final String SENSITIVITY_SEP;

    /**
     * String used to separate the field/annotation name (say, contents_lemma) and the
     * alternative (e.g. "s" for case-sensitive)
     */
    static final String BOOKKEEPING_SEP;

    /** Length of SENSITIVITY_SEP */
    static final int SENSITIVITY_SEP_LEN;

    /** Length of ANNOT_SEP */
    static final int ANNOT_SEP_LEN;

    /** Length of BOOKKEEPING_SEP */
    static final int BOOKKEEPING_SEP_LEN;

    static {
        // Lucene doesn't have any restrictions on characters in field names;
        // use the short, symbolic ones.
        ANNOT_SEP = "%";
        SENSITIVITY_SEP = "@";
        BOOKKEEPING_SEP = "#";
        SENSITIVITY_SEP_LEN = SENSITIVITY_SEP.length();
        ANNOT_SEP_LEN = ANNOT_SEP.length();
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

    private AnnotatedFieldNameUtil() {
    }

    public enum BookkeepFieldType {
        CONTENT_ID,
        FORWARD_INDEX_ID,
        LENGTH_TOKENS
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

    public static String forwardIndexIdField(String annotFieldName) {
        return bookkeepingField(annotFieldName, FORWARD_INDEX_ID_BOOKKEEP_NAME);
    }

    public static String lengthTokensField(String fieldName) {
        return bookkeepingField(fieldName, LENGTH_TOKENS_BOOKKEEP_NAME);
    }

    public static String tagAnnotationField(String fieldName) {
        return annotationField(fieldName, TAGS_ANNOT_NAME);
    }

    /**
     * Construct Lucene field name for annotated field bookkeeping subfield.
     *
     * @param fieldName the base field name
     * @param annotName the annotation name, or null if this is bookkeeping for the whole field
     * @param bookkeepName name of the bookkeeping value
     * @return the Lucene field name
     */
    static String bookkeepingField(String fieldName, String annotName, String bookkeepName) {
        String fieldAnnotName;
        boolean annotGiven = annotName != null && annotName.length() > 0;
        if (fieldName == null || fieldName.length() == 0) {
            if (annotGiven) {
                fieldAnnotName = annotName;
            } else
                throw new IllegalArgumentException("Must specify a base name, a annotation name or both: " + fieldName
                        + ", " + annotName + ", " + bookkeepName);
        } else {
            fieldAnnotName = fieldName + (annotGiven ? ANNOT_SEP + annotName : "");
        }

        if (bookkeepName == null || bookkeepName.length() == 0)
            return fieldAnnotName;
        return fieldAnnotName + BOOKKEEPING_SEP + bookkeepName;
    }

    /**
     * Construct Lucene field name for annotated field bookkeeping subfield.
     *
     * @param fieldName the base field name
     * @param bookkeepName name of the bookkeeping value
     * @return the Lucene field name
     */
    static String bookkeepingField(String fieldName, String bookkeepName) {
        return bookkeepingField(fieldName, null, bookkeepName);
    }

    /**
     * Construct (partial) Lucene field name for annotation on annotated field. 
     *
     * @param fieldName the base field name, or null to leave this part out
     * @param annotName the annotation name (required)
     * @param sensitivityName the sensitivity name, or null to leave this part out
     * @return the (partial) Lucene field name
     */
    public static String annotationField(String fieldName, String annotName, String sensitivityName) {
        String fieldAnnotName;
        boolean annotGiven = annotName != null && annotName.length() > 0;
        if (!annotGiven) {
            throw new IllegalArgumentException("Must specify a annotation name");
        }
        if (fieldName == null || fieldName.length() == 0) {
            fieldAnnotName = annotName;
        } else {
            fieldAnnotName = fieldName + ANNOT_SEP + annotName;
        }

        if (sensitivityName == null || sensitivityName.length() == 0) {
            return fieldAnnotName;
        }
        return fieldAnnotName + SENSITIVITY_SEP + sensitivityName;
    }

    /**
     * Construct partial Lucene field name for annotation on annotated field.
     * 
     * Sensitivity part is not included.
     *
     * @param fieldName the base field name, or null to leave this part out
     * @param annotName the annotation name (required)
     * @return the partial Lucene field name
     */
    public static String annotationField(String fieldName, String annotName) {
        return annotationField(fieldName, annotName, null);
    }

    /**
     * Construct a annotation alternative name from a field annotation name
     * 
     * @param fieldAnnotName the field annotation name
     * @param sensitivityName the alternative name
     * @return the field annotation alternative name
     */
    public static String annotationSensitivity(String fieldAnnotName, String sensitivityName) {
        if (sensitivityName == null || sensitivityName.length() == 0) {
            throw new IllegalArgumentException("Must specify an sensitivity name");
        }
        return fieldAnnotName + SENSITIVITY_SEP + sensitivityName;
    }

    /**
     * Gets the different components of a annotated field annotation (alternative) name.
     *
     * @param luceneFieldName the Lucene index field name, with possibly a annotation
     *            and/or alternative added
     * @return an array of size 1-4, containing the field name, and optionally the
     *         annotation name, alternative name and bookkeeping field name.
     *
     *         Annotation name may be null if this is a main bookkeeping field.
     *         Alternative name may be null if this is a bookkeeping field or if it
     *         indicates the main annotation (not an alternative).
     */
    public static String[] getNameComponents(String luceneFieldName) {

        /*
        
        Field names can be one of the following:
        
        (1) Annotation:              base%annot (e.g. word, lemma, pos)
        (2) Main bookkeeping:        base#cid
        (3) Annotation bookkeeping:  base%annot#fiid
        (4) Annotation alternative:  base%annot@alt
        
        */

        String baseName, annotName, altName, bookkeepingName;

        int annotSepPos = luceneFieldName.indexOf(ANNOT_SEP);
        int altSepPos = luceneFieldName.indexOf(SENSITIVITY_SEP);
        int bookkeepingSepPos;
        bookkeepingSepPos = luceneFieldName.indexOf(BOOKKEEPING_SEP);

        // Strip off annotation and possible alternative
        if (annotSepPos >= 0) {
            // Annotation given (1/3/4)
            baseName = luceneFieldName.substring(0, annotSepPos);
            int afterAnnotSepPos = annotSepPos + ANNOT_SEP_LEN;

            if (altSepPos >= 0) {
                // Annotation and alternative given (4)
                annotName = luceneFieldName.substring(afterAnnotSepPos, altSepPos);
                altName = luceneFieldName.substring(altSepPos + SENSITIVITY_SEP_LEN);
                return new String[] { baseName, annotName, altName };
            }

            // Maybe it's a bookkeeping field?
            if (bookkeepingSepPos >= 0 && bookkeepingSepPos > annotSepPos) {
                // Annotation plus bookkeeping subfield given. (3)
                annotName = luceneFieldName.substring(afterAnnotSepPos, bookkeepingSepPos);
                bookkeepingName = luceneFieldName.substring(bookkeepingSepPos + BOOKKEEPING_SEP_LEN);
                return new String[] { baseName, annotName, null, bookkeepingName };
            }

            // Plain annotation, no alternative or bookkeeping (1)
            annotName = luceneFieldName.substring(afterAnnotSepPos);
            return new String[] { baseName, annotName };
        }

        // No annotation given. Alternative?
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
     * Gets the base annotated field name from a Lucene index field name. So
     * "contents" and "contents%pos" would both yield "contents".
     *
     * @param luceneFieldName the Lucene index field name, with possibly a annotation
     *            added
     * @return the base annotated field name
     */
    public static String getBaseName(String luceneFieldName) {
        // Strip off annotation and possible alternative
        int pos = luceneFieldName.indexOf(ANNOT_SEP);
        if (pos >= 0) {
            return luceneFieldName.substring(0, pos);
        }
        pos = luceneFieldName.indexOf(BOOKKEEPING_SEP);
        if (pos >= 0) {
            return luceneFieldName.substring(0, pos);
        }
        pos = luceneFieldName.indexOf(SENSITIVITY_SEP);
        if (pos >= 0) {
            throw new IllegalArgumentException("Illegal field name: " + luceneFieldName);
        }
        return luceneFieldName;
    }

    public static String getDefaultMainAnnotationName() {
        return DEFAULT_MAIN_ANNOT_NAME;
    }

    public static MatchSensitivity sensitivity(String luceneField) {
        int i = luceneField.indexOf(SENSITIVITY_SEP);
        if (i < 0)
            throw new IllegalArgumentException("luceneField contains no " + SENSITIVITY_SEP);
        return MatchSensitivity.fromLuceneFieldSuffix(luceneField.substring(i + 1));
    }

    /**
     * Is the Lucene field name part of an annotated field or not?
     * 
     * @param luceneFieldName the field name
     * @return true if it's part of an annotated field, false if not
     */
    public static boolean isAnnotatedField(String luceneFieldName) {
        return luceneFieldName.contains(ANNOT_SEP) || luceneFieldName.contains(SENSITIVITY_SEP);
    }
    
    /**
     * Is the specified name a valid XML element name?
     * 
     * Generally, field and annotation names should be valid XML element names, so we
     * don't have to sanitize them when generating output XML.
     * 
     * @param name name to check
     * @return true iff it's a valid XML element name
     */
    public static boolean isValidXmlElementName(String name) {
        return REGEX_VALID_XML_ELEMENT_NAME.matcher(name).matches();
    }
    
    /**
     * Sanitize name if necessary, replacing characters forbidden in XML element names with an underscore.
     *
     * @param name name to sanitize
     * @return sanitized name
     */
    public static String sanitizeXmlElementName(String name) {
        return sanitizeXmlElementName(name, "_");
    }
    
    /**
     * Sanitize name if necessary, replacing characters forbidden in XML element names.
     * 
     * Also prepends an underscore if the name start in an invalid way (with the letters "xml" or not with letter or underscore).
     *
     * @param name name to sanitize
     * @param replaceChar what to replace illegal characters with (used as regex replace string)
     * @return sanitized name
     */
    public static String sanitizeXmlElementName(String name, String replaceChar) {
        name = name.replaceAll("[^\\p{L}0-9_\\.]", replaceChar); // can only contain letters, digits, underscores and periods
        if (name.matches("^[^\\p{L}_].*$") || name.toLowerCase().startsWith("xml")) { // must start with letter or underscore, may not start with "xml"
            name = "_" + name;
        }
        return name;
    }

}
