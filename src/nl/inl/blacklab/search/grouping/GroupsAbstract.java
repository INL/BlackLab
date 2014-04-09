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
abstract class GroupsAbstract implements Groups {
	protected HitProperty criteria;

	public GroupsAbstract(HitProperty criteria) {
		this.criteria = criteria;
	}

	protected HitPropValue getGroupIdentity(int index) {
		return criteria.get(index);
	}

	@Override
	public HitProperty getGroupCriteria() {
		return criteria;
	}

}
