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
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.QueryExecutionContext;

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
public class SpanQueryTagsPayload extends SpanQuery {

	SpanTermQuery clause;

	private String tagName;

	private String baseFieldName;

	public SpanQueryTagsPayload(QueryExecutionContext context, String tagName) {
		this.tagName = tagName;
		baseFieldName = context.fieldName;
		QueryExecutionContext startTagContext = context.withProperty(ComplexFieldUtil.START_TAG_PROP_NAME);
		String startTagFieldName = startTagContext.luceneField();
		this.clause = new SpanTermQuery(new Term(startTagFieldName, startTagContext.optDesensitize(tagName)));
	}

	@Override
	public Spans getSpans(LeafReaderContext context, Bits acceptDocs, Map<Term,TermContext> termContexts)  throws IOException {
		Spans startTags = clause.getSpans(context, acceptDocs, termContexts);
		if (startTags == null)
			return null;
		return new SpansTagsPayload(startTags);
	}

	@Override
	public String toString(String field) {
		return "SpanQueryTagsPayload(" + tagName + ")";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || this.getClass() != o.getClass())
			return false;

		final SpanQueryTagsPayload that = (SpanQueryTagsPayload) o;

		if (!clause.equals(that.clause))
			return false;

		return (getBoost() == that.getBoost());
	}

	/**
	 * Returns the name of the search field. In the case of a complex field, the clauses may
	 * actually query different properties of the same complex field (e.g. "description" and
	 * "description__pos"). That's why only the prefix is returned.
	 *
	 * @return name of the search field. In the case of a complex
	 */
	@Override
	public String getField() {
		return baseFieldName;
	}

	/**
	 * Add all terms to the supplied set
	 *
	 * @param terms
	 *            the set the terms should be added to
	 */
	@SuppressWarnings({ "rawtypes" })
	@Override
	public void extractTerms(Set terms) {
		try {
			// FIXME: temporary extractTerms hack
			Method methodExtractTerms = SpanQuery.class.getDeclaredMethod("extractTerms", Set.class);
			methodExtractTerms.setAccessible(true);
			methodExtractTerms.invoke(clause, terms);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int hashCode() {
		int h = clause.hashCode();
		h ^= (h << 10) | (h >>> 23);
		h ^= Float.floatToRawIntBits(getBoost());
		return h;
	}
}
