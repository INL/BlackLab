package nl.inl.blacklab.resultproperty;

import java.util.List;

public interface ResultPropValue extends Comparable<Object> {

    @Override
    int compareTo(Object o);

    @Override
    int hashCode();

    @Override
    boolean equals(Object obj);

    /**
     * Convert the object to a String representation, for use in e.g. URLs.
     * 
     * @return the serialized object
     */
    String serialize();

    @Override
    String toString();

    List<String> getPropValues();

}
