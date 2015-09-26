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
package nl.inl.blacklab.externalstorage;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.ContentAccessor;

/**
 * Defines a way to access the original indexed content.
 */
public class ContentAccessorContentStore extends ContentAccessor {
	private ContentStore contentStore;

	/**
	 * @return the content store
	 */
	public ContentStore getContentStore() {
		return contentStore;
	}

	private String contentIdField = null;

	/**
	 * @param fieldName field that this content store stores
	 * @param contentStore the content store
	 */
	public ContentAccessorContentStore(String fieldName, ContentStore contentStore) {
		super(fieldName);
		contentIdField = ComplexFieldUtil.contentIdField(ComplexFieldUtil.getBaseName(fieldName));
		this.contentStore = contentStore;
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
	@Override
	public String[] getSubstringsFromDocument(Document d, int[] start, int[] end) {
		if (start.length != end.length)
			throw new RuntimeException("start and end must be of equal length");

		String contentIdStr = d.get(contentIdField);
		if (contentIdStr == null)
			throw new RuntimeException("Lucene document has no content id: " + d);
		int contentId = Integer.parseInt(contentIdStr);
		return contentStore.retrieveParts(contentId, start, end);
	}

	@Override
	public void close() {
		contentStore.close();
	}

}
