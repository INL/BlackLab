package nl.inl.blacklab.search.matchfilter;

import nl.inl.util.StringUtil;

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
    public boolean isTruthy() {
        return true;
    }

    @Override
    public String toString() {
        return s;
    }

    public ConstraintValue stringEquals(ConstraintValueString rb, boolean caseSensitive, boolean diacSensitive) {
        String a = getValue();
        String b = rb.getValue();
        if (!caseSensitive) {
            a = a.toLowerCase();
            b = b.toLowerCase();
        }
        if (!diacSensitive) {
            a = StringUtil.stripAccents(a);
            b = StringUtil.stripAccents(b);
        }
        return ConstraintValue.get(a.equals(b));
    }

}
