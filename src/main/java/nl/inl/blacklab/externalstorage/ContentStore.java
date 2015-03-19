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

import java.util.Set;


/**
 * Store string content by integer id. Quickly retrieve (parts of) the string content.
 */
public abstract class ContentStore {
	/**
	 * Store a document.
	 *
	 * It is possible to first call storePart() several times,
	 * as long as you finish with a call to store. The parameter may
	 * be the empty string if you wish.
	 *
	 * @param content (part of) the content of the document to store
	 * @return the content store id assigned to the document
	 */
	public abstract int store(String content);

	/**
	 * Store part of a large document.
	 *
	 * You can call this several times, but it must end with a call to store()
	 * or the document isn't properly stored.
	 *
	 * @param content part of the content of the document to store
	 */
	public abstract void storePart(String content);

	/**
	 * Retrieve a document from the content store.
	 * @param id the document's content store id
	 * @return the original content
	 */
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

	/**
	 * Close the content store
	 */
	public abstract void close();

	/**
	 * Delete a document from the content store.
	 * @param id content store id of the document to delete
	 */
	public abstract void delete(int id);

	/**
	 * Clear the entire content store.
	 */
	public abstract void clear();

	/**
	 * Returns the set of doc ids in the store.
	 * Note that the IDs of deleted document are still returned by this method.
	 * Use isDeleted() to check.
	 * @return the set of doc ids
	 */
	public abstract Set<Integer> getDocIds();

	/**
	 * Return true iff the entry with this id was deleted.
	 * @param id the entry to check
	 * @return true iff deleted
	 */
	public abstract boolean isDeleted(int id);

	/**
	 * Returns the document length in characters
	 * @param id the document
	 * @return the length in characters
	 */
	public abstract int getDocLength(int id);

}
