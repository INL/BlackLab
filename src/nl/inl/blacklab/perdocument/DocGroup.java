/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.perdocument;

import nl.inl.blacklab.search.Searcher;

/**
 * A group of DocResult objects, plus the "group identity". For example, if you're grouping on
 * author name, the group identity might be the string "Harry Mulisch".
 */
public class DocGroup {
	private String groupIdentity;

	private String humanReadableGroupIdentity;

	private DocResults results;

	public DocGroup(Searcher searcher, String groupIdentity, String humanReadableGroupIdentity) {
		this.groupIdentity = groupIdentity;
		this.humanReadableGroupIdentity = humanReadableGroupIdentity;
		results = new DocResults(searcher);
	}

	public void add(DocResult e) {
		results.add(e);
	}

	public String getIdentity() {
		return groupIdentity;
	}

	public String getHumanReadableIdentity() {
		return humanReadableGroupIdentity;
	}

	public DocResults getResults() {
		return results;
	}

	public int size() {
		return results.size();
	}

}
