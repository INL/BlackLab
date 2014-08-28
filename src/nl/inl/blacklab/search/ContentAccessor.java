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
package nl.inl.blacklab.search;

import org.apache.lucene.document.Document;

/**
 * Defines a way to access the original indexed content.
 *
 * NOTE: this class is very simple and has only 1 implementation. Maybe we should fold it into
 * ContentStore or Searcher to simplify things. We might want to have a content accessor
 * that talks to a website or database later, but this functionality could also be in ContentStore,
 * right?
 *
 */
public abstract class ContentAccessor {
	protected String fieldName;

	public String getFieldName() {
		return fieldName;
	}

	public ContentAccessor(String fieldName) {
		this.fieldName = fieldName;
	}

	/**
	 * Get substrings from a document.
	 *
	 * Note: if start and end are both -1 for a certain substring, the whole document is returned.
	 *
	 * @param d
	 *            the Lucene document (contains the file name)
	 * @param start
	 *            start positions of the substrings.
	 *            -1 means start of document.
	 * @param end
	 *            end positions of the substrings.
	 *            -1 means end of document.
	 * @return the requested substrings from this document
	 */
	public abstract String[] getSubstringsFromDocument(Document d, int[] start, int[] end);

	/**
	 * Get substrings from a document.
	 *
	 * Note: if start and end are both -1, the whole document is returned.
	 *
	 * @param d
	 *            the Lucene document (contains the file name)
	 * @param start
	 *            start positions of the substrings
	 * @param end
	 *            end positions of the substrings
	 * @return the requested substrings from this document
	 */
	public String getSubstringFromDocument(Document d, int start, int end) {
		return getSubstringsFromDocument(d, new int[] { start }, new int[] { end })[0];
	}

	public abstract void close();

}
