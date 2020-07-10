package nl.inl.blacklab.resultproperty;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexImpl;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.Hits;

/**
 * A concrete value of a HitProperty of a Hit
 */
public abstract class PropertyValue implements Comparable<Object> {
    protected static final Logger logger = LogManager.getLogger(PropertyValue.class);

    /**
     * Collator to use for string comparison while sorting/grouping
     */
    static Collator collator = BlackLabIndexImpl.defaultCollator();

    /**
     * Convert the String representation of a HitPropValue back into the
     * HitPropValue
     * 
     * @param hits our hits object
     * @param serialized the serialized object
     * @return the HitPropValue object, or null if it could not be deserialized
     */
    public static PropertyValue deserialize(Hits hits, String serialized) {
        return deserialize(hits.index(), hits.field(), serialized);
    }
    
    /**
     * Convert the String representation of a HitPropValue back into the
     * HitPropValue
     * 
     * @param index our index 
     * @param field field we're searching
     * @param serialized the serialized object
     * @return the HitPropValue object, or null if it could not be deserialized
     */
    public static PropertyValue deserialize(BlackLabIndex index, AnnotatedField field, String serialized) {

        if (PropertySerializeUtil.isMultiple(serialized))
            return PropertyValueMultiple.deserialize(index, field, serialized);

        String[] parts = PropertySerializeUtil.splitPartFirstRest(serialized);
        String type = parts[0].toLowerCase();
        String info = parts.length > 1 ? parts[1] : "";
        List<String> types = Arrays.asList("cwo", "cws", "cwsr", "dec", "int", "str", "doc");
        int typeNum = types.indexOf(type);
        switch (typeNum) {
        case 0:
            return PropertyValueContextWord.deserialize(index, field, info);
        case 1: // cws  (context words)
        case 2: // cwsr (context words, reverse order. e.g. left context)
            return PropertyValueContextWords.deserialize(index, field, info, type.equals("cwsr"));
        case 3:
            return PropertyValueDecade.deserialize(info);
        case 4:
            return PropertyValueInt.deserialize(info);
        case 5:
            return PropertyValueString.deserialize(info);
        case 6:
            return PropertyValueDoc.deserialize(index, info);
        }
        logger.debug("Unknown HitPropValue '" + type + "'");
        return null;
    }
    
    @Override
    public abstract int compareTo(Object o);

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

    /**
     * Convert the object to a String representation, for use in e.g. URLs.
     * 
     * @return the serialized object
     */
    public abstract String serialize();

    @Override
    public abstract String toString();
    
    public boolean isCompound() {
        return false;
    }
    
    public List<PropertyValue> values() {
        return null;
    }

    public List<String> propValues() {
        List<String> l = new ArrayList<>();
        if (isCompound()) {
            for (PropertyValue v : values())
                l.addAll(v.propValues());
        } else {
            l.add(toString());
        }
        return l;
    }
    
    public abstract Object value();
}
