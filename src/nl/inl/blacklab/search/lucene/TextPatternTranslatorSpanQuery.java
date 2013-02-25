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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.TPTranslationContext;
import nl.inl.blacklab.search.TextPatternTranslator;
import nl.inl.blacklab.search.lucene.SpanQueryPosFilter.Filter;
import nl.inl.blacklab.search.sequences.SpanQueryExpansion;
import nl.inl.blacklab.search.sequences.SpanQueryRepetition;
import nl.inl.blacklab.search.sequences.SpanQuerySequence;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.regex.RegexQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;

/**
 * Translates a TextPattern to a Lucene SpanQuery object.
 */
public class TextPatternTranslatorSpanQuery extends TextPatternTranslator<SpanQuery> {

	@Override
	public SpanQuery and(TPTranslationContext context, List<SpanQuery> clauses) {
		return new SpanQueryAnd(clauses);
	}

	@Override
	public SpanQuery or(TPTranslationContext context, List<SpanQuery> clauses) {
		return new SpanOrQuery(clauses.toArray(new SpanQuery[] {}));
	}

	@Override
	public SpanQuery regex(TPTranslationContext context, String value) {
		return new BLSpanMultiTermQueryWrapper<RegexQuery>(new RegexQuery(
				new Term(context.luceneField(), value)));
	}

	@Override
	public SpanQuery sequence(TPTranslationContext context, List<SpanQuery> clauses) {
		return new SpanQuerySequence(clauses);
	}

	@Override
	public SpanQuery docLevelAnd(TPTranslationContext context, List<SpanQuery> clauses) {
		return new SpanQueryDocLevelAnd(clauses);
	}

	@Override
	public SpanQuery fuzzy(TPTranslationContext context, String value, float similarity, int prefixLength) {
		return new SpanFuzzyQuery(new Term(context.luceneField(), value), similarity, prefixLength);
	}

	@Override
	public SpanQuery tags(TPTranslationContext context, String elementName, Map<String, String> attr) {
		SpanQueryTags allTags = new SpanQueryTags(context.fieldName, elementName);
		if (attr == null || attr.size() == 0)
			return allTags;

		// Construct attribute filters
		List<SpanQuery> attrFilters = new ArrayList<SpanQuery>();
		TPTranslationContext startTagContext = context.withProperty(ComplexFieldUtil.START_TAG_PROP_NAME);
		for (Map.Entry<String,String> e: attr.entrySet()) {
			String value = optCaseInsensitive(context, "@" + e.getKey() + "__" + e.getValue());
			attrFilters.add(term(startTagContext, value));
		}

		// Filter the tags
		// (NOTE: only works for start tags and full elements because attribute values
		//  are indexed at the start tag!)
		SpanQuery filter = new SpanQueryAnd(attrFilters);
		return new SpanQueryPosFilter(allTags, filter, Filter.STARTS_AT);
	}

	@Override
	public SpanQuery containing(TPTranslationContext context, SpanQuery containers, SpanQuery search) {
		return new SpanQueryPosFilter(containers, search, SpanQueryPosFilter.Filter.CONTAINING);
	}

	@Override
	public SpanQuery within(TPTranslationContext context, SpanQuery search, SpanQuery containers) {
		return new SpanQueryPosFilter(search, containers, SpanQueryPosFilter.Filter.WITHIN);
	}

	@Override
	public SpanQuery startsAt(TPTranslationContext context, SpanQuery producer, SpanQuery filter) {
		return new SpanQueryPosFilter(producer, filter, SpanQueryPosFilter.Filter.STARTS_AT);
	}

	@Override
	public SpanQuery endsAt(TPTranslationContext context, SpanQuery producer, SpanQuery filter) {
		return new SpanQueryPosFilter(producer, filter, SpanQueryPosFilter.Filter.ENDS_AT);
	}

	@Override
	public SpanQuery term(TPTranslationContext context, String value) {
		// Use a BlackLabSpanTermQuery instead of default Lucene one
		// because we need to override getField() to only return the base field name,
		// not the complete field name with the property.
		return new BLSpanTermQuery(new Term(context.luceneField(), value));
	}

	@Override
	public SpanQuery expand(SpanQuery clause, boolean expandToLeft, int min, int max) {
		return new SpanQueryExpansion(clause, expandToLeft, min, max);
	}

	@Override
	public SpanQuery repetition(SpanQuery clause, int min, int max) {
		return new SpanQueryRepetition(clause, min, max);
	}

	@Override
	public SpanQuery docLevelAndNot(SpanQuery include, SpanQuery exclude) {
		return new SpanQueryAndNot(include, exclude);
	}

	@Override
	public SpanQuery wildcard(TPTranslationContext context, String value) {
		return new BLSpanMultiTermQueryWrapper<WildcardQuery>(new WildcardQuery(new Term(context.luceneField(),
				value)));
	}

	@Override
	public SpanQuery prefix(TPTranslationContext context, String value) {
		return new BLSpanMultiTermQueryWrapper<PrefixQuery>(new PrefixQuery(new Term(context.luceneField(),
				value)));
	}

	@Override
	public SpanQuery not(TPTranslationContext context, SpanQuery clause) {
		return new SpanQueryNot(clause);
	}

	@Override
	public SpanQuery any(TPTranslationContext context) {
		return SpanQueryNot.matchAllTokens(context.luceneField());
	}

	@Override
	public SpanQuery edge(SpanQuery clause, boolean rightEdge) {
		return new SpanQueryEdge(clause, rightEdge);
	}

}
