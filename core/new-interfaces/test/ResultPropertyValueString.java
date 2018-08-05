package nl.inl.blacklab.interfaces.test;

import nl.inl.blacklab.interfaces.results.ResultPropertyValue;

/** Property value that represents a BlackLab document. */
final class ResultPropertyValueString implements ResultPropertyValue {
    
    private String s;
    
    public static ResultPropertyValueString get(String i) {
        return new ResultPropertyValueString(i);
    }
    
    private ResultPropertyValueString(String i) {
        this.s = i;
    }

    @Override
    public int compareTo(ResultPropertyValue arg0) {
        if (arg0 instanceof ResultPropertyValueString) {
            return s.compareTo( ((ResultPropertyValueString)arg0).s );
        }
        throw new IllegalArgumentException("Incompatible types");
    }

    @Override
    public String serialize() {
        throw new UnsupportedOperationException();
    }
}