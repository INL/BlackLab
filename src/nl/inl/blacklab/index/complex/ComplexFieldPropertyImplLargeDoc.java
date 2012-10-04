/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
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
	public void addValue(String value) {
		// Make sure we don't keep duplicates of strings in memory, but re-use earlier instances.
		String storedValue = storedValues.get(value);
		if (storedValue == null) {
			storedValues.put(value, value);
			storedValue = value;
		}
		values.add(storedValue);
	}

	@Override
	public void clear() {
		super.clear();

		// We don't need to clear the cached values between documents, just re-use them all the
		// time.
		// storedValues.clear();
	}

}
