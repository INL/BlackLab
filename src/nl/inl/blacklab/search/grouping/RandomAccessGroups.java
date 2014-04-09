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

import nl.inl.blacklab.search.Searcher;

/**
 * Groups results on the basis of a list of criteria.
 *
 * Unlike its base class, this class also allows random access to the groups, and each group
 * provides random access to the hits. Note that this means that all hits found must be retrieved,
 * which may be unfeasible for large results sets.
 *
 * @deprecated renamed to HitGroups
 */
@Deprecated
public abstract class RandomAccessGroups extends HitGroups {

	public RandomAccessGroups(Searcher searcher, HitProperty groupCriteria) {
		super(searcher, groupCriteria);
	}

}
