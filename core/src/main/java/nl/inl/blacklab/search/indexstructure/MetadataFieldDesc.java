package nl.inl.blacklab.search.indexstructure;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

public class MetadataFieldDesc extends BaseFieldDesc {

	/** Conditions for using the unknown value */
	public enum UnknownCondition {
		NEVER,            // never use unknown value
		MISSING,          // use unknown value when field is missing (not when empty)
		EMPTY,            // use unknown value when field is empty (not when missing)
		MISSING_OR_EMPTY  // use unknown value when field is empty or missing
	}

	/** Possible value for valueListComplete */
	public enum ValueListComplete {
		UNKNOWN, // not known yet; is treated as 'no'
		YES,
		NO
	}

	/**
	 * The field type: text, untokenized or numeric.
	 */
	protected FieldType type = FieldType.TEXT;

	/**
	 * The analyzer to use for indexing and querying this field.
	 */
	private String analyzer = "DEFAULT";

	/**
	 * When value is missing or empty this value may be used instead
	 * (whether it is depends or unknownCondition).
	 */
	private String unknownValue = "unknown";

	/**
	 * When is the unknown value for this field used?
	 */
	private UnknownCondition unknownCondition = UnknownCondition.NEVER;

	/**
	 * The values this field can have. Note that this may not be the complete list;
	 * check valueListComplete.
	 */
	private Map<String, Integer> values = new HashMap<>();

	/**
	 * Whether or not all values are stored here.
	 */
	private ValueListComplete valueListComplete = ValueListComplete.UNKNOWN;

	/**
	 * The field group this field belongs to. Can be used by a generic
	 * search application to generate metadata search interface.
	 */
	private String group;

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
			throw new IllegalArgumentException("Unknown field type name: " + typeName);
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
			throw new IllegalArgumentException("Unknown unknown condition: " + unknownCondition);
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
		this.valueListComplete = valueListComplete ? ValueListComplete.YES : ValueListComplete.NO;
	}

	public String getAnalyzerName() {
		return analyzer;
	}

	/*
	public String getAnalyzerClass() {
		if (analyzer.equalsIgnoreCase("DEFAULT")) {
			return
		}
		return analyzer;
	}
	*/

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
		// NOTE: UNKNOWN means addValue() hasn't been called. This is reported as NO
		//  because some indices may not have values stored. Fields that actually don't
		//  have any values should be rare and aren't very useful, so this seems like
		//  an okay choice.
		return valueListComplete == ValueListComplete.YES;
	}

	/**
	 * Reset the information that is dependent on input data
	 * (i.e. list of values, etc.) because we're going to
	 * (re-)index the data.
	 */
	public void resetForIndexing() {
		this.values.clear();
		valueListComplete = ValueListComplete.UNKNOWN;
	}

	/**
	 * Keep track of unique values of this field so we can store them in the metadata file.
	 *
	 * @param value field value
	 */
	public void addValue(String value) {
		// If we've seen a value, assume we'll get to see all values;
		// when it turns out there's too many or they're too long,
		// we'll change the value to NO.
		if (valueListComplete == ValueListComplete.UNKNOWN)
			valueListComplete = ValueListComplete.YES;

		if (value.length() > 100) {
			// Value too long to store.
			valueListComplete = ValueListComplete.NO;
			return;
		}
		if (values.containsKey(value))  {
			// Seen this value before; increment frequency
			values.put(value, values.get(value) + 1);
		} else {
			// New value; add it
			if (values.size() >= 50) {
				// We can't store thousands of unique values;
				// Stop storing now and indicate that there's more.
				valueListComplete = ValueListComplete.NO;
				return;
			}
			values.put(value, 1);
		}
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public String getGroup() {
		return group;
	}
}