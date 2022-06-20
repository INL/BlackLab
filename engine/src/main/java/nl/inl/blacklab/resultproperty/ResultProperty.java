package nl.inl.blacklab.resultproperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.util.PropertySerializeUtil;

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

    static String serializeMultiple(boolean reverse, List<? extends ResultProperty<?>> properties) {
        String[] values = new String[properties.size()];
        for (int i = 0; i < properties.size(); i++) {
            values[i] = properties.get(i).serialize();
        }
        return (reverse ? "-(" : "") + PropertySerializeUtil.combineMultiple(values) + (reverse ? ")" : "");
    }

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
