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

import java.util.List;

import org.apache.lucene.document.Document;

/**
 * A property in a complex field. See ComplexFieldImpl for details.
 */
public abstract class ComplexFieldProperty {

	/** How a property is to be indexed with respect to case and diacritics sensitivity. */
	public enum SensitivitySetting {
		ONLY_SENSITIVE,                 // only index case- and diacritics-sensitively
		ONLY_INSENSITIVE,               // only index case- and diacritics-insensitively
		SENSITIVE_AND_INSENSITIVE,      // case+diac sensitive as well as case+diac insensitive
		CASE_AND_DIACRITICS_SEPARATE	// all four combinations (sens, insens, case-insens, diac-insens)
	}

	/**
	 * Add a value to the property.
	 * @param value value to add
	 */
	final public void addValue(String value) {
		addValue(value, 1);
	}

	/**
	 * Add a value with a specific position increment to the property.
	 * @param value value to add
	 * @param increment number of tokens past the previous value
	 */
	public abstract void addValue(String value, int increment);

	public abstract void addToLuceneDoc(Document doc, String fieldName, List<Integer> startChars,
			List<Integer> endChars);

	public abstract void clear();

	public abstract void addAlternative(String destName, TokenFilterAdder filterAdder);

	public abstract List<String> getValues();

	public abstract List<Integer> getPositionIncrements();

}
