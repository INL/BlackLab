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

import nl.inl.blacklab.search.Hit;

/**
 * A collection of GroupProperty's identifying a particular group.
 */
public class HitPropertyMultiple extends HitProperty implements Iterable<HitProperty> {
	List<HitProperty> criteria;

	/**
	 * Quick way to create group criteria. Just call this method with the GroupCriterium object(s)
	 * you want.
	 *
	 * @param criteria
	 *            the desired criteria
	 */
	public HitPropertyMultiple(HitProperty... criteria) {
		this.criteria = new ArrayList<HitProperty>(Arrays.asList(criteria));
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

	public void addCriterium(HitProperty crit) {
		criteria.add(crit);
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
	public boolean needsContext() {
		for (HitProperty prop : criteria) {
			if (prop.needsContext())
				return true;
		}
		return false;
	}

	@Override
	public HitPropValueMultiple get(Hit result) {
		HitPropValue[] rv = new HitPropValue[criteria.size()];
		int i = 0;
		for (HitProperty crit : criteria) {
			rv[i] = crit.get(result);
			i++;
		}
		return new HitPropValueMultiple(rv);
	}

	@Override
	public int compare(Hit a, Hit b) {
		return get(a).compareTo(get(b));
	}

//	@Override
//	public String getHumanReadable(Hit result) {
//		StringBuilder b = new StringBuilder();
//		for (HitProperty crit : criteria) {
//			if (b.length() > 0)
//				b.append(", ");
//			b.append(crit.getHumanReadable(result));
//		}
//		return b.toString();
//	}

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

}
