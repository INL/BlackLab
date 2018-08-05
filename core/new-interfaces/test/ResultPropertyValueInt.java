package nl.inl.blacklab.interfaces.test;

import nl.inl.blacklab.interfaces.results.ResultPropertyValue;

/** Property value that represents a BlackLab document. */
final class ResultPropertyValueInt implements ResultPropertyValue {
    
    private int i;
    
    public static ResultPropertyValueInt get(int i) {
        return new ResultPropertyValueInt(i);
    }
    
    private ResultPropertyValueInt(int i) {
        this.i = i;
    }

    @Override
    public int compareTo(ResultPropertyValue arg0) {
        if (arg0 instanceof ResultPropertyValueInt) {
            return Integer.compare(i, ((ResultPropertyValueInt)arg0).i);
        }
        throw new IllegalArgumentException("Incompatible types");
    }

    @Override
    public String serialize() {
        throw new UnsupportedOperationException();
    }
}