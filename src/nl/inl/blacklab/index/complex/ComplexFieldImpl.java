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
 *
 * @deprecated instantiate ComplexField directly instead
 */
@Deprecated
public class ComplexFieldImpl extends ComplexField {
	/** @param name
	 * @param filterAdder
	 * @deprecated use constructor with sensitivity parameter */
	@Deprecated
	public ComplexFieldImpl(String name, TokenFilterAdder filterAdder) {
		this(name, null, filterAdder, true);
	}

	/** @param name
	 * @param mainProperty
	 * @param filterAdder
	 * @deprecated use constructor with sensitivity parameter */
	@Deprecated
	public ComplexFieldImpl(String name, String mainProperty, TokenFilterAdder filterAdder) {
		this(name, mainProperty, filterAdder, true);
	}

	/** @param name
	 * @param filterAdder
	 * @param includeOffsets
	 * @deprecated use constructor with sensitivity parameter */
	@Deprecated
	public ComplexFieldImpl(String name, TokenFilterAdder filterAdder, boolean includeOffsets) {
		this(name, null, filterAdder, includeOffsets);
	}

	/** @param name
	 * @param mainPropertyName
	 * @param filterAdder
	 * @param includeOffsets
	 * @deprecated use constructor with sensitivity parameter */
	@Deprecated
	public ComplexFieldImpl(String name, String mainPropertyName, TokenFilterAdder filterAdder, boolean includeOffsets) {
		super(name, mainPropertyName, filterAdder, includeOffsets);
	}

	/**
	 * Construct a ComplexField object with a main property
	 * @param name field name
	 * @param mainPropertyName main property name
	 * @param sensitivity ways to index main property, with respect to case- and
	 *   diacritics-sensitivity.
	 */
	public ComplexFieldImpl(String name, String mainPropertyName, SensitivitySetting sensitivity) {
		super(name, mainPropertyName, sensitivity);
	}

}