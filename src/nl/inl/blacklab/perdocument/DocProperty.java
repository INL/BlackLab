/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.perdocument;

/**
 * Abstract base class for criteria on which to group DocResult objects. Subclasses implement
 * specific grouping criteria (number of hits, the value of a stored field in the Lucene document,
 * ...)
 */
public abstract class DocProperty {
	/**
	 * Get the desired grouping/sorting property from the DocResult object
	 *
	 * @param result
	 *            the result to get the grouping property for
	 * @return the grouping property. e.g. this might be "Harry Mulisch" when grouping on author.
	 */
	public abstract String get(DocResult result);

	public String getHumanReadable(DocResult result) {
		return get(result);
	}

	public boolean defaultSortDescending() {
		return false;
	}

	public abstract String getName();
}
