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
package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.QueryExecutionContext;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;

/**
 *
 * Returns spans corresponding to a certain element (tag) type.
 *
 * For example, SpanQueryTags("ne") will give us spans for all the <ne> elements in the document.
 *
 * NOTE: this does not work with nested tags yet, as it just searches for all open and close tags
 * and matches the first open tag with the first close tag, etc.
 *
 */
public class SpanQueryTags extends SpanQueryBase {

	private String tagName;

	public SpanQueryTags(QueryExecutionContext context, String tagName) {
		super();
		this.tagName = tagName;
		clauses = new SpanQuery[2];
		baseFieldName = context.fieldName;
		QueryExecutionContext startTagContext = context.withProperty(ComplexFieldUtil.START_TAG_PROP_NAME);
		String startTagFieldName = startTagContext.luceneField();
		QueryExecutionContext endTagContext = context.withProperty(ComplexFieldUtil.END_TAG_PROP_NAME);
		String endTagFieldName = endTagContext.luceneField();

		// Use a BlackLabSpanTermQuery instead of default Lucene one
		// because we need to override getField() to only return the base field name,
		// not the complete field name with the property.
		clauses[0] = new BLSpanTermQuery(new Term(startTagFieldName, startTagContext.optDesensitize(tagName)));
		clauses[1] = new BLSpanTermQuery(new Term(endTagFieldName, endTagContext.optDesensitize(tagName)));
	}

	@Override
	public Spans getSpans(AtomicReaderContext context, Bits acceptDocs, Map<Term,TermContext> termContexts)  throws IOException {
		Spans startTags = clauses[0].getSpans(context, acceptDocs, termContexts);
		Spans endTags = clauses[1].getSpans(context, acceptDocs, termContexts);
		Spans result = new SpansTags(startTags, endTags);

		return result;
	}

	@Override
	public String toString(String field) {
		return "SpanQueryTags(" + tagName + ")";
	}
}
