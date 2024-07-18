package nl.inl.blacklab.resultproperty;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * A concrete value of a HitProperty of a Hit
 */
public abstract class PropertyValue implements Comparable<Object> {
    protected static final Logger logger = LogManager.getLogger(PropertyValue.class);

    /**
     * Collator to use for string comparison while sorting/grouping
     */
    static final Collator collator = BlackLab.defaultCollator();

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
        if (serialized == null || serialized.isEmpty())
            return null;

        if (PropertySerializeUtil.isMultiple(serialized))
            return PropertyValueMultiple.deserialize(index, field, serialized);

        List<String> parts = PropertySerializeUtil.splitPartsList(serialized);
        String type = parts.get(0).toLowerCase();
        List<String> infos = parts.subList(1, parts.size());
        switch (type) {
        case "cwo": // DEPRECATED, to be removed
            return PropertyValueContextWords.deserializeSingleWord(index, field, infos);
        case "cws": // cws  (context words)
        case "cwsr": // cwsr (context words, reverse order. e.g. left context)
            return PropertyValueContextWords.deserialize(index, field, infos, type.equals("cwsr"));
        case "dec":
            return PropertyValueDecade.deserialize(infos.isEmpty() ? "unknown" : infos.get(0));
        case "int":
            return PropertyValueInt.deserialize(infos.isEmpty() ? "-1" : infos.get(0));
        case "str":
            return new PropertyValueString(infos.isEmpty() ? "" : infos.get(0));
        case "doc":
            return PropertyValueDoc.deserialize(index, infos.isEmpty() ? "NO_DOC_ID_SPECIFIED" : infos.get(0));
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

    /**
     * If this is PropertyValueMultiple, get the list of PropertyValues.
     *
     * @return list of values or null if it is not PropertyValueMultiple
     * @deprecated use valuesList() which always returns a list, never null
     */
    @Deprecated
    public List<PropertyValue> values() {
        return null;
    }

    /**
     * Return the list of values.
     *
     * If this is PropertValueMultiple, the list will contain multiple items,
     * otherwise just 1.
     *
     * @return list of values
     */
    public List<PropertyValue> valuesList() {
        return isCompound() ? values() : List.of(this);
    }

    public List<String> propValues() {
        List<String> l = new ArrayList<>();
        if (isCompound()) {
            for (PropertyValue v: valuesList())
                l.addAll(v.propValues());
        } else {
            l.add(toString());
        }
        return l;
    }
    
    public abstract Object value();
}
