/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.grouping;

public interface Groups extends Iterable<Group> {

	public abstract HitProperty getGroupCriteria();

}
