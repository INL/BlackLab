package nl.inl.blacklab.search.matchfilter;

public class ConstraintValueUndefined extends ConstraintValue {

    ConstraintValueUndefined() {
        // NOP
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public boolean isTruthy() {
        return false;
    }

    @Override
    public String toString() {
        return "undefined";
    }

}
