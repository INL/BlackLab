package nl.inl.blacklab.search.matchfilter;

/**
 * Data value a constraint (MatchFilter) can evaluate to.
 *
 * e.g. the constraint <code>a.lemma</code> evaluates to a
 * ConstraintValueString while the constraint
 * <code>a.lemma = b.lemma</code> evaluates to a ConstraintValueBoolean.
 */
public abstract class ConstraintValue implements Comparable<ConstraintValue>, TextPatternStruct {

    private static final ConstraintValue FALSE = new ConstraintValueBoolean(false);

    private static final ConstraintValue TRUE = new ConstraintValueBoolean(true);

    private static final ConstraintValue UNDEFINED = new ConstraintValueUndefined();

    public static ConstraintValue get(int i) {
        return new ConstraintValueInt(i);
    }

    public static ConstraintValue get(String s) {
        return new ConstraintValueString(s);
    }

    public static ConstraintValue get(boolean b) {
        return b ? TRUE : FALSE;
    }

    public static ConstraintValue undefined() {
        return UNDEFINED;
    }

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int compareTo(ConstraintValue rb);

    public abstract boolean isTruthy();

    @Override
    public abstract String toString();
}
