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

import java.util.List;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternTranslator;
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
public class TextPatternTranslatorSpanQuery implements TextPatternTranslator<SpanQuery> {
	@Override
	public SpanQuery and(String fieldName, List<SpanQuery> clauses) {
		return new SpanQueryAnd(clauses);
	}

	@Override
	public SpanQuery or(String fieldName, List<SpanQuery> clauses) {
		return new SpanOrQuery(clauses.toArray(new SpanQuery[] {}));
	}

	@Override
	public SpanQuery property(String fieldName, String propertyName, String altName,
			TextPattern input) {
		return input.translate(this, ComplexFieldUtil.fieldName(fieldName, propertyName, altName));
	}

	@Override
	public SpanQuery regex(String fieldName, String value) {
		// return new BLSpanRegexQuery(new Term(fieldName, value));
		return new BLSpanMultiTermQueryWrapper<RegexQuery>(new RegexQuery(
				new Term(fieldName, value)));
	}

	@Override
	public SpanQuery sequence(String fieldName, List<SpanQuery> clauses) {
		return new SpanQuerySequence(clauses);
	}

	@Override
	public SpanQuery docLevelAnd(String fieldName, List<SpanQuery> clauses) {
		return new SpanQueryDocLevelAnd(clauses);
	}

	@Override
	public SpanQuery fuzzy(String fieldName, String value, float similarity, int prefixLength) {
		return new SpanFuzzyQuery(new Term(fieldName, value), similarity, prefixLength);
	}

	@Override
	public SpanQuery tags(String fieldName, String elementName) {
		return new SpanQueryTags(fieldName, elementName);
	}

	@Override
	public SpanQuery containing(String fieldName, SpanQuery containers, SpanQuery search) {
		return new SpanQueryContaining(containers, search);
	}

	@Override
	public SpanQuery term(String fieldName, String value) {
		// Use a BlackLabSpanTermQuery instead of default Lucene one
		// because we need to override getField() to only return the base field name,
		// not the complete field name with the property.
		return new BLSpanTermQuery(new Term(fieldName, value));
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
	public SpanQuery wildcard(String fieldName, String value) {
		return new BLSpanMultiTermQueryWrapper<WildcardQuery>(new WildcardQuery(new Term(fieldName,
				value)));
	}

	@Override
	public SpanQuery prefix(String fieldName, String value) {
		return new BLSpanMultiTermQueryWrapper<PrefixQuery>(new PrefixQuery(new Term(fieldName,
				value)));
	}

}
