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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

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

	private IntArrayList start = new IntArrayList();

	private IntArrayList end = new IntArrayList();

	private String fieldName;

	private ComplexFieldProperty mainProperty;

	private Set<String> noForwardIndexProps = Collections.emptySet();

	public void setNoForwardIndexProps(Set<String> noForwardIndexProps) {
		this.noForwardIndexProps = noForwardIndexProps;
	}

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
		if (noForwardIndexProps.contains(name)) {
			p.setForwardIndex(false);
		}
		properties.put(name, p);
		return p;
	}

	public ComplexFieldProperty addProperty(String name, SensitivitySetting sensitivity) {
		return addProperty(name, sensitivity, false);
	}

	public void addStartChar(int startChar) {
		start.add(startChar);
	}

	public void addEndChar(int endChar) {
		end.add(endChar);
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

	public ComplexFieldProperty getProperty(String name) {
		ComplexFieldProperty p = properties.get(name);
		if (p == null)
			throw new IllegalArgumentException("Undefined property '" + name + "'");
		return p;
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


}
