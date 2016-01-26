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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;

import nl.inl.blacklab.index.complex.ComplexFieldProperty.SensitivitySetting;


/**
 * A complex field is like a Lucene field, but in addition to its "normal" value, it can have
 * multiple properties per word (not just a single token). The properties might be "headword", "pos"
 * (part of speech), "namedentity" (whether or not the word is (part of) a named entity like a
 * location or place), etc.
 *
 * Complex fields are implemented by indexing a field in Lucene for each property. For example, if
 * complex field "contents" has properties "headword" and "pos", there would be 3 Lucene fields for
 * the complex field: "contents", "contents__headword" and "contents__pos".
 *
 * The main field ("contents" in the above example) may include offset information if you want (e.g.
 * for highlighting). All Lucene fields will include position information (for use with
 * SpanQueries).
 *
 * N.B. It is crucial that everything stays in synch, so you should call all the appropriate add*()
 * methods for each property and each token, or use the correct position increments to keep everything
 * synched up. The same goes for addStartChar() and addEndChar() (although, if you don't want any
 * offsets, you need not call these).
 */
public class ComplexField {

	private Map<String, ComplexFieldProperty> properties = new HashMap<>();

	private List<Integer> start = new ArrayList<>();

	private List<Integer> end = new ArrayList<>();

	private String fieldName;

	private ComplexFieldProperty mainProperty;

	/**
	 * Construct a ComplexField object with a main property
	 * @param name field name
	 * @param mainPropertyName main property name
	 * @param sensitivity ways to index main property, with respect to case- and
	 *   diacritics-sensitivity.
	 * @param mainPropHasPayloads does the main property have payloads?
	 */
	public ComplexField(String name, String mainPropertyName, SensitivitySetting sensitivity, boolean mainPropHasPayloads) {
		boolean includeOffsets = true;
		fieldName = name;
		if (mainPropertyName == null)
			mainPropertyName = ComplexFieldUtil.getDefaultMainPropName();
		mainProperty = new ComplexFieldProperty(mainPropertyName, sensitivity, includeOffsets, mainPropHasPayloads);
		properties.put(mainPropertyName, mainProperty);
	}

	public int numberOfTokens() {
		return start.size();
	}

	public ComplexFieldProperty addProperty(String name, SensitivitySetting sensitivity, boolean includePayloads) {
		ComplexFieldProperty p = new ComplexFieldProperty(name, sensitivity, false, includePayloads);
		properties.put(name, p);
		return p;
	}

	public ComplexFieldProperty addProperty(String name, SensitivitySetting sensitivity) {
		return addProperty(name, sensitivity, false);
	}

	/**
	 * @param propName
	 * @param altName
	 * @deprecated use SensitivitySetting, or create additional properties
	 */
	@Deprecated
	public void addPropertyAlternative(String propName, String altName) {
		addPropertyAlternative(propName, altName, null);
	}

	/**
	 * @param propName
	 * @param altName
	 * @param filterAdder
	 * @deprecated use SensitivitySetting, or create additional properties
	 */
	@Deprecated
	public void addPropertyAlternative(String propName, String altName, TokenFilterAdder filterAdder) {
		ComplexFieldProperty p = properties.get(propName);
		if (p == null)
			throw new RuntimeException("Undefined property '" + propName + "'");
		p.addAlternative(altName, filterAdder);
	}

	public void addStartChar(int startChar) {
		start.add(startChar);
	}

	public void addEndChar(int endChar) {
		end.add(endChar);
	}

	/**
	 * Add a token value to the main property for this field.
	 *
	 * The main property usually contains the word form.
	 *
	 * @param value the token value to add
	 * @deprecated use getMainProperty().addValue()
	 */
	@Deprecated
	public void addValue(String value) {
		addValue(value, 1);
	}

	/**
	 * Add a value to the main property.
	 * @param value the value
	 * @param posIncr the position increment.
	 * @deprecated use getMainProperty().addValue()
	 */
	@Deprecated
	public void addValue(String value, int posIncr) {
		mainProperty.addValue(value, posIncr);
	}

	public void addToLuceneDoc(Document doc) {
		for (ComplexFieldProperty p : properties.values()) {
			p.addToLuceneDoc(doc, fieldName, start, end);
		}

		// Add number of tokens in complex field as a stored field,
		// because we need to be able to find this property quickly
		// for SpanQueryNot.
		// (Also note that this is the actual number of words + 1,
		//  because we always store a dummy "closing token" at the end
		//  that doesn't contain a word but may contain trailing punctuation)
		doc.add(new IntField(ComplexFieldUtil.lengthTokensField(fieldName), numberOfTokens(), Field.Store.YES));
	}

	public void clear() {
		start.clear();
		end.clear();
		for (ComplexFieldProperty p : properties.values()) {
			p.clear();
		}
	}

	/**
	 * @param altName
	 * @deprecated use SensitivitySetting, or create additional properties
	 */
	@Deprecated
	public void addAlternative(String altName) {
		mainProperty.addAlternative(altName, null);
	}

	/**
	 * @param altName
	 * @param filterAdder
	 * @deprecated use SensitivitySetting, or create additional properties
	 */
	@Deprecated
	public void addAlternative(String altName, TokenFilterAdder filterAdder) {
		mainProperty.addAlternative(altName, filterAdder);
	}

	public ComplexFieldProperty getProperty(String name) {
		ComplexFieldProperty p = properties.get(name);
		if (p == null)
			throw new RuntimeException("Undefined property '" + name + "'");
		return p;
	}

	/**
	 * Get the list of values for a property
	 * @param name property name
	 * @return list of values
	 * @deprecated ask the property object itself instead
	 */
	@Deprecated
	public List<String> getPropertyValues(String name) {
		ComplexFieldProperty p = properties.get(name);
		if (p == null)
			throw new RuntimeException("Undefined property '" + name + "'");
		return p.getValues();
	}

	/**
	 * Get the list of position increments for a property
	 * @param name property name
	 * @return list of increments
	 * @deprecated ask the property object itself instead
	 */
	@Deprecated
	public List<Integer> getPropertyPositionIncrements(String name) {
		ComplexFieldProperty p = properties.get(name);
		if (p == null)
			throw new RuntimeException("Undefined property '" + name + "'");
		return p.getPositionIncrements();
	}

	public ComplexFieldProperty getMainProperty() {
		return mainProperty;
	}

	public String getName() {
		return fieldName;
	}

	public Collection<ComplexFieldProperty> getProperties() {
		return properties.values();
	}

	/**
	 * Add a value to a property
	 * @param name property name
	 * @param value the value to add
	 * @param posIncr position increment
	 * @deprecated keep a reference to the property object and add values to that.
	 */
	@Deprecated
	public void addPropertyValue(String name, String value, int posIncr) {
		ComplexFieldProperty p = properties.get(name);
		if (p == null)
			throw new RuntimeException("Undefined property '" + name + "'");
		p.addValue(value, posIncr);
	}

	/**
	 * Add a value to a property
	 * @param name property name
	 * @param value the value to add
	 * @deprecated keep a reference to the property object and add values to that.
	 */
	@Deprecated
	public void addPropertyValue(String name, String value) {
		addPropertyValue(name, value, 1);
	}


}
