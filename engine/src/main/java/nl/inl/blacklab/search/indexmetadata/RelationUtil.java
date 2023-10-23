package nl.inl.blacklab.search.indexmetadata;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import nl.inl.blacklab.search.BlackLabIndex;

public class RelationUtil {

    /** Relation class used for inline tags. Deliberately obscure to avoid collisions with "real" relations. */
    public static final String RELATION_CLASS_INLINE_TAG = "__tag";

    /** Relation class to use for dependency relations. */
    public static final String RELATION_CLASS_DEPENDENCY = "dep";

    /** Separator between relation class (e.g. "__tag", "dep" for dependency relation, etc.) and relation type
     *  (e.g. "s" for sentence tag, or "nsubj" for dependency relation "nominal subject") */
    public static final String RELATION_CLASS_TYPE_SEPARATOR = "::";

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
     *
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

    /**
     * Get the full relation type for a relation class and relation type.
     *
     * @param relClass relation class, e.g. "dep" for dependency relations
     * @param type relation type, e.g. "nsubj" for a nominal subject
     * @return full relation type
     */
    public static String fullType(String relClass, String type) {
        return relClass + RELATION_CLASS_TYPE_SEPARATOR + type;
    }

    /**
     * Get the full relation type for an inline tag.
     *
     * @param tagName tag name
     * @return full relation type
     */
    public static String inlineTagFullType(String tagName) {
        return fullType(RELATION_CLASS_INLINE_TAG, tagName);
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
        return CH_NAME_START + name + KEY_VALUE_SEPARATOR + value + ATTR_SEPARATOR;
    }

    /**
     * Given the indexed term, return the full relation type.
     *
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
     *
     * Relations are indexed with a full type, consisting of a relation class and a relation type.
     * The class is used to distinguish between different groups of relations, e.g. inline tags
     * and dependency relations.
     *
     * @param fullRelationType full relation type
     * @return relation class and relation type
     */
    public static String[] classAndType(String fullRelationType) {
        int sep = fullRelationType.indexOf(RELATION_CLASS_TYPE_SEPARATOR);
        if (sep < 0)
            return new String[] { "", fullRelationType };
        return new String[] {
            fullRelationType.substring(0, sep),
            fullRelationType.substring(sep + RELATION_CLASS_TYPE_SEPARATOR.length())
        };
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
    public static String searchRegex(String fullRelationType, Map<String, String> attributes) {
        // Sort and concatenate the attribute names and values
        String attrPart = attributes == null ? "" : attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> tagAttributeIndexValue(e.getKey(), e.getValue(), BlackLabIndex.IndexType.INTEGRATED))
                .collect(Collectors.joining(".*")); // zero or more chars between attribute matches

        // The regex consists of the type part followed by the (sorted) attributes part.
        return fullRelationType + ATTR_SEPARATOR + ".*" + (attrPart.isEmpty() ? "" : attrPart + ".*");
    }
}
