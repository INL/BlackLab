package nl.inl.blacklab.resultproperty;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;

import nl.inl.blacklab.search.results.Results;

/**
 * Property of some result (i.e. hit, group, groupOfGroups)
 * @param <T> type of result
 */
public interface ResultProperty<T> extends Comparator<T>, Serializable {

    /**
     * Get the property value for a specific result.
     * @param hit result to get property value for
     * @return property value
     */
    PropertyValue get(T hit);

    /**
     * Compares two hits on this property.
     *
     * The default implementation uses get() to compare the two hits. Subclasses may
     * override this method to provide a more efficient implementation.
     *
     * Note that we use Object as the type instead of Hit to save on run-time type
     * checking. We know (slash hope :-) that this method is only ever called to
     * compare Hits.
     *
     * @param a first hit
     * @param b second hit
     * @return 0 if equal, negative if a < b, positive if a > b.
     */
    @Override
    int compare(T a, T b);

    /**
     * Get the name of the property
     * @return name
     */
    String getName();

    /**
     * Serialize this HitProperty so we can deserialize it later (to pass it via
     * URL, for example)
     * 
     * @return the String representation of this HitProperty
     */
    String serialize();

    /**
     * Reverse the sort order of this hit property.
     * 
     * @return a new hit property with the sort order reversed
     */
    ResultProperty<T> reverse();

    /**
     * Is the comparison reversed?
     * 
     * @return true if it is, false if not
     */
    boolean isReverse();

    @Override
    String toString();

    /**
     * Get the names of all (sub-)properties separately.
     * 
     * @return the list
     */
    List<String> getPropNames();

    /**
     * Sort the results.
     * 
     * @param results results to sort
     * @return sorted results object
     */
    Results<T> sortResults(Results<T> results);

}
