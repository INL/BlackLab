/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.externalstorage;

/**
 * Store string content by integer id. Quickly retrieve (parts of) the string content.
 */
public abstract class ContentStore {
	public abstract int store(String content);

	public abstract void storePart(String content);

	public abstract String retrieve(int id);

	/**
	 * Retrieve substring from a document.
	 *
	 * @param id
	 *            content store document id
	 * @param start
	 *            start of the substring
	 * @param end
	 *            end of the substring
	 * @return the substring
	 */
	public String retrievePart(int id, int start, int end) {
		return retrieveParts(id, new int[] { start }, new int[] { end })[0];
	}

	/**
	 * Retrieve substrings from a document.
	 *
	 * @param id
	 *            content store document id
	 * @param start
	 *            start of the substring
	 * @param end
	 *            end of the substring
	 * @return the substrings
	 */
	public abstract String[] retrieveParts(int id, int[] start, int[] end);

	public abstract void close();

	public abstract void delete(int id);

	public abstract void clear();

}
