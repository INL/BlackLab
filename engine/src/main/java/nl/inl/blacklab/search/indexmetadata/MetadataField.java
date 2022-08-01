package nl.inl.blacklab.search.indexmetadata;

import java.util.List;
import java.util.Map;

/** A metadata field. */
public interface MetadataField extends Field {

	/**
	 * Should we store the values for this field?
	 *
	 * We're moving away from storing values separately, because we can just
	 * use DocValues to find the values when we need them.
	 *
	 * @param keepTrackOfValues whether or not to store values here
	 */
	default void setKeepTrackOfValues(boolean keepTrackOfValues) { }

    String uiType();

	FieldType type();

	List<String> displayOrder();

	String analyzerName();

	String unknownValue();

	UnknownCondition unknownCondition();

	Map<String, Integer> valueDistribution();

	ValueListComplete isValueListComplete();

    Map<String, String> displayValues();

	String group();

    @Override
    default String contentsFieldName() {
        return name();
    }
	
}
