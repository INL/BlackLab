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

import nl.inl.blacklab.index.complex.ComplexFieldProperty.SensitivitySetting;

import org.apache.lucene.document.Document;

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
 * N.B. It is crucial that you call addValue() for every token. Also, make sure you call
 * addPropertyValue() for EVERY property EVERY token. The same goes for addStartChar() and
 * addEndChar() (although, if you don't want any offsets, you need not call these).
 */
public abstract class ComplexField {

	/**
	 * Current number of tokens (actually, the number of start character positions added so far)
	 * @return the number of tokens
	 */
	public abstract int numberOfTokens();

	/**
	 * Add a property to the complex field
	 * @param name property name
	 * @deprecated use version with sensitivity parameter
	 */
	@Deprecated
	public abstract void addProperty(String name);

	/**
	 * Add a property to the complex field
	 * @param name property name
	 * @param filterAdder specifies what filters to add to the TokenStream for this property,
	 * so for example input can be lowercased, etc.
	 * @deprecated use version with sensitivity parameter
	 */
	@Deprecated
	public abstract void addProperty(String name, TokenFilterAdder filterAdder);

	/**
	 * Add a property to the complex field
	 * @param name property name
	 * @param sensitivity ways to index property, with respect to case- and
	 *   diacritics-sensitivity.
	 */
	public abstract void addProperty(String name, SensitivitySetting sensitivity);

	/**
	 * Add a property alternative to the complex field.
	 *
	 * Use this if the default SensitivitySettings are not enough for your purposes,
	 * and you want to index the same TokenStream in an additional way.
	 *
	 * @param sourceName property name
	 * @param altPostfix alternative postfix to add to the property name
	 */
	public abstract void addPropertyAlternative(String sourceName, String altPostfix);

	/**
	 * Add a property alternative to the complex field.
	 *
	 * Use this if the default SensitivitySettings are not enough for your purposes,
	 * and you want to index the same TokenStream in an additional way.
	 *
	 * @param sourceName property name
	 * @param altPostfix alternative postfix to add to the property name
	 * @param filterAdder specifies what filters to add to the TokenStream for this property,
	 * so for example input can be lowercased, etc.
	 */
	public abstract void addPropertyAlternative(String sourceName, String altPostfix,
			TokenFilterAdder filterAdder);

	/**
	 * Add an alternative for the main property to the complex field.
	 *
	 * The main property is nameless and usually contains the word form.
	 *
	 * Use this if the default SensitivitySettings are not enough for your purposes,
	 * and you want to index the same TokenStream in an additional way.
	 *
	 * @param altPostfix alternative postfix to add to the property name
	 */
	public abstract void addAlternative(String altPostfix);

	/**
	 * Add an alternative for the main property to the complex field.
	 *
	 * The main property is nameless and usually contains the word form.
	 *
	 * Use this if the default SensitivitySettings are not enough for your purposes,
	 * and you want to index the same TokenStream in an additional way.
	 *
	 * @param altPostfix alternative postfix to add to the property name
	 * @param filterAdder specifies what filters to add to the TokenStream for this property,
	 * so for example input can be lowercased, etc.
	 */
	public abstract void addAlternative(String altPostfix, TokenFilterAdder filterAdder);

	/**
	 * Add a token value to the main property for this field.
	 *
	 * The main property is nameless and usually contains the word form.
	 *
	 * @param value the token value to add
	 */
	public void addValue(String value) {
		addValue(value, 1);
	}

	/**
	 * Add a token value to the main property for this field.
	 *
	 * The main property is nameless and usually contains the word form.
	 *
	 * @param value the token value to add
	 * @param posIncrement position increment to use for this value
	 */
	public abstract void addValue(String value, int posIncrement);

	/**
	 * Add a property value.
	 *
	 * @param name name of the property to add value to
	 * @param value the token value to add
	 */
	public void addPropertyValue(String name, String value) {
		addPropertyValue(name, value, 1);
	}

	/**
	 * Add a property value.
	 *
	 * @param name name of the property to add value to
	 * @param value the token value to add
	 * @param posIncrement position increment to use for this value
	 */
	public abstract void addPropertyValue(String name, String value, int posIncrement);

	/**
	 * Add a token starting character position in the original input.
	 *
	 * @param startChar the character position to add
	 */
	public abstract void addStartChar(int startChar);

	/**
	 * Add a token endinf character position in the original input.
	 *
	 * @param endChar the character position to add
	 */
	public abstract void addEndChar(int endChar);

	/**
	 * Add all the stored values for this complex field to the Lucene document
	 *
	 * @param doc the document to add the values to
	 */
	public abstract void addToLuceneDoc(Document doc);

	/**
	 * Clear the contents of the complex field
	 */
	public abstract void clear();

//	public abstract void addTokens(TokenStream c) throws IOException;

//	public abstract void addPropertyTokens(String propertyName, TokenStream c) throws IOException;

	/** Retrieve a property [alternative] object
	 *
	 * This is used to fill the forward index.
	 *
	 * @param propName property name
	 * @return the property object
	 */
	public abstract ComplexFieldProperty getProperty(String propName);

	/**
	 * Get all the values stored for the specified property [alternative].
	 *
	 * This is used to fill the forward index.
	 *
	 * @param name name of the property (or alternative) to retrieve values for
	 * @return the list of stored values
	 */
	public abstract List<String> getPropertyValues(String name);

	/**
	 * Get all the position increments stored for the specified property [alternative].
	 *
	 * This is used to fill the forward index.
	 *
	 * @param name name of the property (or alternative) to retrieve pos. increments for
	 * @return the list of stored position increments
	 */
	public abstract List<Integer> getPropertyPositionIncrements(String name);

	public abstract ComplexFieldProperty getMainProperty();

	public abstract String getName();

}
