package nl.inl.blacklab.search.indexmetadata;

import java.util.List;
import java.util.Map;

/** A metadata field. */
public interface MetadataField extends Field {
	
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
