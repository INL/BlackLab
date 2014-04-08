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
package nl.inl.blacklab.search.grouping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Searcher;

import org.apache.lucene.search.spans.SpanQuery;

/**
 * Groups results on the basis of a list of criteria, and provide random access to the resulting
 * groups.
 *
 * This implementation doesn't care in what order the spans appear, it will just retrieve all of
 * them and put each of them in a group. This takes more memory and time than if the spans to be
 * grouped are sequential (in which case you should use ResultsGrouperSequential).
 */
public class ResultsGrouper extends RandomAccessGroups {
	/**
	 * The groups.
	 */
	Map<HitPropValue, RandomAccessGroup> groups = new HashMap<HitPropValue, RandomAccessGroup>();

	/**
	 * The groups, in sorted order.
	 */
	List<RandomAccessGroup> groupsOrdered = new ArrayList<RandomAccessGroup>();

	/**
	 * Default field to make concordances from.
	 */
	private String defaultConcField;

	/**
	 * Field our current concordances came from.
	 */
	private List<String> contextField;

	/**
	 * Total number of hits.
	 */
	private int totalHits = 0;

	/**
	 * Size of the largest group.
	 */
	private int largestGroupSize = 0;

	/**
	 * Construct a ResultsGrouper object, by grouping the supplied spans.
	 *
	 * @param searcher
	 *            our Searcher object
	 * @param source
	 *            the Spans to group
	 * @param criteria
	 *            the criteria to group on
	 * @param defaultConcField
	 *            the default concordance field
	 * @deprecated pass a Hits object to ResultsGrouper
	 */
	@Deprecated
	public ResultsGrouper(Searcher searcher, SpanQuery source, HitProperty criteria,
			String defaultConcField) {
		this(new Hits(searcher, defaultConcField, source), criteria);
	}

	/**
	 * Construct a ResultsGrouper object, by grouping the supplied hits.
	 *
	 * @param hits
	 *            the hits to group
	 * @param criteria
	 *            the criteria to group on
	 */
	public ResultsGrouper(Hits hits, HitProperty criteria) {
		super(hits.getSearcher(), criteria);
		defaultConcField = hits.getConcordanceFieldName();
		List<String> requiredContext = criteria.needsContext();
		if (requiredContext != null) {
			hits.findContext(requiredContext);
		}
		contextField = hits.getContextFieldPropName();
		Thread currentThread = Thread.currentThread();
		for (int i = 0; i < hits.size(); i++) {
		//for (Hit hit : hits) {
			if (currentThread.isInterrupted()) {
				// Thread was interrupted. Don't throw exception because not
				// all client programs use this feature and we shouldn't force
				// them to catch a useless exception.
				// This does mean that it's the client's responsibility to detect
				// thread interruption if it wants to be able to break off long-running
				// queries.
				return;
			}
			addHit(hits, i);
		}

		// If the group identities are context words, we should possibly merge
		// some groups if they have identical sort orders (up to now, we've grouped on
		// token id, not sort order).

	}

	/**
	 * Add a hit to the appropriate group.
	 *
	 * @param hits
	 *    the hits object this hit is in
	 * @param originalHitIndex
	 *    original (before sorting) index of the hit
	 */
	public void addHit(Hits hits, int originalHitIndex) {
		HitPropValue identity = getGroupIdentity(originalHitIndex);
		RandomAccessGroup group = groups.get(identity);
		if (group == null) {
			group = new RandomAccessGroup(searcher, identity, defaultConcField);
			group.setContextField(contextField);
			groups.put(identity, group);
			groupsOrdered.add(group);
		}
		group.add(hits.getByOriginalOrder(originalHitIndex));
		if (group.size() > largestGroupSize)
			largestGroupSize = group.size();
		totalHits++;
	}

	/**
	 * Get the total number of hits
	 *
	 * @return the number of hits
	 */
	@Override
	public int getTotalResults() {
		return totalHits;
	}

	/**
	 * Get all groups as a map
	 *
	 * @return a map of groups indexed by group property
	 */
	@Override
	public Map<HitPropValue, RandomAccessGroup> getGroupMap() {
		return Collections.unmodifiableMap(groups);
	}

	/**
	 * Get all groups as a list
	 *
	 * @return the list of groups
	 */
	@Override
	public List<RandomAccessGroup> getGroups() {
		return Collections.unmodifiableList(groupsOrdered);
	}

	/**
	 * Sort groups
	 *
	 * @param prop
	 *            the property to sort on
	 * @param sortReverse
	 *            whether to sort in descending order
	 */
	@Override
	public void sortGroups(GroupProperty prop, boolean sortReverse) {
		Comparator<Group> comparator = new ComparatorGroupProperty(prop, sortReverse,
				searcher.getCollator());

		Collections.sort(groupsOrdered, comparator);
	}

	/**
	 * Return the size of the largest group
	 *
	 * @return size of the largest group
	 */
	@Override
	public int getLargestGroupSize() {
		return largestGroupSize;
	}

	/**
	 * Return the number of groups
	 *
	 * @return number of groups
	 */
	@Override
	public int numberOfGroups() {
		return groups.size();
	}
}
