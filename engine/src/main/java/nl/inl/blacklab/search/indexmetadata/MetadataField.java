package nl.inl.blacklab.search.indexmetadata;

import java.util.List;
import java.util.Map;

/** A metadata field. */
public interface MetadataField extends Field {

    /**
     * @deprecated use {@link #custom()} and .get("uiType", "") instead
     */
    @Deprecated
    String uiType();

	FieldType type();

    /**
     * @deprecated use {@link #custom()} and .get("displayOrder", Collections.emptyList()) instead
     */
    @Deprecated
    List<String> displayOrder();

	String analyzerName();

    /**
     * @deprecated use {@link #custom()} and .get("unknownValue", "") instead
     */
    @Deprecated
	String unknownValue();

    /**
     * @deprecated use {@link #custom()} and .get("unknownCondition", "") instead
     */
    @Deprecated
	UnknownCondition unknownCondition();

	Map<String, Integer> valueDistribution();

	ValueListComplete isValueListComplete();

    /**
     * @deprecated use {@link #custom()} and .get("displayValues", Collections.emptyMap()) instead
     */
    @Deprecated
    Map<String, String> displayValues();

    @Override
    default String contentsFieldName() {
        return name();
    }

}
