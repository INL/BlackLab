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
