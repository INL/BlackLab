/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search;

import org.apache.lucene.document.Document;

/**
 * Defines a way to access the original indexed content.
 *
 * NOTE: this class is very simple and has only 1 implementation. Maybe we should fold it into
 * ContentStore or Searcher to simplify things.
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
	 *            start positions of the substrings
	 * @param end
	 *            end positions of the substrings
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

}
