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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.Searcher;

/**
 * Applies grouping to the results in a DocResults object.
 */
public class DocGrouper implements Iterable<DocGroup> {
	Map<String, DocGroup> groups = new HashMap<String, DocGroup>();

	List<DocGroup> orderedGroups = new ArrayList<DocGroup>();

	private Searcher searcher;

	private int largestGroupSize = 0;

	private int totalResults = 0;

	private DocProperty groupBy;

	/**
	 * Constructor. Fills the groups from the given document results.
	 *
	 * @param docResults
	 *            the results to group.
	 * @param groupBy
	 *            the criterium to group on.
	 */
	public DocGrouper(DocResults docResults, DocProperty groupBy) {
		searcher = docResults.getSearcher();
		this.groupBy = groupBy;
		for (DocResult r : docResults.getResults()) {
			String groupId = groupBy.get(r);
			DocGroup group = groups.get(groupId);
			if (group == null) {
				group = new DocGroup(searcher, groupId, groupBy.getHumanReadable(r));
				groups.put(groupId, group);
				orderedGroups.add(group);
			}
			group.add(r);
			if (group.size() > largestGroupSize)
				largestGroupSize = group.size();
			totalResults++;
		}
	}

	public Collection<DocGroup> getGroups() {
		return Collections.unmodifiableCollection(orderedGroups);
	}

	public DocGroup getGroup(String groupId) {
		return groups.get(groupId);
	}

	public void sortGroups(DocGroupProperty prop, boolean sortReverse) {
		Comparator<DocGroup> comparator = new ComparatorDocGroupProperty(prop, sortReverse,
				searcher.getCollator());
		Collections.sort(orderedGroups, comparator);
	}

	@Override
	public Iterator<DocGroup> iterator() {
		return getGroups().iterator();
	}

	public int numberOfGroups() {
		return groups.size();
	}

	public int getLargestGroupSize() {
		return largestGroupSize;
	}

	public int getTotalResults() {
		return totalResults;
	}

	public DocProperty getGroupCriteria() {
		return groupBy;
	}

}
