/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
/**
 *
 */
package nl.inl.blacklab.search.lucene;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.spans.SpanTermQuery;

/**
 * @author VGeirnaert
 *
 */
public class BLSpanTermQuery extends SpanTermQuery {

	/**
	 * @param term
	 */
	public BLSpanTermQuery(Term term) {
		super(term);
	}

	/**
	 * Overriding getField to return only part of the field name. The part before the underscore
	 * will be returned. This is necessary because BlackLab uses the field property in a somewhat
	 * different manner: on top of a simply 'content' field there may also be 'content__headword'
	 * and 'content__pos'.
	 *
	 * This makes it possible to use termqueries with different fieldnames in the same AND or OR
	 * query. To ensure Lucence does not object to this, we need to generalise the field name to the
	 * common descriptor - which is the part before the underscore.
	 *
	 * This does mean that field names should NEVER include underscores.
	 *
	 * @return String field
	 */
	@Override
	public String getField() {
		return ComplexFieldUtil.getBaseName(term.field());
	}
}
