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
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
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
	 * A BLSpanQuery that simply matches all tokens in a field
	 * @param matchAllTokensFieldName what field to match all tokens in
	 */
	private SpanQueryNot(String matchAllTokensFieldName) {
		clauses = Arrays.asList((BLSpanQuery)null);
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
		BLSpanQuery rewritten = clauses.get(0).rewrite(reader);

		// Can we cancel out a double not?
		if (rewritten.okayToInvertForOptimization())
			return rewritten.inverted(); // yes

		// No, must remain a NOT
		if (rewritten == clauses.get(0)) {
			return this;
		}
		SpanQueryNot result = new SpanQueryNot(rewritten);
		if (ignoreLastToken)
			result.setIgnoreLastToken(true);
		return result;
	}

	@Override
	public boolean hasConstantLength() {
		return true;
	}

	@Override
	public int getMinLength() {
		return 1;
	}

	@Override
	public int getMaxLength() {
		return 1;
	}

	@Override
	public BLSpanQuery inverted() {
		return clauses.get(0); // Just return our clause, dropping the NOT operation
	}

	@Override
	protected boolean okayToInvertForOptimization() {
		// Yes, inverting is actually an improvement
		return true;
	}

	@Override
	public boolean isSingleTokenNot() {
		return true;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof SpanQueryNot) {
			return super.equals(obj);
		}
		return false;
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		BLSpanQuery query = clauses.get(0);
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
		return "NOT(" + (clauses.get(0) == null ? "" : clausesToString(field)) + ")";
	}

	/** Set whether to ignore the last token.
	 *
	 * @param ignoreLastToken if true, we assume the last token is always a special closing token and ignore it
	 */
	public void setIgnoreLastToken(boolean ignoreLastToken) {
		this.ignoreLastToken = ignoreLastToken;
	}

}
