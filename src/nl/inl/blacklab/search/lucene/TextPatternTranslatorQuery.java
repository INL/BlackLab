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
import java.util.Map;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternTranslator;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.regex.RegexQuery;

/**
 * Translates a TextPattern to a regular Lucene Query object.
 *
 * Note that a regular Query should really only be used for fairly simple patterns. - Patterns with
 * complex sequences cannot be scored per-document because there is no non-SpanQuery way of finding
 * complex sequences. Simple term phrases *DO* work, because they can use PhraseQuery internally. -
 * TextPatternAnd (searching for two subfeatures of a single word) doesn't work correctly on a
 * per-document basis because we need position information to execute that query. -
 * TextPatternRegex/Wildcard expand to MultiTermQueries, which quickly fall back to
 * ConstantScoreQuery. This might be fixable; google "MultiTermQuery scoring" or see the code for
 * details.
 */
public class TextPatternTranslatorQuery extends TextPatternTranslator<Query> {
	private Query makeBooleanQuery(List<Query> clauses, Occur occur) {
		BooleanQuery booleanQuery = new BooleanQuery();
		for (Query query : clauses) {
			booleanQuery.add(query, occur);
		}
		return booleanQuery;
	}

	@Override
	public Query and(String fieldName, List<Query> clauses) {
		return docLevelAnd(fieldName, clauses);
	}

	@Override
	public Query docLevelAnd(String fieldName, List<Query> clauses) {
		return makeBooleanQuery(clauses, Occur.MUST);
	}

	@Override
	public Query or(String fieldName, List<Query> clauses) {
		return makeBooleanQuery(clauses, Occur.SHOULD);
	}

	@Override
	public Query property(String fieldName, String propertyName, String altName, TextPattern input) {
		return input.translate(this, ComplexFieldUtil.fieldName(fieldName, propertyName, altName));
	}

	@Override
	public Query regex(String fieldName, String value) {
		return new RegexQuery(new Term(fieldName, value));
	}

	@Override
	public Query sequence(String fieldName, List<Query> clauses) {
		boolean isComplex = false;
		for (Query q : clauses) {
			if (!(q instanceof TermQuery)) {
				isComplex = true;
				break;
			}
		}

		if (isComplex) {
			// Sequence contains complex queries; PhraseQuery won't work.
			// Fall back to AND (forget the sequence part).
			return and(fieldName, clauses);
		}

		// It's a simple word sequence; construct a PhraseQuery
		PhraseQuery pq = new PhraseQuery();
		for (Query q : clauses) {
			pq.add(((TermQuery) q).getTerm());
		}
		return pq;
	}

	@Override
	public Query fuzzy(String fieldName, String value, float similarity, int prefixLength) {
		return new FuzzyQuery(new Term(fieldName, value), similarity, prefixLength);
	}

	@Override
	public Query tags(String fieldName, String elementName, Map<String, String> attr) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Query containing(String fieldName, Query containers, Query search) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Query term(String fieldName, String value) {
		return new TermQuery(new Term(fieldName, value));
	}

	@Override
	public Query expand(Query clause, boolean expandToLeft, int min, int max) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Query repetition(Query clause, int min, int max) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Query docLevelAndNot(Query include, Query exclude) {
		BooleanQuery booleanQuery = new BooleanQuery();
		booleanQuery.add(include, Occur.MUST);
		booleanQuery.add(exclude, Occur.MUST_NOT);
		return booleanQuery;
	}

	@Override
	public Query wildcard(String fieldName, String value) {
		return new WildcardQuery(new Term(fieldName, value));
	}

	@Override
	public Query prefix(String fieldName, String value) {
		return new PrefixQuery(new Term(fieldName, value));
	}

	@Override
	public Query not(String fieldName, Query clause) {
		BooleanQuery booleanQuery = new BooleanQuery();
		booleanQuery.add(clause, Occur.MUST_NOT);
		return booleanQuery;
	}

	@Override
	public Query any(String fieldName) {
		return new MatchAllDocsQuery();
	}

	@Override
	public Query within(String fieldName, Query search, Query containers) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Query startsAt(String fieldName, Query producer, Query filter) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Query endsAt(String fieldName, Query producer, Query filter) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Query edge(Query clause, boolean rightEdge) {
		throw new UnsupportedOperationException();
	}
}
