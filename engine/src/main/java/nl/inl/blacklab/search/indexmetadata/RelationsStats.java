package nl.inl.blacklab.search.indexmetadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Relations stats for an index, including classes, types and attributes.
 */
public class RelationsStats {

    /**
     * Information about a relation type (under a class) and the attributes that occur with it.
     */
    public static class TypeStats {
        /**
         * How often this relation type occurs
         */
        private long count;

        /**
         * What attributes occur and with what values
         */
        private Map<String, Map<String, Long>> attributesAndValues = new TreeMap<>();

        void add(String term, long freq) {
            count += freq;
            Map<String, String> termAttr = RelationUtil.attributesFromIndexedTerm(term);
            termAttr.forEach((attr, value) -> {
                Map<String, Long> attrValues = attributesAndValues.computeIfAbsent(attr, k -> new HashMap<>());
                attrValues.compute(value, (k, v) -> freq + (v == null ? 0 : v));
            });
        }

        public long getCount() {
            return count;
        }

        public Map<String, Map<String, Long>> getAttributes() {
            return Collections.unmodifiableMap(attributesAndValues);
        }
    }

    /**
     * Statistics about a relation class and its types of relations and their attributes.
     */
    public static class ClassStats {
        /**
         * What relation types occur and with what attributes
         */
        private Map<String, TypeStats> relationTypes = new TreeMap<>();

        void add(String term, long freq) {
            String[] classAndType = RelationUtil.classAndType(RelationUtil.fullTypeFromIndexedTerm(term));
            String relationType = classAndType[1];
            TypeStats typeStats = relationTypes.computeIfAbsent(relationType,
                    k -> new TypeStats());
            typeStats.add(term, freq);
        }

        public Map<String, TypeStats> getRelationTypes() {
            return Collections.unmodifiableMap(relationTypes);
        }
    }

    /**
     * Is this an old external index that indexes tags differently?
     */
    private boolean oldStyleStarttag;

    /**
     * What relation classes occur and with what types and attributes
     */
    private Map<String, ClassStats> classes = new TreeMap<>();

    RelationsStats(boolean oldStyleStarttag) {
        this.oldStyleStarttag = oldStyleStarttag;
    }

    public Map<String, ClassStats> getClasses() {
        return Collections.unmodifiableMap(classes);
    }

    boolean addIndexedTerm(String term, long freq) {
        if (term.isEmpty())
            return true; // empty terms are added if no relations are found at a position (?)
        if (oldStyleStarttag && term.startsWith("@"))
            return true; // attribute value in older index; cannot match to its tag
        String relationClass;
        if (oldStyleStarttag) {
            // Old external index. Convert term to new style so we can process it the same way.
            relationClass = RelationUtil.RELATION_CLASS_INLINE_TAG;
            term = RelationUtil.indexTerm(RelationUtil.fullType(relationClass, term), null);
        } else {
            // New integrated index with spans indexed as relations as well.
            relationClass = RelationUtil.classAndType(RelationUtil.fullTypeFromIndexedTerm(term))[0];
        }
        ClassStats relClassStats = classes.computeIfAbsent(relationClass, k -> new ClassStats());
        relClassStats.add(term, freq);
        return true;
    }
}
