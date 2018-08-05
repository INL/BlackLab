package nl.inl.blacklab.search.indexmetadata.nint;

import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.UnknownCondition;
import nl.inl.blacklab.search.indexmetadata.ValueListComplete;

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

}
