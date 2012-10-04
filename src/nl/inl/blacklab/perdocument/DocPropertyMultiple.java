/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.perdocument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * A collection of GroupProperty's identifying a particular group.
 */
public class DocPropertyMultiple extends DocProperty implements Iterable<DocProperty> {
	List<DocProperty> criteria;

	/**
	 * Quick way to create group criteria. Just call this method with the GroupCriterium object(s)
	 * you want.
	 *
	 * @param criteria
	 *            the desired criteria
	 */
	public DocPropertyMultiple(DocProperty... criteria) {
		this.criteria = new ArrayList<DocProperty>(Arrays.asList(criteria));
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof DocPropertyMultiple) {
			return ((DocPropertyMultiple) obj).criteria.equals(criteria);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return criteria.hashCode();
	}

	public void addCriterium(DocProperty crit) {
		criteria.add(crit);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		int i = 0;
		for (DocProperty prop : criteria) {
			if (i > 0)
				str.append(",");
			str.append(prop.toString());
			i++;
		}
		return str.toString();
	}

	@Override
	public Iterator<DocProperty> iterator() {
		return criteria.iterator();
	}

	@Override
	public String get(DocResult result) {
		StringBuilder b = new StringBuilder();
		for (DocProperty crit : criteria) {
			if (b.length() > 0)
				b.append(";");
			b.append(crit.get(result));
		}
		return b.toString();
	}

	@Override
	public String getHumanReadable(DocResult result) {
		StringBuilder b = new StringBuilder();
		for (DocProperty crit : criteria) {
			if (b.length() > 0)
				b.append(", ");
			b.append(crit.getHumanReadable(result));
		}
		return b.toString();
	}

	@Override
	public String getName() {
		StringBuilder b = new StringBuilder();
		for (DocProperty crit : criteria) {
			if (b.length() > 0)
				b.append(", ");
			b.append(crit.getName());
		}
		return b.toString();
	}

}
