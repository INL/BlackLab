/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
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
	public boolean needsConcordances() {
		for (HitProperty prop : criteria) {
			if (prop.needsConcordances())
				return true;
		}
		return false;
	}

	@Override
	public String get(Hit result) {
		StringBuilder b = new StringBuilder();
		for (HitProperty crit : criteria) {
			if (b.length() > 0)
				b.append(";");
			b.append(crit.get(result));
		}
		return b.toString();
	}

	@Override
	public String getHumanReadable(Hit result) {
		StringBuilder b = new StringBuilder();
		for (HitProperty crit : criteria) {
			if (b.length() > 0)
				b.append(", ");
			b.append(crit.getHumanReadable(result));
		}
		return b.toString();
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

}
