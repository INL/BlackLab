package nl.inl.blacklab.search.lucene;

/**
 * Information about a match (captured while matching).
 *
 * This can be a captured group, or a relation (e.g. a dependency relation
 * or an inline tag) used in the query.
 */
public interface MatchInfo extends Comparable<MatchInfo> {

    /**
     * The type of match info.
     */
    enum Type {
        SPAN,
        RELATION
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
    static boolean equal(MatchInfo[] a, MatchInfo[] b) {
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

    Type getType();

    @Override
    String toString();

    @Override
    default int compareTo(MatchInfo o) {
        // Subclasses should compare properly if types match;
        // if not, just compare class names
        return getClass().getName().compareTo(o.getClass().getName());
    }

    @Override
    boolean equals(Object o);

    @Override
    int hashCode();

    int getSpanStart();

    int getSpanEnd();

    default boolean isSpanEmpty() {
        return getSpanStart() == getSpanEnd();
    }
}
