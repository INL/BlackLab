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

import java.util.HashMap;
import java.util.Map;

/**
 * A property in a complex field. See ComplexFieldImpl for details.
 *
 * This subclass adds the recycling of values per document. This makes sure that large documents can
 * still fit in memory.
 */
class ComplexFieldPropertyImplLargeDoc extends ComplexFieldPropertyImplSimple {
	private Map<String, String> storedValues = new HashMap<String, String>();

	public ComplexFieldPropertyImplLargeDoc(String name, boolean includeOffsets) {
		super(name, includeOffsets);
	}

	public ComplexFieldPropertyImplLargeDoc(String name, TokenFilterAdder filterAdder,
			boolean includeOffsets) {
		super(name, filterAdder, includeOffsets);
	}

	@Override
	public void addValue(String value, int increment) {
		// Make sure we don't keep duplicates of strings in memory, but re-use earlier instances.
		String storedValue = storedValues.get(value);
		if (storedValue == null) {
			storedValues.put(value, value);
			storedValue = value;
		}
		super.addValue(storedValue, increment);
	}

	@Override
	public void clear() {
		super.clear();

		// We don't need to clear the cached values between documents, just re-use them all the
		// time.
		// storedValues.clear();
	}

}
