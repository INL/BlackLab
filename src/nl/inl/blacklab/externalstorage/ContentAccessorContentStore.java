/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.externalstorage;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.ContentAccessor;

import org.apache.lucene.document.Document;

/**
 * Defines a way to access the original indexed content.
 */
public class ContentAccessorContentStore extends ContentAccessor {
	private ContentStore contentStore;

	public ContentStore getContentStore() {
		return contentStore;
	}

	private String contentIdField = null;

	public ContentAccessorContentStore(String fieldName, ContentStore contentStore) {
		super(fieldName);
		contentIdField = ComplexFieldUtil.fieldName(fieldName, "cid");
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
	 *            start positions of the substrings
	 * @param end
	 *            end positions of the substrings
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

}
