/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.grouping;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.Searcher;

/**
 * Groups results on the basis of a list of criteria.
 *
 * Unlike its base class, this class also allows random access to the groups, and each group
 * provides random access to the hits. Note that this means that all hits found must be retrieved,
 * which may be unfeasible for large results sets.
 */
public abstract class RandomAccessGroups extends GroupsAbstract {
	Searcher searcher;

	public RandomAccessGroups(Searcher searcher, HitProperty groupCriteria) {
		super(groupCriteria);

		this.searcher = searcher;
	}

	public abstract Map<String, RandomAccessGroup> getGroupMap();

	public abstract List<RandomAccessGroup> getGroups();

	public abstract void sortGroups(GroupProperty prop, boolean sortReverse);

	public RandomAccessGroup getGroup(String identity) {
		return getGroupMap().get(identity);
	}

	Iterator<RandomAccessGroup> currentIt;

	@Override
	public Iterator<Group> iterator() {
		currentIt = getGroups().iterator();
		return new Iterator<Group>() {

			@Override
			public boolean hasNext() {
				return currentIt.hasNext();
			}

			@Override
			public Group next() {
				return currentIt.next();
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}

		};
	}

	/**
	 * Get the total number of hits
	 *
	 * @return the number of hits
	 */
	public abstract int getTotalResults();

	/**
	 * Return the size of the largest group
	 *
	 * @return size of the largest group
	 */
	public abstract int getLargestGroupSize();

	/**
	 * Return the number of groups
	 *
	 * @return number of groups
	 */
	public abstract int numberOfGroups();
}
