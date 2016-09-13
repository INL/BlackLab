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
package nl.inl.blacklab.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.Term;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnd;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter;
import nl.inl.blacklab.search.lucene.SpanQueryTags;
import nl.inl.blacklab.search.lucene.SpanQueryTagsOld;
import nl.inl.util.StringUtil;

/**
 * A TextPattern matching a word.
 */
public class TextPatternTags extends TextPattern {

	protected String elementName;

	Map<String, String> attr;

	public TextPatternTags(String elementName, Map<String, String> attr) {
		this.elementName = elementName;
		this.attr = attr;
	}

	public TextPatternTags(String elementName) {
		this(elementName, null);
	}

	public Term getTerm(String fieldName) {
		return new Term(fieldName, elementName);
	}

	@Override
	public BLSpanQuery translate(QueryExecutionContext context) {
		String elementName1 = optInsensitive(context, elementName);
		BLSpanQuery allTags;
		if (context.tagLengthInPayload()) {
			// Modern index, with tag length in payload
			allTags = new SpanQueryTags(context, elementName1);
		} else {
			// Older index, with end tags stored in separate property
			allTags = new SpanQueryTagsOld(context, elementName1);
		}
		if (attr == null || attr.isEmpty())
			return allTags;

		// Construct attribute filters
		List<BLSpanQuery> attrFilters = new ArrayList<>();
		QueryExecutionContext startTagContext = context.withProperty(ComplexFieldUtil.START_TAG_PROP_NAME);
		for (Map.Entry<String,String> e: attr.entrySet()) {
			String value = optInsensitive(context, "@" + e.getKey() + "__" + e.getValue());
			attrFilters.add((BLSpanQuery) new BLSpanTermQuery(new Term(startTagContext.luceneField(), startTagContext.subpropPrefix() + startTagContext.optDesensitize(value))));
		}

		// Filter the tags
		// (NOTE: only works for start tags and full elements because attribute values
		//  are indexed at the start tag!)
		BLSpanQuery filter;
		if (attrFilters.size() == 1)
			filter = attrFilters.get(0);
		else
			filter = new SpanQueryAnd(attrFilters);
		return new SpanQueryPositionFilter(allTags, filter, TextPatternPositionFilter.Operation.STARTS_AT, false);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternTags) {
			TextPatternTags tp = ((TextPatternTags) obj);
			return elementName.equals(tp.elementName) && attr.equals(tp.attr);
		}
		return false;
	}

	public String getElementName() {
		return elementName;
	}

	@Override
	public int hashCode() {
		return elementName.hashCode() + attr.hashCode();
	}

	@Deprecated
	@Override
	public String toString(QueryExecutionContext context) {
		return toString();
	}

	@Override
	public String toString() {
		if (attr != null && !attr.isEmpty())
			return "TAGS(" + elementName + ", " + StringUtil.join(attr) + ")";
		return "TAGS(" + elementName + ")";
	}

}
