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
