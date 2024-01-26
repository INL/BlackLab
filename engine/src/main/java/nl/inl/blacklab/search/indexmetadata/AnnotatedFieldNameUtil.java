package nl.inl.blacklab.search.indexmetadata;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.search.BlackLabIndex;

/**
 * Some utility functions for dealing with annotated field names.
 */
public final class AnnotatedFieldNameUtil {

    public static final String FORWARD_INDEX_ID_BOOKKEEP_NAME = "fiid";

    /** Used in classic external index format to store content store id in Lucene doc */
    private static final String CONTENT_ID_BOOKKEEP_NAME = "cid";

    /** Used in integrated index format to store content in Lucene doc */
    public static final String CONTENT_STORE_BOOKKEEP_NAME = "cs";

    private static final String LENGTH_TOKENS_BOOKKEEP_NAME = "length_tokens";

    private static final String DOC_START_OFFSET_BOOKKEEP_NAME = "doc_start_offset";

    private static final String DOC_END_OFFSET_BOOKKEEP_NAME = "doc_end_offset";

    /** Used as a default value if no name has been specified (legacy indexers only) */
    public static final String DEFAULT_MAIN_ANNOT_NAME = Constants.DEFAULT_MAIN_ANNOT_NAME;

    private static final String LEGACY_TAGS_ANNOT_NAME = "starttag";

    @Deprecated
    /** @deprecated use {@link #relationAnnotationName(DocWriter)} instead */
    public static final String TAGS_ANNOT_NAME = LEGACY_TAGS_ANNOT_NAME;

    private static final String RELATIONS_ANNOT_NAME = "_relation";

    /** Annotation name for the spaces and punctuation between words */
    public static final String PUNCTUATION_ANNOT_NAME = "punct";

    /**
     * Subannotations are indexed in a separate field, prefixed with their main
     * annotation name, so a subannotation of "pos" named "number" will be indexed
     * in a field "pos_number". This is the separator used for this prefix.
     */
    public static final String SUBANNOTATION_FIELD_PREFIX_SEPARATOR = "_";

    /** For parallel corpora, this separator goes between the field name and the version name, e.g.
     *  contents__nl, contents__de, etc.
     */
    public static final String PARALLEL_VERSION_SEPARATOR = "__";

    /**
     * Valid XML element names. Field and annotation names should generally conform to
     * this.
     */
    private static final Pattern REGEX_VALID_XML_ELEMENT_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z\\d\\-_.]*");

    /**
     * String used to separate the base field name (say, contents) and the field
     * annotation (pos, lemma, etc.)
     */
    static final String ANNOT_SEP = "%";

    /**
     * String used to separate the field/annotation name (say, contents_lemma) and the
     * alternative (e.g. "s" for case-sensitive)
     */
    static final String SENSITIVITY_SEP = "@";

    /**
     * String used to separate the field/annotation name (say, contents_lemma) and the
     * alternative (e.g. "s" for case-sensitive)
     */
    static final String BOOKKEEPING_SEP = "#";

    /** Length of SENSITIVITY_SEP */
    static final int SENSITIVITY_SEP_LEN = SENSITIVITY_SEP.length();

    /** Length of ANNOT_SEP */
    static final int ANNOT_SEP_LEN = ANNOT_SEP.length();

    /** Length of BOOKKEEPING_SEP */
    static final int BOOKKEEPING_SEP_LEN = BOOKKEEPING_SEP.length();

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

    public static boolean defaultSensitiveInsensitive(String name) {
        // Historic behaviour: if no sensitivity is given, "word" and "lemma" annotations will
        // get SensitivitySetting.SENSITIVE_AND_INSENSITIVE; all others get SensitivitySetting.ONLY_INSENSITIVE.
        // We warn users if their configuration relies on this, so we can eventually remove it.
        return name.equals("lemma") || name.equals(DEFAULT_MAIN_ANNOT_NAME);
    }

    /**
     * Get the name of the annotation that stores the relations between words.
     * @param indexType index type
     * @return name of annotation that stores the relations between words (and inline tags)
     */
    public static String relationAnnotationName(BlackLabIndex.IndexType indexType) {
        return indexType == BlackLabIndex.IndexType.EXTERNAL_FILES ? LEGACY_TAGS_ANNOT_NAME : RELATIONS_ANNOT_NAME;
    }

    public static boolean isRelationAnnotation(String name) {
        // TODO: icky, we can't name an annotation "starttag" in the integrated index this way.
        //   not a huge deal, and we can remove it when we remove the external index.
        return name.equals(LEGACY_TAGS_ANNOT_NAME) || name.equals(RELATIONS_ANNOT_NAME);
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

    public static String contentStoreField(String fieldName) {
        return bookkeepingField(fieldName, CONTENT_STORE_BOOKKEEP_NAME);
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

    /** Offset where the document starts in the content
     *  (used for parallel corpora, where multiple annotated fields might use the same
     *   file stored in the content store that contains all the languages)
     */
    public static String docStartOffsetField(String fieldName) {
        return bookkeepingField(fieldName, DOC_START_OFFSET_BOOKKEEP_NAME);
    }

    /** Offset where the document ends in the content
     *  (used for parallel corpora, where multiple annotated fields might use the same
     *   file stored in the content store that contains all the languages)
     */
    public static String docEndOffsetField(String fieldName) {
        return bookkeepingField(fieldName, DOC_END_OFFSET_BOOKKEEP_NAME);
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

        String baseName, annotName, sensitivityName, bookkeepingName;

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
                sensitivityName = luceneFieldName.substring(altSepPos + SENSITIVITY_SEP_LEN);
                return new String[] { baseName, annotName, sensitivityName };
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
            sensitivityName = luceneFieldName.substring(bookkeepingSepPos + BOOKKEEPING_SEP_LEN);
            return new String[] { baseName, null, null, sensitivityName };
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
     * Sanitize name if necessary, replacing forbidden characters with underscores.
     *
     * Also prepends an underscore if the name start in an invalid way (with the letters "xml" or not with letter or underscore).
     *
     * @param name           name to sanitize
     * @param disallowDashes if true, also disallow dash in names (even though XML element names can contain those)
     *                       (done for index compatibility; classic index format forbids these, but the new integrated
     *                       index format does allow them)
     * @return sanitized name
     */
    public static String sanitizeXmlElementName(String name, boolean disallowDashes) {
        if (name.isEmpty())
            return "_EMPTY_";
        name = name.replaceAll("[^\\p{L}\\d_.\\-]", "_"); // can only contain letters, digits, underscores and periods
        if (disallowDashes)
            name = name.replaceAll("-", "_");
        if (name.matches("^[^\\p{L}_].*$") || name.toLowerCase().startsWith("xml")) { // must start with letter or underscore, may not start with "xml"
            name = "_" + name;
        }
        return name;
    }

    /**
     * Check if the field name is a parallel field name.
     * <p>
     * Parallel field names have a suffix with a version code, e.g. "contents__nl" or "contents__de".
     *
     * @param fieldName field name to check
     * @return true if it's a parallel field name, false if not
     */
    public static boolean isParallelField(String fieldName) {
        return fieldName.contains(PARALLEL_VERSION_SEPARATOR); // TODO: make this more robust
    }

    /**
     * Split a parallel field name into the field base name and version.
     * <p>
     * If the field name doesn't have a version suffix, the second element in the
     * returned array will be empty.
     * <p>
     * Examples:
     * - "contents__de" will return ["contents", "de"]
     * - "contents" will return ["contents", ""]
     *
     * @param fieldName field name to split
     * @return array with two elements: field base name and version
     */
    public static String[] splitParallelFieldName(String fieldName) {
        if (fieldName.contains(PARALLEL_VERSION_SEPARATOR))
            return fieldName.split(PARALLEL_VERSION_SEPARATOR, 2);
        else
            return new String[] { fieldName, "" };
    }

    /**
     * Get the parallel field name for a field name and version.
     *
     * @param fieldBaseName field base name
     * @param version version
     * @return parallel field name
     */
    public static String getParallelFieldName(String fieldBaseName, String version) {
        return fieldBaseName + PARALLEL_VERSION_SEPARATOR + version;
    }

    /**
     * Get a different version of the given parallel field name.
     * <p>
     * Examples:
     * - getParallelFieldVersion("contents__de", "nl") will return "contents__nl"
     * - getParallelFieldVersion("contents", "de") will return "contents__de"
     * - getParallelFieldVersion("contents", "") will return "contents"
     *
     * @param fieldName a parallel field name with a version suffix
     * @param version version to substitute
     * @return the parallel field name with the indicated version
     */
    public static String getParallelFieldVersion(String fieldName, String version) {
        if (version.isEmpty())
            return fieldName;
        return splitParallelFieldName(fieldName)[0] + PARALLEL_VERSION_SEPARATOR + version;
    }

    /**
     * Check if these two fields have the same parallel base field, i.e. are different versions of the same content.
     *
     * Examples:
     * - "contents__nl" and "contents__de" have the same parallel base field ("contents")
     * - "contents__nl" and "metadata__nl" do not have the same parallel base field
     *
     * @param field1 first field name
     * @param field2 second field name
     * @return true if they have the same parallel base field, false if not
     */
    public static boolean isSameParallelBaseField(String field1, String field2) {
        String f1 = splitParallelFieldName(field1)[0];
        String f2 = splitParallelFieldName(field2)[0];
        return f1.equals(f2);
    }

}
