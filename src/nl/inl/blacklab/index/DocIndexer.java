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
package nl.inl.blacklab.index;

import java.util.Map;

import nl.inl.blacklab.search.indexstructure.FieldType;

/**
 * Indexes a file.
 */
public interface DocIndexer {
	/**
	 * Thrown when the maximum number of documents has been reached
	 */
	public static class MaxDocsReachedException extends RuntimeException {
		//
	}

	/**
	 * Index documents contained in a file.
	 *
	 * @throws Exception
	 */
	public void index() throws Exception;

	/**
	 * Set a parameter for this indexer (such as which type of metadata block to process)
	 * @param name parameter name
	 * @param value parameter value
	 */
	public void setParameter(String name, String value);


	/**
	 * Set a number of parameters for this indexer
	 * @param param the parameter names and values
	 */
	void setParameters(Map<String, String> param);

	/**
	 * Get a parameter that was set for this indexer
	 * @param name parameter name
	 * @param defaultValue parameter default value
	 * @return the parameter value (or the default value if it was not specified)
	 */
	public String getParameter(String name, String defaultValue);

	/**
	 * Get a parameter that was set for this indexer
	 * @param name parameter name
	 * @return the parameter value (or null if it was not specified)
	 */
	public String getParameter(String name);

	/**
	 * Check if the specified parameter has a value
	 * @param name parameter name
	 * @return true iff the parameter has a value
	 */
	boolean hasParameter(String name);

	/**
	 * Return the fieldtype to use for the specified field.
	 * @param fieldName the field name
	 * @return the fieldtype
	 */
	public FieldType getMetadataFieldTypeFromIndexerProperties(String fieldName);
}
