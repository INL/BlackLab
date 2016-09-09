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
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;

/**
 * Returns all tokens that do not occur in the matches
 * of the specified query.
 *
 * Each token is returned as a single hit.
 */
public class SpanQueryNot extends BLSpanQueryAbstract {

	/** if true, we assume the last token is always a special closing token and ignore it */
	boolean ignoreLastToken = false;

	public SpanQueryNot(BLSpanQuery query) {
		super(query);
	}

	public SpanQueryNot(Collection<BLSpanQuery> clauscol) {
		super(clauscol);
	}

	public SpanQueryNot(BLSpanQuery[] _clauses) {
		super(_clauses);
	}

	/**
	 * A SpanQuery that simply matches all tokens in a field
	 * @param matchAllTokensFieldName what field to match all tokens in
	 */
	private SpanQueryNot(String matchAllTokensFieldName) {
		clauses = new BLSpanQuery[1];
		clauses[0] = null;
		baseFieldName = matchAllTokensFieldName;
	}

	/**
	 * Return a query that matches all tokens in a field.
	 *
	 * @param ignoreLastToken if true, we assume the last token is always a special closing
	 *     token and ignore it (special closing token is required for punctuation after the last word)
	 * @param fieldName the field from which to match
	 * @return the resulting query
	 */
	public static BLSpanQuery matchAllTokens(boolean ignoreLastToken, String fieldName) {
		SpanQueryNot spanQueryNot = new SpanQueryNot(fieldName);
		spanQueryNot.setIgnoreLastToken(ignoreLastToken);
		return spanQueryNot;
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		BLSpanQuery[] rewritten = rewriteClauses(reader);
		if (rewritten == null)
			return this;
		SpanQueryNot result = new SpanQueryNot(rewritten[0]);
		if (ignoreLastToken)
			result.setIgnoreLastToken(true);
		return result;
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		SpanQuery query = clauses[0];
		SpanWeight weight = query == null ? null : query.createWeight(searcher, needsScores);
		return new SpanWeightNot(weight, searcher, needsScores ? getTermContexts(weight) : null);
	}

	public class SpanWeightNot extends SpanWeight {

		final SpanWeight weight;

		public SpanWeightNot(SpanWeight weight, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQueryNot.this, searcher, terms);
			this.weight = weight;
		}

		@Override
		public void extractTerms(Set<Term> terms) {
			if (weight != null)
				weight.extractTerms(terms);
		}

		@Override
		public void extractTermContexts(Map<Term, TermContext> contexts) {
			if (weight != null)
				weight.extractTermContexts(contexts);
		}

		@Override
		public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
			Spans spans = weight == null ? null : weight.getSpans(context, requiredPostings);
			return new SpansNot(ignoreLastToken, context.reader(), baseFieldName, spans);
		}

	}

	@Override
	public String toString(String field) {
		return "SpanQueryNot(" + (clauses[0] == null ? "" : clausesToString(field)) + ")";
	}

	/** Set whether to ignore the last token.
	 *
	 * @param ignoreLastToken if true, we assume the last token is always a special closing token and ignore it
	 */
	public void setIgnoreLastToken(boolean ignoreLastToken) {
		this.ignoreLastToken = ignoreLastToken;
	}

}
