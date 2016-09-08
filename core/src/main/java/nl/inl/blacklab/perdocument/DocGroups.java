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
import nl.inl.blacklab.search.grouping.HitPropValue;

/**
 * Applies grouping to the results in a DocResults object.
 */
public class DocGroups implements Iterable<DocGroup> {
	Map<HitPropValue, DocGroup> groups = new HashMap<>();

	List<DocGroup> orderedGroups = new ArrayList<>();

	private Searcher searcher;

	private int largestGroupSize = 0;

	private int totalResults = 0;

	private DocProperty groupBy;

	/**
	 * The DocResults we were created from
	 */
	private DocResults docResults;

	/**
	 * Constructor. Fills the groups from the given document results.
	 *
	 * @param docResults
	 *            the results to group.
	 * @param groupBy
	 *            the criterium to group on.
	 */
	DocGroups(DocResults docResults, DocProperty groupBy) {
		this.docResults = docResults;
		searcher = docResults.getSearcher();
		this.groupBy = groupBy;
		//Thread currentThread = Thread.currentThread();
		Map<HitPropValue, List<DocResult>> groupLists = new HashMap<>();
		for (DocResult r : docResults) {
			HitPropValue groupId = groupBy.get(r);
			List<DocResult> group = groupLists.get(groupId);
			if (group == null) {
				group = new ArrayList<>();
				groupLists.put(groupId, group);
			}
			group.add(r);
			if (group.size() > largestGroupSize)
				largestGroupSize = group.size();
			totalResults++;
		}
		for (Map.Entry<HitPropValue, List<DocResult>> e: groupLists.entrySet()) {
			DocGroup docGroup = new DocGroup(searcher, e.getKey(), e.getValue());
			groups.put(e.getKey(), docGroup);
			orderedGroups.add(docGroup);
		}
	}

	public Collection<DocGroup> getGroups() {
		return Collections.unmodifiableCollection(orderedGroups);
	}

	public DocGroup getGroup(HitPropValue groupId) {
		return groups.get(groupId);
	}

	/**
	 * Order the groups based on the specified group property.
	 *
	 * @param prop the property to sort on
	 * @param sortReverse if true, perform reverse sort
	 */
	public void sort(DocGroupProperty prop, boolean sortReverse) {
		Comparator<DocGroup> comparator = new ComparatorDocGroupProperty(prop, sortReverse,
				searcher.getCollator());
		Collections.sort(orderedGroups, comparator);
	}

	public void sort(DocGroupProperty prop) {
		sort(prop, false);
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

	public DocResults getOriginalDocResults() {
		return docResults;
	}

}
