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
/**
 *
 */
package nl.inl.blacklab.index.complex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.TermVector;

/**
 * A property in a complex field. See ComplexFieldImpl for details.
 */
class ComplexFieldPropertyImplSimple extends ComplexFieldProperty {
	protected boolean includeOffsets;

	/**
	 *  Term values for this property.
	 */
	protected List<String> values = new ArrayList<String>();

	/** Token position increments. This allows us to index multiple terms at a single token position (just
	 *  set the token increments of the additional tokens to 0). */
	protected List<Integer> increments = new ArrayList<Integer>();

	/**
	 * A property may be indexed in different ways (alternatives). This specifies names and filters
	 * for each way.
	 */
	private Map<String, TokenFilterAdder> alternatives = new HashMap<String, TokenFilterAdder>();

	/**
	 * The property name
	 */
	private String propName;

	public ComplexFieldPropertyImplSimple(String name, boolean includeOffsets) {
		this(name, null, includeOffsets);
	}

	public ComplexFieldPropertyImplSimple(String name, TokenFilterAdder filterAdder,
			boolean includeOffsets) {
		super();
		propName = name;
		alternatives.put("", filterAdder);
		this.includeOffsets = includeOffsets;
	}

	@Override
	public void addValue(String value, int increment) {

		// Special case: if previous value was the empty string and position increment is 0,
		// replace the previous value. This is convenient to keep all the properties synched
		// up while indexing (by adding a dummy empty string if we don't have a value for a
		// property), while still being able to add a value to this position later (for example,
		// when we encounter an XML close tag.
		int lastIndex = values.size() - 1;
		if (lastIndex >= 0 && values.get(lastIndex).length() == 0 && increment == 0) {
			// Change the last value but don't change the increment.
			values.set(lastIndex, value);
			return;
		}

		values.add(value);
		increments.add(increment);
	}

	TokenStream getTokenStream(String altName, List<Integer> startChars, List<Integer> endChars) {
		TokenStream ts;
		if (includeOffsets)
			ts = new TokenStreamWithOffsets(values, increments, startChars, endChars);
		else
			ts = new TokenStreamFromList(values, increments);
		TokenFilterAdder filterAdder = alternatives.get(altName);
		if (filterAdder != null)
			return filterAdder.addFilters(ts);
		return ts;
	}

	TermVector getTermVectorOption() {
		return includeOffsets ? TermVector.WITH_POSITIONS_OFFSETS : TermVector.WITH_POSITIONS;
	}

	@Override
	public void addToLuceneDoc(Document doc, String fieldName, List<Integer> startChars,
			List<Integer> endChars) {
		for (String altName : alternatives.keySet()) {
			doc.add(new Field(ComplexFieldUtil.fieldName(fieldName, propName, altName),
					getTokenStream(altName, startChars, endChars), getTermVectorOption()));
		}
	}

	@Override
	public void clear() {
		values.clear();
		increments.clear();
	}

	@Override
	public void addAlternative(String altName, TokenFilterAdder filterAdder) {
		alternatives.put(altName, filterAdder);
	}

	@Override
	public List<String> getValues() {
		return Collections.unmodifiableList(values);
	}

	@Override
	public List<Integer> getPositionIncrements() {
		return Collections.unmodifiableList(increments);
	}

}
