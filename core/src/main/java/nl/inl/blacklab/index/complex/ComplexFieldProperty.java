/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.index.complex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.util.BytesRef;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import nl.inl.util.CollUtil;

/**
 * A property in a complex field. See ComplexField for details.
 */
public class ComplexFieldProperty {

	/** Maximum length a value is allowed to be. */
	private static final int MAXIMUM_VALUE_LENGTH = 1000;

	/** The field type for properties without character offsets */
	private static FieldType tokenStreamFieldNoOffsets;

	/** The field type for properties with character offsets (the main alternative) */
	private static FieldType tokenStreamFieldWithOffsets;

	static {
		FieldType type = tokenStreamFieldNoOffsets = new FieldType();
		type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
		type.setTokenized(true);
		type.setOmitNorms(true);
		type.setStored(false);
		type.setStoreTermVectors(true);
		type.setStoreTermVectorPositions(true);
		type.setStoreTermVectorOffsets(false);
		type.freeze();

		type = tokenStreamFieldWithOffsets = new FieldType(tokenStreamFieldNoOffsets);
		type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
		type.setStoreTermVectorOffsets(true);
		type.freeze();
	}

	/** How a property is to be indexed with respect to case and diacritics sensitivity. */
	public enum SensitivitySetting {
		ONLY_SENSITIVE,                 // only index case- and diacritics-sensitively
		ONLY_INSENSITIVE,               // only index case- and diacritics-insensitively
		SENSITIVE_AND_INSENSITIVE,      // case+diac sensitive as well as case+diac insensitive
		CASE_AND_DIACRITICS_SEPARATE	// all four combinations (sens, insens, case-insens, diac-insens)
	}

	protected boolean includeOffsets;

	/**
	 *  Term values for this property.
	 */
	protected List<String> values = new ArrayList<>();

	/** Token position increments. This allows us to index multiple terms at a single token position (just
	 *  set the token increments of the additional tokens to 0). */
	protected IntArrayList increments = new IntArrayList();

	/**
	 * Payloads for this property, if any.
	 */
	protected List<BytesRef> payloads = null;

	/** Position of the last value added
	 */
	protected int lastValuePosition = -1;

	/**
	 * A property may be indexed in different ways (alternatives). This specifies names and filters
	 * for each way.
	 */
	private Map<String, TokenFilterAdder> alternatives = new HashMap<>();

	/** The main alternative (the one that gets character offsets if desired) */
	private String mainAlternative;

	/** The property name */
	private String propName;

	/** Does this property get its own forward index? */
	private boolean hasForwardIndex = true;

	/** To keep memory usage down, we make sure we only store 1 copy of each string value */
	private Map<String, String> storedValues = new HashMap<>();

	/**
	 * Construct a ComplexFieldProperty object with the default alternative
	 * @param name property name
	 * @param sensitivity ways to index this property, with respect to case- and
	 *   diacritics-sensitivity.
	 * @param includeOffsets whether to include character offsets in the main alternative
	 * @param includePayloads will this property include payloads?
	 */
	public ComplexFieldProperty(String name, SensitivitySetting sensitivity,
			boolean includeOffsets, boolean includePayloads) {
		super();
		propName = name;

		mainAlternative = null;
		if (sensitivity != SensitivitySetting.ONLY_INSENSITIVE) {
			// Add sensitive alternative
			alternatives.put(ComplexFieldUtil.SENSITIVE_ALT_NAME, null);
			mainAlternative = ComplexFieldUtil.SENSITIVE_ALT_NAME;
		}
		if (sensitivity != SensitivitySetting.ONLY_SENSITIVE) {
			// Add insensitive alternative
			alternatives.put(ComplexFieldUtil.INSENSITIVE_ALT_NAME, new DesensitizerAdder(true, true));
			if (mainAlternative == null)
				mainAlternative = ComplexFieldUtil.INSENSITIVE_ALT_NAME;
		}
		if (sensitivity == SensitivitySetting.CASE_AND_DIACRITICS_SEPARATE) {
			// Add case-insensitive and diacritics-insensitive alternatives
			alternatives.put(ComplexFieldUtil.CASE_INSENSITIVE_ALT_NAME, new DesensitizerAdder(true, false));
			alternatives.put(ComplexFieldUtil.DIACRITICS_INSENSITIVE_ALT_NAME, new DesensitizerAdder(false, true));
		}

		this.includeOffsets = includeOffsets;
		if (includePayloads)
			payloads = new ArrayList<>();
	}

	TokenStream getTokenStream(String altName, IntArrayList startChars, IntArrayList endChars) {
		TokenStream ts;
		if (includeOffsets) {
			ts = new TokenStreamWithOffsets(values, increments, startChars, endChars);
		} else {
			ts = new TokenStreamFromList(values, increments, payloads);
		}
		TokenFilterAdder filterAdder = alternatives.get(altName);
		if (filterAdder != null)
			return filterAdder.addFilters(ts);
		return ts;
	}

	FieldType getTermVectorOptionFieldType(String altName) {
		// Main alternative of a property may get character offsets
		// (if it's the main property of a complex field)
		if (includeOffsets && altName.equals(mainAlternative))
			return tokenStreamFieldWithOffsets;

		// Named alternatives and additional properties don't get character offsets
		return tokenStreamFieldNoOffsets;
	}

	public void addToLuceneDoc(Document doc, String fieldName, IntArrayList startChars,
			IntArrayList endChars) {
		for (String altName : alternatives.keySet()) {
			//doc.add(new Field(ComplexFieldUtil.propertyField(fieldName, propName, altName),
			//		getTokenStream(altName, startChars, endChars), getTermVectorOption(altName)));
			doc.add(new Field(ComplexFieldUtil.propertyField(fieldName, propName, altName),
					getTokenStream(altName, startChars, endChars), getTermVectorOptionFieldType(altName)));
		}
	}

	public List<String> getValues() {
		return Collections.unmodifiableList(values);
	}

	public List<Integer> getPositionIncrements() {
		return CollUtil.toList(increments);
	}

	public int lastValuePosition() {
		return lastValuePosition;
	}

	public String getName() {
		return propName;
	}

	public boolean hasForwardIndex() {
		return hasForwardIndex;
	}

	public void setForwardIndex(boolean b) {
		hasForwardIndex = b;
	}

	/**
	 * Add a value to the property.
	 * @param value value to add
	 */
	final public void addValue(String value) {
		addValue(value, 1);
	}

	/**
	 * Add a value to the property.
	 *
	 * @param value the value to add
	 * @param increment number of tokens distance from the last token added
	 */
	public void addValue(String value, int increment) {
		if (value.length() > MAXIMUM_VALUE_LENGTH) {
			// Let's keep a sane maximum value length.
			// (Lucene's is 32766, but we don't want to go that far)
			value = value.substring(0, MAXIMUM_VALUE_LENGTH);
		}

		// Make sure we don't keep duplicates of strings in memory, but re-use earlier instances.
		String storedValue = storedValues.get(value);
		if (storedValue == null) {
			storedValues.put(value, value);
			storedValue = value;
		}

		// Special case: if previous value was the empty string and position increment is 0,
		// replace the previous value. This is convenient to keep all the properties synched
		// up while indexing (by adding a dummy empty string if we don't have a value for a
		// property), while still being able to add a value to this position later (for example,
		// when we encounter an XML close tag.
		int lastIndex = values.size() - 1;
		if (lastIndex >= 0 && values.get(lastIndex).length() == 0 && increment == 0) {
			// Change the last value but don't change the increment.
			values.set(lastIndex, storedValue);
			return;
		}

		values.add(storedValue);
		increments.add(increment);
		lastValuePosition += increment; // keep track of position of last token

	}

	/**
	 * Add a value to the property at a specific position.
	 *
	 * Please note that if you add a value beyond the current position,
	 * the next call to addValue() will add from this new position! This
	 * is not an issue if you add a value at a lower position (that
	 * operation doesn't change the current last token position used
	 * for addValue()).
	 *
	 * @param value the value to add
	 * @param position the position to put it at
	 * @return new position of the last token, in case it changed.
	 */
	public int addValueAtPosition(String value, int position) {
		if (value.length() > MAXIMUM_VALUE_LENGTH) {
			// Let's keep a sane maximum value length.
			// (Lucene's is 32766, but we don't want to go that far)
			value = value.substring(0, MAXIMUM_VALUE_LENGTH);
		}

		if (position >= lastValuePosition) {
			// Beyond the last position; regular addValue()
			addValue(value, position - lastValuePosition);
		} else {
			// Make sure we don't keep duplicates of strings in memory, but re-use earlier instances.
			String storedValue = storedValues.get(value);
			if (storedValue == null) {
				storedValues.put(value, value);
				storedValue = value;
			}

			// Before the last position.
			// Find the index where the value should be inserted.
			int curPos = this.lastValuePosition;
			for (int i = values.size() - 1; i >= 0; i--) {
				if (curPos <= position)  {
					// Value should be inserted after this index.
					int n = i + 1;
					values.add(n, storedValue);
					int incr = position - curPos;
					increments.addAtIndex(n, incr);
					if (increments.size() > n + 1 && incr > 0) {
						// Inserted value wasn't the last value, so the
						// increment for the value after this is now wrong;
						// correct it.
						increments.set(n + 1, increments.get(n + 1) - incr);
					}
				}
				curPos -= increments.get(i); // go to previous value position
			}
		}

		return lastValuePosition;
	}

	public void addPayload(BytesRef payload) {
		payloads.add(payload);
	}

	public int getLastValueIndex() {
		return values.size() - 1;
	}

	public void setPayloadAtIndex(int i, BytesRef payload) {
		payloads.set(i, payload);
	}

	public void clear() {
		values.clear();
		increments.clear();
		lastValuePosition = -1;

		// In theory, we don't need to clear the cached values between documents, but
		// for large data sets, this would keep getting larger and larger, so we do
		// it anyway.
		storedValues.clear();
	}

	public boolean hasPayload() {
		return payloads != null;
	}
}
