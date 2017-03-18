package nl.inl.blacklab.search.lucene;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.SpanTermQuery.SpanTermWeight;
import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.fimatch.NfaFragment;
import nl.inl.blacklab.search.fimatch.NfaState;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;

/**
 * BL-specific subclass of SpanTermQuery that changes what getField() returns
 * (the complex field name instead of the full Lucene field name) in order to be
 * able to combine queries in different Lucene fields using AND and OR. Also makes
 * sure the SpanWeight returned by createWeight() produces a BLSpans, not a regular
 * Spans.
 */
public class BLSpanTermQuery extends BLSpanQuery {

	public static BLSpanTermQuery from(SpanTermQuery q) {
		return new BLSpanTermQuery(q);
	}

	SpanTermQuery query;

	private TermContext termContext;

	/** Construct a SpanTermQuery matching the named term's spans.
	 *
	 * @param term term to search
	 */
	public BLSpanTermQuery(Term term) {
		query = new SpanTermQuery(term);
		termContext = null;
	}

	BLSpanTermQuery(SpanTermQuery termQuery) {
		this(termQuery.getTerm());
	}

	/**
	 * Expert: Construct a SpanTermQuery matching the named term's spans, using
	 * the provided TermContext.
	 *
	 * @param term term to search
	 * @param context TermContext to use to search the term
	 */
	public BLSpanTermQuery(Term term, TermContext context) {
		query = new SpanTermQuery(term, context);
		termContext = context;
	}

	@Override
	public String getRealField() {
		return query.getTerm().field();
	}

	@Override
	public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		final TermContext context;
		final IndexReaderContext topContext = searcher.getTopReaderContext();
		if (termContext == null || termContext.topReaderContext != topContext) {
			context = TermContext.build(topContext, query.getTerm());
		} else {
			context = termContext;
		}
		Map<Term, TermContext> contexts = needsScores ? Collections.singletonMap(query.getTerm(), context) : null;
		final SpanTermWeight weight = query.new SpanTermWeight(context, searcher, contexts);
		return new BLSpanWeight(this, searcher, contexts) {
			@Override
			public void extractTermContexts(Map<Term, TermContext> contexts) {
				weight.extractTermContexts(contexts);
			}

			@Override
			public BLSpans getSpans(LeafReaderContext ctx, Postings requiredPostings) throws IOException {
				Spans spans = weight.getSpans(ctx, requiredPostings);
				return spans == null ? null : new BLSpansWrapper(spans);
			}

			@Override
			public void extractTerms(Set<Term> terms) {
				weight.extractTerms(terms);
			}
		};
	}

	@Override
	public String toString(String field) {
		return "TERM(" + query + ")";
	}

	@Override
	public int hashCode() {
		return query.hashCode() ^ 0xB1ACC1AB;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof BLSpanTermQuery) {
			BLSpanTermQuery other = (BLSpanTermQuery) obj;
			return query.equals(other.query);
		}
		return false;
	}

	@Override
	public boolean hitsAllSameLength() {
		return true;
	}

	@Override
	public int hitsLengthMin() {
		return 1;
	}

	@Override
	public int hitsLengthMax() {
		return 1;
	}

	@Override
	public boolean hitsEndPointSorted() {
		return true;
	}

	@Override
	public boolean hitsStartPointSorted() {
		return true;
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return true;
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return true;
	}

	@Override
	public boolean hitsAreUnique() {
		return true;
	}

	@Override
	public NfaFragment getNfa(ForwardIndexAccessor fiAccessor, int direction) {
		Term term = query.getTerm();
		String propertyName = ComplexFieldUtil.getNameComponents(term.field())[1];
		int propertyNumber = fiAccessor.getPropertyNumber(propertyName);
		String propertyValue = term.text();
		int termNumber = fiAccessor.getTermNumber(propertyNumber, propertyValue);
		NfaState state = NfaState.token(propertyNumber, termNumber, null);
		return new NfaFragment(state, Arrays.asList(state));
	}

	@Override
	public boolean canMakeNfa() {
		return true;
	}

	@Override
	public long estimatedNumberOfHits(IndexReader reader) {
		try {
			return reader.totalTermFreq(query.getTerm());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
