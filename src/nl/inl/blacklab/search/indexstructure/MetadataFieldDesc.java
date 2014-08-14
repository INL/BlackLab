package nl.inl.blacklab.search.indexstructure;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import nl.inl.util.json.JSONException;
import nl.inl.util.json.JSONObject;

public class MetadataFieldDesc extends BaseFieldDesc {

	/** Conditions for using the unknown value */
	public enum UnknownCondition {
		NEVER,            // never use unknown value
		MISSING,          // use unknown value when field is missing (not when empty)
		EMPTY,            // use unknown value when field is empty (not when missing)
		MISSING_OR_EMPTY  // use unknown value when field is empty or missing
	}

	protected FieldType type = FieldType.TEXT;

	private String analyzer = "default";

	private String unknownValue = "unknown";

	private UnknownCondition unknownCondition = UnknownCondition.NEVER;

	private Map<String, Integer> values = new HashMap<String, Integer>();

	private boolean valueListComplete = true;

	public MetadataFieldDesc(String fieldName, FieldType type) {
		super(fieldName);
		this.type = type;
	}

	public MetadataFieldDesc(String fieldName, String typeName) {
		super(fieldName);
		if (typeName.equals("untokenized")) {
			this.type = FieldType.UNTOKENIZED;
		} else if (typeName.equals("tokenized") || typeName.equals("text")) {
			this.type = FieldType.TEXT;
		} else if (typeName.equals("numeric")) {
			this.type = FieldType.NUMERIC;
		} else {
			throw new RuntimeException("Unknown field type name: " + typeName);
		}
	}

	public FieldType getType() {
		return type;
	}

	public void setAnalyzer(String analyzer) {
		this.analyzer = analyzer;
	}

	public void setUnknownValue(String unknownValue) {
		this.unknownValue = unknownValue;
	}

	public void setUnknownCondition(String unknownCondition) {
		if (unknownCondition.equals("NEVER")) {
			this.unknownCondition = UnknownCondition.NEVER;
		} else if (unknownCondition.equals("MISSING")) {
			this.unknownCondition = UnknownCondition.MISSING;
		} else if (unknownCondition.equals("EMPTY")) {
			this.unknownCondition = UnknownCondition.EMPTY;
		} else if (unknownCondition.equals("MISSING_OR_EMPTY")) {
			this.unknownCondition = UnknownCondition.MISSING_OR_EMPTY;
		} else {
			throw new RuntimeException("Unknown unknown condition: " + unknownCondition);
		}
	}

	public void setUnknownCondition(UnknownCondition unknownCondition) {
		this.unknownCondition = unknownCondition;
	}

	public void setValues(JSONObject values) {
		this.values.clear();
		for (Object oValue: values.keySet()) {
			String value = (String)oValue;
			int count;
			try {
				count = Integer.parseInt(values.get(value).toString());
			} catch (NumberFormatException e) {
				count = 0;
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
			this.values.put(value, count);
		}
	}

	public void setValueListComplete(boolean valueListComplete) {
		this.valueListComplete = valueListComplete;
	}

	public String getAnalyzer() {
		return analyzer;
	}

	public String getUnknownValue() {
		return unknownValue;
	}

	public UnknownCondition getUnknownCondition() {
		return unknownCondition;
	}

	public Map<String, Integer> getValueDistribution() {
		return Collections.unmodifiableMap(values);
	}

	public boolean isValueListComplete() {
		return valueListComplete;
	}

	/**
	 * Reset the information that is dependent on input data
	 * (i.e. list of values, etc.) because we're going to
	 * (re-)index the data.
	 */
	public void resetForIndexing() {
		this.values.clear();
		valueListComplete = true;
	}

	/**
	 * Keep track of unique values of this field so we can store them in the metadata file.
	 *
	 * @param value field value
	 */
	public void addValue(String value) {
		if (values.containsKey(value))  {
			// Seen this value before; increment frequency
			values.put(value, values.get(value) + 1);
		} else {
			// New value; add it
			if (values.size() >= 50) {
				// We can't store thousands of unique values;
				// Stop storing now and indicate that there's more.
				valueListComplete = false;
			}
			values.put(value, 1);
		}
	}
}