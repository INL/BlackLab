package nl.inl.blacklab.search.indexmetadata;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import nl.inl.blacklab.search.BlackLabIndex;

public class RelationUtil {

    /** Relation class used for inline tags. Deliberately obscure to avoid collisions with "real" relations. */
    public static final String CLASS_INLINE_TAG = "__tag";

    /** Relation class to use for dependency relations (by convention). */
    public static final String CLASS_DEPENDENCY = "dep";

    /** Relation class to use for dependency relations. */
    public static final String CLASS_DEFAULT = "rel";

    /** Relation class to use for alignment relations (by convention). */
    public static final String CLASS_ALIGNMENT = "al";

    /** Default relation class regex for finding dependency relations ("any true relation class", i.e. no inline tags). */
    public static final String CLASS_ANY_REGEX = "[^_].*";

    /** Default relation target version: any or none */
    public static final String OPTIONAL_TARGET_VERSION_REGEX = "(" + AnnotatedFieldNameUtil.PARALLEL_VERSION_SEPARATOR + ".*)?";

    /** Default relation type: any */
    public static final String ANY_TYPE_REGEX = ".*";

    /** Separator between relation class (e.g. "__tag", "dep" for dependency relation, etc.) and relation type
     *  (e.g. "s" for sentence tag, or "nsubj" for dependency relation "nominal subject") */
    public static final String CLASS_TYPE_SEPARATOR = "::";

    /** Separator after relation type and attribute value in _relation annotation. */
    private static final String ATTR_SEPARATOR = "\u0001";

    /** Separator between attr and value in _relation annotation. */
    private static final String KEY_VALUE_SEPARATOR = "\u0002";

    /** Character before attribute name in _relation annotation. */
    private static final String CH_NAME_START = "\u0003";

    /**
     * Determine the term to index in Lucene for a relation.
     *
     * @param fullRelationType full relation type
     * @param attributes any attributes for this relation
     * @return term to index in Lucene
     */
    public static String indexTerm(String fullRelationType, Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty())
            return fullRelationType + ATTR_SEPARATOR;

        // Sort and concatenate the attribute names and values
        String attrPart = attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> tagAttributeIndexValue(e.getKey(), e.getValue(),
                                BlackLabIndex.IndexType.INTEGRATED))
                .collect(Collectors.joining());

        // The term to index consists of the type followed by the (sorted) attributes.
        return fullRelationType + ATTR_SEPARATOR + attrPart;
    }

    /**
     * Determine the term to index in Lucene for a relation.
     * <p>
     * This version can handle relations with multiple values for the same attribute,
     * which can happen as a result of processing steps during indexing.
     *
     * @param fullRelationType full relation type
     * @param attributes any attributes for this relation
     * @return term to index in Lucene
     */
    public static String indexTermMulti(String fullRelationType, Map<String, Collection<String>> attributes) {
        if (attributes == null)
            return fullRelationType + ATTR_SEPARATOR;

        // Sort and concatenate the attribute names and values
        String attrPart = attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getValue().stream()
                        .map( v -> tagAttributeIndexValue(e.getKey(), v,
                                    BlackLabIndex.IndexType.INTEGRATED))
                        .collect(Collectors.joining()))
                .collect(Collectors.joining());

        // The term to index consists of the type followed by the (sorted) attributes.
        return fullRelationType + ATTR_SEPARATOR + attrPart;
    }

    public static Map<String, String> attributesFromIndexedTerm(String indexedTerm) {
        int i = indexedTerm.indexOf(ATTR_SEPARATOR);
        if (i < 0 || i == indexedTerm.length() - 1)
            return Collections.emptyMap();
        Map<String, String> attributes = new HashMap<>();
        for (String attrPart: indexedTerm.substring(i + 1).split(ATTR_SEPARATOR)) {
            String[] keyVal = attrPart.split(KEY_VALUE_SEPARATOR, 2);
            // older index doesn't have CH_NAME_START; strip it if it's there
            String key = keyVal[0].startsWith(CH_NAME_START) ? keyVal[0].substring(1) : keyVal[0];
            if (!attributes.containsKey(key)) // only the first value if there's multiple!
                attributes.put(key, keyVal[1]);
        }
        return attributes;
    }

    public static String optPar(String expr) {
        if (expr.startsWith("(") && expr.endsWith(")") || expr.matches("\\.[*+?]"))
            return expr;
        return "(" + expr + ")";
    }

    /**
     * Get the full relation type for a relation class and relation type regex.
     *
     * @param relClass relation class regex, e.g. "dep" for dependency relations
     * @param type relation type regex, e.g. "nsubj" for a nominal subject
     * @return full relation type regex
     */
    public static String fullTypeRegex(String relClass, String type) {
        return optPar(relClass) + CLASS_TYPE_SEPARATOR + optPar(type);
    }

    /**
     * Get the full relation type for a relation class and relation type.
     *
     * @param relClass relation class, e.g. "dep" for dependency relations
     * @param type relation type, e.g. "nsubj" for a nominal subject
     * @return full relation type
     */
    public static String fullType(String relClass, String type) {
        return relClass + CLASS_TYPE_SEPARATOR + type;
    }

    /**
     * What value do we index for attributes to tags (spans)?
     * <p>
     * (integrated index) A tag <s id="123"> ... </s> would be indexed in annotation "_relation"
     * with a single tokens: "__tag::s\u0001\u0003id\u0002123\u0001".
     * <p>
     * (classic external index) A tag <s id="123"> ... </s> would be indexed in annotation "starttag"
     * with two tokens at the same position: "s" and "@id__123".
     *
     * @param name attribute name
     * @param value attribute value
     * @return value to index for this attribute
     */
    public static String tagAttributeIndexValue(String name, String value, BlackLabIndex.IndexType indexType) {
        return tagAttributeIndexValue(false, name, value, indexType);
    }

    /**
     * What value do we index for attributes to tags (spans)?
     * <p>
     * (integrated index) A tag <s id="123"> ... </s> would be indexed in annotation "_relation"
     * with a single tokens: "__tag::s\u0001\u0003id\u0002123\u0001".
     * <p>
     * (classic external index) A tag <s id="123"> ... </s> would be indexed in annotation "starttag"
     * with two tokens at the same position: "s" and "@id__123".
     *
     * @param useOldEncoding use the older encoding without CH_NAME_START?
     * @param name attribute name
     * @param value attribute value
     * @return value to index for this attribute
     */
    public static String tagAttributeIndexValue(boolean useOldEncoding, String name, String value, BlackLabIndex.IndexType indexType) {
        if (indexType == BlackLabIndex.IndexType.EXTERNAL_FILES) {
            // NOTE: this means that we cannot distinguish between attributes for
            // different start tags occurring at the same token position!
            // (In the integrated index format, we include all attributes in the term)
            return "@" + name.toLowerCase() + "__" + value.toLowerCase();
        }
        return (useOldEncoding ? "" : CH_NAME_START) + name + KEY_VALUE_SEPARATOR + value + ATTR_SEPARATOR;
    }

    /**
     * Given the indexed term, return the full relation type.
     * <p>
     * This leaves out any attributes indexed with the relation.
     *
     * @param indexedTerm the term indexed in Lucene
     * @return the full relation type
     */
    public static String fullTypeFromIndexedTerm(String indexedTerm) {
        int sep = indexedTerm.indexOf(ATTR_SEPARATOR);
        if (sep < 0)
            return indexedTerm;
        return indexedTerm.substring(0, sep);
    }

    /**
     * Split a full relation type into relation class and relation type.
     * <p>
     * Relations are indexed with a full type, consisting of a relation class and a relation type.
     * The class is used to distinguish between different groups of relations, e.g. inline tags
     * and dependency relations.
     *
     * @param fullRelationType full relation type
     * @return relation class and relation type
     */
    public static String[] classAndType(String fullRelationType) {
        int sep = fullRelationType.indexOf(CLASS_TYPE_SEPARATOR);
        if (sep < 0)
            return new String[] { "", fullRelationType };
        return new String[] {
            fullRelationType.substring(0, sep),
            fullRelationType.substring(sep + CLASS_TYPE_SEPARATOR.length())
        };
    }

    /**
     * Determine the search regex for a relation.
     * <p>
     * NOTE: both fullRelationTypeRegex and attribute names/values are interpreted as regexes,
     * so any regex special characters you wish to find should be escaped!
     *
     * @param index index we're using (to check index flag IFL_INDEX_RELATIONS_TWICE)
     * @param fullRelationTypeRegex full relation type
     * @param attributes any attribute criteria for this relation
     * @return regex to find this relation
     */
    public static String searchRegex(BlackLabIndex index, String fullRelationTypeRegex, Map<String, String> attributes) {

        // Check if this is an older index that uses the attribute encoding without CH_NAME_START
        // (will be removed eventually)
        boolean useOldRelationsEncoding = index != null && index.metadata()
                .indexFlag(IndexMetadataIntegrated.IFL_INDEX_RELATIONS_TWICE).isEmpty();

        if (attributes == null || attributes.isEmpty()) {
            // No attribute filters, so find the faster term that only has the relation type.
            // (for older encoding, just do a prefix query on the slower terms)
            return optPar(fullRelationTypeRegex) + ATTR_SEPARATOR + (useOldRelationsEncoding ? ".*" : "");
        }

        // Sort and concatenate the attribute names and values
        String attrPart = attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> tagAttributeIndexValue(useOldRelationsEncoding,
                        e.getKey(), e.getValue(), BlackLabIndex.IndexType.INTEGRATED))
                .collect(Collectors.joining(".*")); // zero or more chars between attribute matches

        // The regex consists of the type part followed by the (sorted) attributes part.
        return optPar(fullRelationTypeRegex) + ATTR_SEPARATOR + ".*" + attrPart + ".*";
    }

    public static String optPrependDefaultClass(String relationTypeRegex) {
        if (!relationTypeRegex.contains(CLASS_TYPE_SEPARATOR))
            relationTypeRegex = fullTypeRegex(CLASS_ANY_REGEX, optPar(relationTypeRegex));
        return relationTypeRegex;
    }
}
