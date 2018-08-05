package nl.inl.blacklab.interfaces.results;

import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * Value of a result property.
 * 
 * Used for sorting and grouping.
 * 
 * For example:
 * - a string (e.g. if we're grouping on a metadata field),
 * - an integer (e.g. if we're sorting by group size),
 * - context words (e.g. if we're grouping on hit text),
 * - Doc (if we're grouping hits by document).
 */
public interface ResultPropertyValue extends Comparable<ResultPropertyValue>, Iterable<ResultPropertyValue> {
    
    /**
     * Convert the object to a String representation, for use in e.g. URLs.
     * @return the serialized object
     */
    String serialize();
    
    /**
     * Is this a compound of multiple property values?
     * 
     * If so, call iterator() or stream() to access them.
     * 
     * @return true if this is a compound, false if not
     */
    default boolean isCompound() {
        return false;
    }

    /**
     * Iterate over any constituent property values.
     * 
     * Because it is possible to sort or group on multiple values, 
     * it is useful to be able to retrieve them all separately,
     * e.g. for display in the interface.
     * 
     * For single values, will return the "empty iterator". Check isCompound() to know
     * whether or not it is useful to call this method.
     * 
     * @return iterator over constituent values
     */
    @Override
    default Iterator<ResultPropertyValue> iterator() {
        return Collections.emptyIterator();
    }

    /**
     * Stream any constituent property values.
     * 
     * Because it is possible to sort or group on multiple values, 
     * it is useful to be able to retrieve them all separately,
     * e.g. for display in the interface.
     * 
     * For single values, will return the "empty stream". Check isCompound() to know
     * whether or not it is useful to call this method.
     * 
     * @return stream of constituent values
     */
    default Stream<ResultPropertyValue> stream() {
        return Stream.empty();
    }

}
