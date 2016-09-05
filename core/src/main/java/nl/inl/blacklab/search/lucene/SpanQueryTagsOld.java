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
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.QueryExecutionContext;

/**
 * Old version of SpanQueryTags.
 *
 * Start and end tags used to be stored in separate properties.
 * This class is used for indices with indexFormat <= 3.
 * Starting from indexFormat 3.1, tag lengths are stored in the start tag payload.
 *
 *
 * Returns spans corresponding to a certain element (tag) type.
 *
 * For example, SpanQueryTags("ne") will give us spans for all the <ne> elements in the document.
 *
 * NOTE: this does not work with nested tags yet, as it just searches for all open and close tags
 * and matches the first open tag with the first close tag, etc.
 *
 */
public class SpanQueryTagsOld extends SpanQueryBase {

	private String tagName;

	public SpanQueryTagsOld(QueryExecutionContext context, String tagName) {
		super();
		this.tagName = tagName;
		clauses = new SpanQuery[2];
		baseFieldName = context.fieldName();
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
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		SpanWeight startWeight = clauses[0].createWeight(searcher, needsScores);
		SpanWeight endWeight = clauses[1].createWeight(searcher, needsScores);
		Map<Term, TermContext> contexts = needsScores ? getTermContexts(startWeight, endWeight) : null;
		return new SpanWeightTagsOld(startWeight, endWeight, searcher, contexts);
	}

	public class SpanWeightTagsOld extends SpanWeight {

		final SpanWeight startWeight, endWeight;

		public SpanWeightTagsOld(SpanWeight startWeight, SpanWeight endWeight, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQueryTagsOld.this, searcher, terms);
			this.startWeight = startWeight;
			this.endWeight = endWeight;
		}

		@Override
		public void extractTerms(Set<Term> terms) {
			startWeight.extractTerms(terms);
			endWeight.extractTerms(terms);
		}

		@Override
		public void extractTermContexts(Map<Term, TermContext> contexts) {
			startWeight.extractTermContexts(contexts);
			endWeight.extractTermContexts(contexts);
		}

		@Override
		public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
			Spans startTags = startWeight.getSpans(context, requiredPostings);
			Spans endTags = endWeight.getSpans(context, requiredPostings);
			if (startTags == null || endTags == null)
				return null;
			return new SpansTagsOld(startTags, endTags);
		}
	}

	@Override
	public String toString(String field) {
		return "SpanQueryTags(" + tagName + ")";
	}
}
