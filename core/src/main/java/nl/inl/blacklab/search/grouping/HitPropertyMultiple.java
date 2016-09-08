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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import nl.inl.blacklab.search.Hits;

/**
 * A collection of GroupProperty's identifying a particular group.
 */
public class HitPropertyMultiple extends HitProperty implements Iterable<HitProperty> {
	List<HitProperty> criteria;

	List<String> contextNeeded;

	/**
	 * Quick way to create group criteria. Just call this method with the GroupCriterium object(s)
	 * you want.
	 *
	 * @param criteria
	 *            the desired criteria
	 */
	public HitPropertyMultiple(HitProperty... criteria) {
		super(criteria[0].hits);
		this.criteria = new ArrayList<>(Arrays.asList(criteria));

		determineContextNeeded();
	}

	/**
	 * Determine what context we need for each property,
	 * and let the properties know at what context index/indices
	 * they can find the context(s) they need.
	 *
	 * Called whenever something about our criteria changes.
	 */
	private void determineContextNeeded() {
		// Figure out what context(s) we need
		List<String> result = new ArrayList<>();
		for (HitProperty prop : criteria) {
			List<String> requiredContext = prop.needsContext();
			if (requiredContext != null) {
				for (String c: requiredContext) {
					if (!result.contains(c))
						result.add(c);
				}
			}
		}
		contextNeeded = result.isEmpty() ? null : result;

		// Let criteria know what context number(s) they need
		for (HitProperty prop : criteria) {
			List<String> requiredContext = prop.needsContext();
			if (requiredContext != null) {
				List<Integer> contextNumbers = new ArrayList<>();
				for (String c: requiredContext) {
					contextNumbers.add(contextNeeded.indexOf(c));
				}
				prop.setContextIndices(contextNumbers);
			}
		}
		contextNeeded = result.isEmpty() ? null : result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof HitPropertyMultiple) {
			return ((HitPropertyMultiple) obj).criteria.equals(criteria);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return criteria.hashCode();
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		int i = 0;
		for (HitProperty prop : criteria) {
			if (i > 0)
				str.append(",");
			str.append(prop.toString());
			i++;
		}
		return str.toString();
	}

	@Override
	public Iterator<HitProperty> iterator() {
		return criteria.iterator();
	}

	@Override
	public List<String> needsContext() {
		return contextNeeded;
	}

	@Override
	public HitPropValueMultiple get(int hitNumber) {
		HitPropValue[] rv = new HitPropValue[criteria.size()];
		int i = 0;
		for (HitProperty crit : criteria) {
			rv[i] = crit.get(hitNumber);
			i++;
		}
		return new HitPropValueMultiple(rv);
	}

	@Override
	public int compare(Object i, Object j) {
		for (HitProperty crit : criteria) {
			int cmp = crit.compare(i, j);
			if (cmp != 0)
				return reverse ? -cmp : cmp;
		}
		return 0;
	}

	@Override
	public String getName() {
		StringBuilder b = new StringBuilder();
		for (HitProperty crit : criteria) {
			if (b.length() > 0)
				b.append(", ");
			b.append(crit.getName());
		}
		return b.toString();
	}


	@Override
	public String serialize() {
		String[] values = new String[criteria.size()];
		for (int i = 0; i < criteria.size(); i++) {
			values[i] = criteria.get(i).serialize();
		}
		return (reverse ? "-(" : "") + PropValSerializeUtil.combineMultiple(values) + (reverse ? ")" : "");
	}

	public static HitPropertyMultiple deserialize(Hits hits, String info) {
		String[] strValues = PropValSerializeUtil.splitMultiple(info);
		HitProperty[] values = new HitProperty[strValues.length];
		int i = 0;
		for (String strValue: strValues) {
			values[i] = HitProperty.deserialize(hits, strValue);
			i++;
		}
		return new HitPropertyMultiple(values);
	}
}
