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
package nl.inl.blacklab.perdocument;

import java.util.ArrayList;

/**
 * A list of DocResult objects (document-level query results). The list may be sorted by calling
 * DocResults.sort().
 */
public class DocResultsWindow extends DocResults {
	private DocResults source;

	private int first;

	private int numberPerPage;

	/**
	 *
	 * @param source
	 * @param first
	 * @param numberPerPage
	 */
	DocResultsWindow(DocResults source, int first, int numberPerPage) {
		super(source.getSearcher());
		this.source = source;
		this.first = first;
		this.numberPerPage = numberPerPage;

		boolean emptyResultSet = !source.sizeAtLeast(1);
		if (first < 0 || (emptyResultSet && first > 0) ||
			(!emptyResultSet && !source.sizeAtLeast(first + 1))) {
			throw new RuntimeException("First hit out of range");
		}

		// Auto-clamp number
		int number = numberPerPage;
		if (!source.sizeAtLeast(first + number))
			number = source.size() - first;

		// Make sublist (don't use sublist because the backing list may change if not
		// all hits have been read yet)
		results = new ArrayList<>();
		for (int i = first; i < first + number; i++) {
			results.add(source.get(i));
		}
	}

	public boolean hasNext() {
		return source.sizeAtLeast(first + numberPerPage + 1);
	}

	public boolean hasPrevious() {
		return first > 0;
	}

	public int nextFrom() {
		return first + results.size();
	}

	public int prevFrom() {
		return first - numberPerPage;
	}

	public int first() {
		return first;
	}

	public int last() {
		return first + results.size() - 1;
	}

	public int number() {
		return results.size();
	}

	public int totalResults() {
		return source.size();
	}

	public DocResults getOriginalDocs() {
		return source;
	}

}
