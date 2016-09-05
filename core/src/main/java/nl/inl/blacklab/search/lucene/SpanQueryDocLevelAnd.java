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
/*
 * SpanAndQuery.java
 *
 * Created on May 11, 2006, 4:37 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;

/**
 * A SpanQuery for and AND-construction at the document level.
 */
public class SpanQueryDocLevelAnd extends SpanQueryBase {
	public SpanQueryDocLevelAnd(SpanQuery first, SpanQuery second) {
		super(first, second);
	}

	public SpanQueryDocLevelAnd(Collection<SpanQuery> clauscol) {
		super(clauscol);
	}

	public SpanQueryDocLevelAnd(SpanQuery[] _clauses) {
		super(_clauses);
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		List<SpanWeight> weights = new ArrayList<>();
		for (SpanQuery clause: clauses) {
			weights.add(clause.createWeight(searcher, needsScores));
		}
		Map<Term, TermContext> contexts = needsScores ? getTermContexts(weights.toArray(new SpanWeight[0])) : null;
		return new SpanWeightDocLevelAnd(weights, searcher, contexts);
	}

	public class SpanWeightDocLevelAnd extends SpanWeight {

		final List<SpanWeight> weights;

		public SpanWeightDocLevelAnd(List<SpanWeight> weights, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQueryDocLevelAnd.this, searcher, terms);
			this.weights = weights;
		}

		@Override
		public void extractTerms(Set<Term> terms) {
			for (SpanWeight weight: weights) {
				weight.extractTerms(terms);
			}
		}

		@Override
		public void extractTermContexts(Map<Term, TermContext> contexts) {
			for (SpanWeight weight: weights) {
				weight.extractTermContexts(contexts);
			}
		}

		@Override
		public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
			Spans combi = weights.get(0).getSpans(context, requiredPostings);
			for (int i = 1; i < weights.size(); i++) {
				Spans si = weights.get(i).getSpans(context, requiredPostings);
				if (combi == null || si == null)
					return null; // if no hits in one of the clauses, no hits in and query
				combi = new SpansDocLevelAnd(combi, si);
			}
			return combi;
		}
	}

	@Override
	public String toString(String field) {
		return "SpanQueryDocLevelAnd(" + clausesToString(field) + ")";
	}
}
