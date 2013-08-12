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


/**
 * A property in a complex field. See ComplexFieldImpl for details.
 *
 * This subclass adds the recycling of values per document. This makes sure that large documents can
 * still fit in memory.
 *
 * @deprecated instantiate ComplexFieldProperty directly instead
 */
@Deprecated
class ComplexFieldPropertyImplLargeDoc extends ComplexFieldPropertyImplSimple {

	/**
	 * Construct a ComplexFieldProperty object with the default alternative
	 * @param name property name
	 * @param includeOffsets whether to include character offsets in the main alternative
	 * @deprecated Use constructor with SensitivitySetting parameter
	 */
	@Deprecated
	public ComplexFieldPropertyImplLargeDoc(String name, boolean includeOffsets) {
		super(name, includeOffsets);
	}

	/**
	 * Construct a ComplexFieldProperty object with the default alternative
	 * @param name property name
	 * @param filterAdder what filter(s) to add, or null if none
	 * @param includeOffsets whether to include character offsets in the main alternative
	 * @deprecated Use constructor with SensitivitySetting parameter
	 */
	@Deprecated
	public ComplexFieldPropertyImplLargeDoc(String name, TokenFilterAdder filterAdder,
			boolean includeOffsets) {
		super(name, filterAdder, includeOffsets);
	}

	/**
	 * Construct a ComplexFieldProperty object with the default alternative
	 * @param name property name
	 * @param sensitivity ways to index this property, with respect to case- and
	 *   diacritics-sensitivity.
	 * @param includeOffsets whether to include character offsets in the main alternative
	 */
	public ComplexFieldPropertyImplLargeDoc(String name, SensitivitySetting sensitivity,
			boolean includeOffsets) {
		super(name, sensitivity, includeOffsets);
	}
}
