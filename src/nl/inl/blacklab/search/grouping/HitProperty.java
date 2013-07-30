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
import java.util.Comparator;
import java.util.List;

import nl.inl.blacklab.search.Hit;

/**
 * Abstract base class for a property of a hit, like document title, hit text, right context, etc.
 */
public abstract class HitProperty implements Comparator<Object> {

	public HitProperty() {
		contextIndices = new ArrayList<Integer>();
		contextIndices.add(0);
	}

	public abstract HitPropValue get(Hit result);

	/**
	 * Compares two hits on this property.
	 *
	 * Note that we use Object as the type instead of Hit to save
	 * on run-time type checking. We know (slash hope :-) that this
	 * method is only ever called to compare Hits.
	 *
	 * @param a first hit
	 * @param b second hit
	 * @return 0 if equal, negative if a < b, positive if a > b.
	 */
	@Override
	public abstract int compare(Object a, Object b);

	/**
	 * Retrieve context from which field(s) prior to sorting/grouping on this
	 * property?
	 * @return null if no context is required, the fieldname otherwise
	 */
	public List<String> needsContext() {
		return null;
	}

	public abstract String getName();

	/**
	 * For HitProperties that need context, the context indices that
	 * correspond to the context(s) they need in the result set.
	 * (in the same order as reported by needsContext()).
	 */
	List<Integer> contextIndices = null;

	/**
	 * For HitProperties that need context, sets the context indices that
	 * correspond to the context(s) they need in the result set.
	 * @param contextIndices the indices, in the same order as reported by needsContext().
	 */
	public void setContextIndices(List<Integer> contextIndices) {
		this.contextIndices.clear();
		this.contextIndices.addAll(contextIndices);
	}
}
