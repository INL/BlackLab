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
 * Counts the number of documents that have a certain property.
 *
 * Similar to grouping documents, but doesn't store the documents and hits.
 *
 * Useful for faceted search.
 */
public class DocCounts implements Iterable<DocCount> {
	Map<HitPropValue, DocCount> counts = new HashMap<HitPropValue, DocCount>();

	List<DocCount> orderedGroups = new ArrayList<DocCount>();

	private Searcher searcher;

	private int largestGroupSize = 0;

	private int totalResults = 0;

	private DocProperty countBy;

	/**
	 * The DocResults we were created from
	 */
	private DocResults docResults;

	/**
	 * Constructor. Fills the groups from the given document results.
	 *
	 * @param docResults
	 *            the results to group.
	 * @param countBy
	 *            the criterium to group on.
	 */
	DocCounts(DocResults docResults, DocProperty countBy) {
		this.docResults = docResults;
		searcher = docResults.getSearcher();
		this.countBy = countBy;
		Thread currentThread = Thread.currentThread();
		for (DocResult r : docResults) {
			if (currentThread.isInterrupted()) {
				// Thread was interrupted. Don't throw exception because not
				// all client programs use this feature and we shouldn't force
				// them to catch a useless exception.
				// This does mean that it's the client's responsibility to detect
				// thread interruption if it wants to be able to break off long-running
				// queries.
				return;
			}

			HitPropValue groupId = countBy.get(r);
			DocCount count = counts.get(groupId);
			if (count == null) {
				count = new DocCount(searcher, groupId);
				counts.put(groupId, count);
			}
			count.increment();
			if (count.size() > largestGroupSize)
				largestGroupSize = count.size();
			totalResults++;
		}
	}

	public Collection<DocCount> getCounts() {
		return Collections.unmodifiableCollection(orderedGroups);
	}

	public Integer getCount(HitPropValue groupId) {
		return counts.get(groupId).size();
	}

	public void sortGroups(DocGroupProperty prop, boolean sortReverse) {
		Comparator<DocGroup> comparator = new ComparatorDocGroupProperty(prop, sortReverse,
				searcher.getCollator());
		Collections.sort(orderedGroups, comparator);
	}

	@Override
	public Iterator<DocCount> iterator() {
		return getCounts().iterator();
	}

	public int numberOfGroups() {
		return counts.size();
	}

	public int getLargestGroupSize() {
		return largestGroupSize;
	}

	public int getTotalResults() {
		return totalResults;
	}

	public DocProperty getGroupCriteria() {
		return countBy;
	}

	public DocResults getOriginalDocResults() {
		return docResults;
	}

}
