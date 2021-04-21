package nl.inl.blacklab.resultproperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Property of some result (i.e. hit, group, groupOfGroups)
 * @param <T> type of result
 */
public interface ResultProperty<T> extends Serializable {

    /**
     * Strip sensitivity information.
     * 
     * When deserializing, we ignore sensitivity option for non-context properties.
     * Eventually, we will implement this for these properties.
     * 
     * @param info input string
     * @return stripped string
     */
    static String ignoreSensitivity(String info) {
        if (info.endsWith(":s") || info.endsWith(":i"))
            return info.substring(0, info.length() - 2);
        return info;
    }

    /**
     * Get the property value for a specific result.
     * @param hit result to get property value for
     * @return property value
     */
//    PropertyValue get(T hit);

//    PropertyValue get(int index);
    
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
//    @Override
//    int compare(T a, T b);]
//    int compare(int aIndex, int bIndex);

    /**
     * Get the name of the property
     * @return name
     */
    String name();

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
    default List<String> propNames() {
        List<String> names = new ArrayList<>();
        if (isCompound()) {
            props().forEach(prop -> names.addAll(prop.propNames()));
        } else {
            names.add(serializeReverse() + name());
        }
        return names;
    }
    
    @Override
    int hashCode();
    
    @Override
    boolean equals(Object obj);

    default boolean isCompound() {
        return false;
    }

    default List<? extends ResultProperty<T>> props() {
        return null;
    }

    String serializeReverse();

}
