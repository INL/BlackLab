package nl.inl.blacklab.search.lucene;

/**
 * Information about a match (captured while matching).
 *
 * This can be a captured group, or a relation (e.g. a dependency relation
 * or an inline tag) used in the query.
 */
public abstract class MatchInfo implements Comparable<MatchInfo> {

    /**
     * The type of match info.
     */
    public enum Type {
        SPAN,
        RELATION,
        LIST_OF_RELATIONS,
        INLINE_TAG;

        public String jsonName() {
            switch (this) {
            case SPAN: return "span";
            case RELATION: return "relation";
            case LIST_OF_RELATIONS: return "list";
            case INLINE_TAG: return "tag";
            default: throw new RuntimeException("Unknown match info type: " + this);
            }
        }
    }

    /**
     * Null-safe equality test of two MatchInfos.
     *
     * Returns true if both are null, or if they are equal.
     *
     * @param a the first MatchInfo
     * @param b the second MatchInfo
     * @return true iff both are null, or if they are equal
     */
    public static boolean equal(MatchInfo[] a, MatchInfo[] b) {
        if ((a == null) != (b == null)) {
            // One is null, the other is not.
            return false;
        }
        if (a == null) {
            // Both null
            return true;
        }
        // Both set
        return a.equals(b);
    }

    /** Field this match info is from.
     *  If this is not a parallel corpus, this will always be the field that was searched.
     *  Never null.
     */
    private final String field;

    MatchInfo(String field) {
        assert field != null;
        this.field = field;
    }

    public String getField() {
        return field;
    }

    protected String toStringOptFieldName(String defaultField) {
        return field.equals(defaultField) ? "" : " (" + getField() + ")";
    }

    public abstract Type getType();

    @Override
    public String toString() {
        return toString("");
    }

    public abstract String toString(String defaultField);

    @Override
    public int compareTo(MatchInfo o) {
        // Subclasses should compare properly if types match;
        // if not, just compare class names
        return getClass().getName().compareTo(o.getClass().getName());
    }

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();

    public abstract int getSpanStart();

    public abstract int getSpanEnd();

    public boolean isSpanEmpty() {
        return getSpanStart() == getSpanEnd();
    }

    /** Match info definition: name, type, (optionally) overridden field */
    public static class Def {
        /** This group's index in the captured group array */
        private int index;

        /** This group's name */
        private String name;

        /** What type of match info is this? (span, tag, relation, list of relations) */
        private Type type;

        /** What field is this match info for? Never null. */
        private String field;

        public Def(int index, String name, Type type, String field) {
            this.index = index;
            this.name = name;
            this.type = type;
            assert field != null;
            this.field = field;
        }

        public int getIndex() {
            return index;
        }

        public String getName() {
            return name;
        }

        public Type getType() {
            return type;
        }

        public String getField() {
            return field;
        }

        public void updateType(Type type) {
            if (type == null) {
                // This just means "we don't know the type at this point in the query",
                // which is fine (any match info can be used as a regular span). The type
                // will be known at one location in the query, so this will eventually get set.
                return;
            }
            assert this.type == null || type == this.type;
            this.type = type;
        }
    }
}
