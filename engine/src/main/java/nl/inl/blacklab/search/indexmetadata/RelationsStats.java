package nl.inl.blacklab.search.indexmetadata;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import nl.inl.util.LimitUtil;

/**
 * Relations stats for a corpus, including classes, types and attributes.
 */
public class RelationsStats {

    /**
     * Information about a relation type (under a class) and the attributes that occur with it.
     */
    public class TypeStats implements LimitUtil.Limitable<TypeStats> {

        /**
         * How often this relation type occurs
         */
        private long count;

        /**
         * What attributes occur and with what values
         */
        private Map<String, TruncatableFreqList> attributesAndValues = new TreeMap<>();

        void add(String term, long freq) {
            count += freq;
            Map<String, String> termAttr = RelationUtil.attributesFromIndexedTerm(term);
            termAttr.forEach((attr, value) -> {
                // Add the attribute
                TruncatableFreqList attrValues = attributesAndValues.computeIfAbsent(attr,
                        k -> new TruncatableFreqList(limitValues));
                attrValues.add(value, freq);
            });
        }

        public long getCount() {
            return count;
        }

        public Map<String, TruncatableFreqList> getAttributes() {
            return Collections.unmodifiableMap(attributesAndValues);
        }

        @Override
        public TypeStats withLimit(long limitValues) {
            if (limitValues == RelationsStats.this.limitValues)
                return this;
            if  (limitValues > RelationsStats.this.limitValues) {
                // Are all our lists complete? Then this is okay.
                if (attributesAndValues.values().stream().noneMatch(TruncatableFreqList::isTruncated))
                    return this;
                throw new IllegalArgumentException("Cannot increase limitValues from " + RelationsStats.this.limitValues + " to " + limitValues);
            }

            // Re-limit this type
            TypeStats result = new TypeStats();
            result.count = count;
            result.attributesAndValues = LimitUtil.limit(attributesAndValues, limitValues);
            return result;
        }
    }

    /**
     * Statistics about a relation class and its types of relations and their attributes.
     */
    public class ClassStats implements LimitUtil.Limitable<ClassStats> {
        /**
         * What relation types occur and with what attributes
         */
        private Map<String, TypeStats> relationTypes = new TreeMap<>();

        void add(String term, long freq) {
            String[] classAndType = RelationUtil.classAndType(RelationUtil.fullTypeFromIndexedTerm(term));
            String relationType = classAndType[1];
            // Add the relation type
            TypeStats typeStats = relationTypes.computeIfAbsent(relationType, k -> new TypeStats());
            typeStats.add(term, freq);
        }

        public Map<String, TypeStats> getRelationTypes() {
            return Collections.unmodifiableMap(relationTypes);
        }

        @Override
        public ClassStats withLimit(long limitValues) {
            ClassStats result = new ClassStats();
            result.relationTypes = LimitUtil.limit(relationTypes, limitValues);
            return result;
        }
    }

    /**
     * Is this an old external index that indexes tags differently?
     */
    private boolean oldStyleStarttag;

    private long limitValues;

    /**
     * What relation classes occur and with what types and attributes
     */
    private Map<String, ClassStats> classes = new TreeMap<>();

    RelationsStats(boolean oldStyleStarttag, long limitValues) {
        this.oldStyleStarttag = oldStyleStarttag;
        this.limitValues = limitValues;
    }

    public RelationsStats withLimit(long limitValues) {
        if (limitValues == this.limitValues)
            return this;
        if (limitValues > this.limitValues) //@@@ could be okay if no lists are truncated
            throw new IllegalArgumentException("Cannot increase limitValues from " + this.limitValues + " to " + limitValues);

        RelationsStats result = new RelationsStats(oldStyleStarttag, limitValues);
        result.classes = LimitUtil.limit(classes, limitValues);
        return result;
    }

    /** What limitValues value was used while collecting relationsStats?
     *  (we do this to limit memory usage, but we can only reuse the data
     *   for limitValues <= this value)
     */
    public long getLimitValues() {
        return limitValues;
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
