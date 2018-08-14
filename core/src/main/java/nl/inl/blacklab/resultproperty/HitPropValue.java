package nl.inl.blacklab.resultproperty;

import java.text.Collator;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.util.StringUtil;

/**
 * A concrete value of a HitProperty of a Hit
 */
public abstract class HitPropValue implements ResultPropValue {
    protected static final Logger logger = LogManager.getLogger(HitPropValue.class);

    /**
     * Collator to use for string comparison while sorting/grouping
     */
    static Collator collator = StringUtil.getDefaultCollator();

    /**
     * Convert the String representation of a HitPropValue back into the
     * HitPropValue
     * 
     * @param hits our hits object
     * @param serialized the serialized object
     * @return the HitPropValue object, or null if it could not be deserialized
     */
    public static HitPropValue deserialize(Hits hits, String serialized) {
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
    public static HitPropValue deserialize(BlackLabIndex index, AnnotatedField field, String serialized) {

        if (PropValSerializeUtil.isMultiple(serialized))
            return HitPropValueMultiple.deserialize(index, field, serialized);

        String[] parts = PropValSerializeUtil.splitPartFirstRest(serialized);
        String type = parts[0].toLowerCase();
        String info = parts.length > 1 ? parts[1] : "";
        List<String> types = Arrays.asList("cwo", "cws", "dec", "int", "str", "doc");
        int typeNum = types.indexOf(type);
        switch (typeNum) {
        case 0:
            return HitPropValueContextWord.deserialize(index, field, info);
        case 1:
            return HitPropValueContextWords.deserialize(index, field, info);
        case 2:
            return HitPropValueDecade.deserialize(info);
        case 3:
            return HitPropValueInt.deserialize(info);
        case 4:
            return HitPropValueString.deserialize(info);
        case 5:
            return HitPropValueDoc.deserialize(index, info);
        }
        logger.debug("Unknown HitPropValue '" + type + "'");
        return null;
    }
}
