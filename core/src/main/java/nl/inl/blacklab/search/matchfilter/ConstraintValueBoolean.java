package nl.inl.blacklab.search.matchfilter;

public class ConstraintValueBoolean extends ConstraintValue {

    private boolean b;

    public ConstraintValueBoolean(boolean b) {
        this.b = b;
    }

    @Override
    public boolean isTruthy() {
        return b;
    }

    @Override
    public String toString() {
        return b ? "true" : "false";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (b ? 1231 : 1237);
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
        ConstraintValueBoolean other = (ConstraintValueBoolean) obj;
        return b == other.b;
    }

}
