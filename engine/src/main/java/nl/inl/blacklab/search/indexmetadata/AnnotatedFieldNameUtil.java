package nl.inl.blacklab.search.indexmetadata;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    /** Separator before attribute name+value in _relation annotation. */
    private  static final String ATTR_SEPARATOR = "\u0001";

    /** Separator between attr and value in _relation annotation. */
    private  static final String KEY_VALUE_SEPARATOR = "\u0002";

    /** Separator between relation class (e.g. "__tag", "dep" for dependency relation, etc.) and relation type
     *  (e.g. "s" for sentence tag, or "nsubj" for dependency relation "nominal subject") */
    private static final String RELATION_CLASS_TYPE_SEPARATOR = "::";

    /** Relation class used for inline tags. Deliberately obscure to avoid collisions with "real" relations. */
    public static final String RELATION_CLASS_INLINE_TAG = "__tag";

    /** Relation class to use for dependency relations. */
    public static final String RELATION_CLASS_DEPENDENCY = "dep";

    private AnnotatedFieldNameUtil() {
    }

    public static boolean defaultSensitiveInsensitive(String name) {
        // Historic behaviour: if no sensitivity is given, "word" and "lemma" annotations will
        // get SensitivitySetting.SENSITIVE_AND_INSENSITIVE; all others get SensitivitySetting.ONLY_INSENSITIVE.
        // We warn users if their configuration relies on this, so we can eventually remove it.
        return name.equals("lemma") || name.equals(DEFAULT_MAIN_ANNOT_NAME);
    }

    /**
     * Determine the term to index in Lucene for a relation.
     *
     * @param fullRelationType full relation type
     * @param attributes any attributes for this relation
     * @return term to index in Lucene
     */
    public static String relationIndexTerm(String fullRelationType, Map<String, String> attributes) {
        if (attributes == null)
            return fullRelationType + ATTR_SEPARATOR;

        // Sort and concatenate the attribute names and values
        String attrPart = attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> AnnotatedFieldNameUtil.tagAttributeIndexValue(e.getKey(), e.getValue(),
                                BlackLabIndex.IndexType.INTEGRATED))
                .collect(Collectors.joining());

        // The term to index consists of the type followed by the (sorted) attributes.
        return fullRelationType + ATTR_SEPARATOR + attrPart;
    }

    /**
     * Determine the term to index in Lucene for a relation.
     *
     * @param fullRelationType full relation type
     * @param attributes any attributes for this relation
     * @return term to index in Lucene
     */
    public static String relationIndexTermMulti(String fullRelationType, Map<String, Collection<String>> attributes) {
        if (attributes == null)
            return fullRelationType + ATTR_SEPARATOR;

        // Sort and concatenate the attribute names and values
        String attrPart = attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getValue().stream()
                        .map( v -> AnnotatedFieldNameUtil.tagAttributeIndexValue(e.getKey(), v,
                                    BlackLabIndex.IndexType.INTEGRATED))
                        .collect(Collectors.joining()))
                .collect(Collectors.joining());

        // The term to index consists of the type followed by the (sorted) attributes.
        return fullRelationType + ATTR_SEPARATOR + attrPart;
    }

    /**
     * Determine the search regex for a relation.
     *
     * NOTE: both fullRelationType and attribute names/values are interpreted as regexes,
     * so any regex special characters you wish to find should be escaped!
     *
     * @param fullRelationType full relation type
     * @param attributes any attribute criteria for this relation
     * @return regex to find this relation
     */
    public static String relationSearchRegex(String fullRelationType, Map<String, String> attributes) {
        // Sort and concatenate the attribute names and values
        String attrPart = attributes == null ? "" : attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> tagAttributeIndexValue(e.getKey(), e.getValue(), BlackLabIndex.IndexType.INTEGRATED))
                .collect(Collectors.joining(".*")); // zero or more chars between attribute matches

        // The regex consists of the type part followed by the (sorted) attributes part.
        return fullRelationType + ATTR_SEPARATOR + ".*" + (attrPart.isEmpty() ? "" : attrPart + ".*");
    }

    /**
     * Get the full relation type for a relation class and relation type.
     *
     * @param relClass relation class, e.g. "dep" for dependency relations
     * @param type relation type, e.g. "nsubj" for a nominal subject
     * @return full relation type
     */
    public static String fullRelationType(String relClass, String type) {
        return relClass + RELATION_CLASS_TYPE_SEPARATOR + type;
    }

    /**
     * Get the full relation type for an inline tag.
     *
     * @param tagName tag name
     * @return full relation type
     */
    public static String tagFullRelationType(String tagName) {
        return fullRelationType(RELATION_CLASS_INLINE_TAG, tagName);
    }

    /**
     * Given the indexed term, return the full relation type.
     *
     * This leaves out any attributes indexed with the relation.
     *
     * @param indexedTerm the term indexed in Lucene
     * @return the full relation type
     */
    public static String fullRelationTypeFromIndexedTerm(String indexedTerm) {
        int sep = indexedTerm.indexOf(ATTR_SEPARATOR);
        if (sep < 0)
            return indexedTerm;
        return indexedTerm.substring(0, sep);
    }

    /**
     * Split a full relation type into relation class and relation type.
     *
     * Relations are indexed with a full type, consisting of a relation class and a relation type.
     * The class is used to distinguish between different groups of relations, e.g. inline tags
     * and dependency relations.
     *
     * @param fullRelationType full relation type
     * @return relation class and relation type
     */
    public static String[] relationClassAndType(String fullRelationType) {
        int sep = fullRelationType.indexOf(RELATION_CLASS_TYPE_SEPARATOR);
        if (sep < 0)
            return new String[] { "", fullRelationType };
        return new String[] { fullRelationType.substring(0, sep), fullRelationType.substring(sep + RELATION_CLASS_TYPE_SEPARATOR.length()) };
    }

    /**
     * What value do we index for attributes to tags (spans)?
     *
     * (integrated index) A tag <s id="123"> ... </s> would be indexed in annotation "_relation"
     * with a single tokens: "__tag\u0002s\u0001id\u0002123\u0001".
     *
     * (classic external index) A tag <s id="123"> ... </s> would be indexed in annotation "starttag"
     * with two tokens at the same position: "s" and "@id__123".
     *
     * @param name attribute name
     * @param value attribute value
     * @return value to index for this attribute
     */
    public static String tagAttributeIndexValue(String name, String value, BlackLabIndex.IndexType indexType) {
        if (indexType == BlackLabIndex.IndexType.EXTERNAL_FILES) {
             // NOTE: this means that we cannot distinguish between attributes for
             // different start tags occurring at the same token position!
             // (In the integrated index format, we include all attributes in the term)
            return "@" + name.toLowerCase() + "__" + value.toLowerCase();
        }
        return name + KEY_VALUE_SEPARATOR + value + ATTR_SEPARATOR;
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
        name = name.replaceAll("[^\\p{L}\\d_.\\-]", "_"); // can only contain letters, digits, underscores and periods
        if (disallowDashes)
            name = name.replaceAll("-", "_");
        if (name.matches("^[^\\p{L}_].*$") || name.toLowerCase().startsWith("xml")) { // must start with letter or underscore, may not start with "xml"
            name = "_" + name;
        }
        return name;
    }

}
