package nl.inl.blacklab.search.matchfilter;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class ConstraintValueString extends ConstraintValue {

    String s;

    ConstraintValueString(String s) {
        if (s == null)
            throw new IllegalArgumentException("s cannot be null!");
        this.s = s;
    }

    public String getValue() {
        return s;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((s == null) ? 0 : s.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ConstraintValueString other = (ConstraintValueString) obj;
        if (s == null) {
            if (other.s != null)
                return false;
        } else if (!s.equals(other.s))
            return false;
        return true;
    }

    @Override
    public int compareTo(ConstraintValue other) {
        if (other instanceof ConstraintValueString)
            return s.compareTo(((ConstraintValueString) other).s);
        throw new IllegalArgumentException("Can only compare equal types! Tried to compare string to " + other.getClass().getName());
    }

    @Override
    public boolean isTruthy() {
        return true;
    }

    @Override
    public String toString() {
        return s;
    }

    public ConstraintValue stringEquals(ConstraintValueString rb, MatchSensitivity sensitivity) {
        String a = sensitivity.desensitize(getValue());
        String b = sensitivity.desensitize(rb.getValue());
        return ConstraintValue.get(a.equals(b));
    }

    public int stringCompareTo(ConstraintValueString rb, MatchSensitivity sensitivity) {
        String a = sensitivity.desensitize(getValue());
        String b = sensitivity.desensitize(rb.getValue());
        return a.compareTo(b);
    }
}
