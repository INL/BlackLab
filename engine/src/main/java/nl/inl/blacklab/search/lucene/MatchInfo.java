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
        INLINE_TAG
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

    /** Field this match info is from (parallel corpora), or null if default field. */
    private String overriddenField;

    MatchInfo(String overriddenField) {
        this.overriddenField = overriddenField;
    }

    public String getOverriddenField() {
        return overriddenField;
    }

    protected String toStringOptFieldName() {
        return getOverriddenField() == null ? "" : " (" + getOverriddenField() + ")";
    }

    public abstract Type getType();

    @Override
    public abstract String toString();

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
}
