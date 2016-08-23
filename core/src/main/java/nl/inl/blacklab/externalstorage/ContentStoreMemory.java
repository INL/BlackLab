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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Store string content by id in memory.
 */
public class ContentStoreMemory extends ContentStore {
	/**
	 * The TOC entries
	 */
	private Map<Integer, String> toc;

	private int nextId = 1;

	/**
	 * Create a new in-memory content store.
	 */
	public ContentStoreMemory() {
		toc = new HashMap<>();
	}

	/**
	 * Delete all content in the document store
	 */
	@Override
	public void clear() {
		toc.clear();
	}

	/**
	 * Close this content store (in our case, do nothing)
	 */
	@Override
	public void close() {
		//
	}

	/*
	 * Store the given content and assign an id to it
	 *
	 * @param content the content to store
	 *
	 * @return the id assigned to the content
	 */
	@Override
	public synchronized int store(String content) {
		int id = nextId;
		nextId++;
		toc.put(id, content);
		return id;
	}

	/**
	 * Retrieve content with given id
	 *
	 * @param id
	 *            the id
	 * @return the string
	 */
	@Override
	public String retrieve(int id) {
		return toc.get(id);
	}

	/**
	 * Retrieve one or more substrings from the specified content.
	 *
	 * This is more efficient than retrieving the whole content, or retrieving parts in separate
	 * calls, because the file is only opened once and random access is used to read only the
	 * required parts.
	 *
	 * NOTE: if offset and length are both -1, retrieves the whole content. This is used by the
	 * retrieve(id) method.
	 *
	 * @param id
	 *            id of the string to get substrings from
	 * @param start
	 *            the starting points of the substrings (in characters)
	 * @param end
	 *            the end points of the substrings (in characters)
	 */
	@Override
	public synchronized String[] retrieveParts(int id, int[] start, int[] end) {
		String content = toc.get(id);
		if (content == null)
			return null;

		int n = start.length;
		if (n != end.length)
			throw new IllegalArgumentException("start and end must be of equal length");
		String[] result = new String[n];
		for (int i = 0; i < n; i++) {
			if (start[i] == -1 && end[i] == -1) {
				// whole content
				start[i] = 0;
				end[i] = content.length();
			}
			if (start[i] < 0 || end[i] < 0) {
				throw new IllegalArgumentException("Illegal values, start = " + start[i] + ", end = "
						+ end[i]);
			}
			if (start[i] > content.length() || end[i] > content.length()) {
				throw new IllegalArgumentException("Value(s) out of range, start = " + start[i]
						+ ", end = " + end[i] + ", content length = " + content.length());
			}

			result[i] = content.substring(start[i], end[i]);
		}
		return result;
	}

	@Override
	public synchronized void delete(int id) {
		toc.remove(id);
	}

	@Override
	public void storePart(String content) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public Set<Integer> getDocIds() {
		return toc.keySet();
	}

	@Override
	public boolean isDeleted(int id) {
		return false;
	}

	@Override
	public int getDocLength(int id) {
		return toc.get(id).length();
	}

	@Override
	public Set<Integer> idSet() {
		return toc.keySet();
	}

}
