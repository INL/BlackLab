/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.grouping;

import org.apache.lucene.search.spans.Spans;

/**
 * A group of results, with its group identity and the results themselves.
 *
 * You can only iterate over the Hit objects. The subclass RandomAccessGroup does allow random
 * access to the hits.
 */
public abstract class Group {
	protected String groupIdentity;

	private String humanReadableGroupIdentity;

	public Group(String groupIdentity, String humanReadableGroupIdentity) {
		this.groupIdentity = groupIdentity;
		this.humanReadableGroupIdentity = humanReadableGroupIdentity;
	}

	public String getIdentity() {
		return groupIdentity;
	}

	public String getHumanReadableIdentity() {
		return humanReadableGroupIdentity;
	}

	public abstract Spans getSpans();

}
