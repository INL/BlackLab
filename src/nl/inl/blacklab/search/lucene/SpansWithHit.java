/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import nl.inl.blacklab.search.Hit;

/**
 * The purpose of this class is to make it more efficient to implement our own Spans classes that
 * work with Hit objects internally (like e.g. SpansCacher, SpansSorter, etc.).
 *
 * The getHit method allows such classes to provide the client with an additional method of getting
 * the hit information. This way, if the client needs a Hit object too, we avoid instantiating
 * multiple copies of the same Hit.
 */
public abstract class SpansWithHit extends SpansAbstract {
	/**
	 * Makes a new Hit object from the document id, start and end positions.
	 *
	 * Subclasses that already have a Hit object available should override this and return the
	 * existing Hit object, to avoid excessive Hit instantiations.
	 *
	 * @return the Hit object for the current hit
	 */
	public abstract Hit getHit();

}
