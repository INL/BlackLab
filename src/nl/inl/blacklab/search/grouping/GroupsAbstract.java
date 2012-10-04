/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.search.Hit;

/**
 * A number of groups of hits, grouped on the basis of a list of criteria.
 *
 * This abstract base class provides sequential access to the Group objects, which in turn provides
 * sequential access to the Hit objects.
 *
 * The subclass RandomAccessGroups provides an additional random access interface, at the cost of
 * having to fetch all results before accessing them. It has an implementation in the ResultsGrouper
 * class.
 *
 * This class has an implementation in the ResultsGrouperSequential class. Always use this
 * implementation if you can (that is, if your results are already ordered by the grouping you wish
 * to apply, such as when grouping on document), as it is faster and uses less memory.
 */
public abstract class GroupsAbstract implements Groups {
	protected HitProperty criteria;

	public GroupsAbstract(HitProperty criteria) {
		this.criteria = criteria;
	}

	protected String getHumanReadableGroupIdentity(Hit result) {
		return criteria.getHumanReadable(result);
	}

	protected String getGroupIdentity(Hit result) {
		return criteria.get(result);
	}

	@Override
	public HitProperty getGroupCriteria() {
		return criteria;
	}

}
