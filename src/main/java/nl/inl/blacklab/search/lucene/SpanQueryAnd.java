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

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;

/**
 * Combines SpanQueries using AND. Note that this means that only matches with the same document id,
 * the same start and the same end positions in all SpanQueries will be kept.
 */
public class SpanQueryAnd extends SpanQueryBase {
	public SpanQueryAnd(SpanQuery first, SpanQuery second) {
		super(first, second);
	}

	public SpanQueryAnd(Collection<SpanQuery> clauscol) {
		super(clauscol);
	}

	public SpanQueryAnd(SpanQuery[] _clauses) {
		super(_clauses);
	}

	@Override
	public Spans getSpans(LeafReaderContext context, Bits acceptDocs,
			Map<Term, TermContext> termContexts) throws IOException {
		Spans combi = clauses[0].getSpans(context, acceptDocs, termContexts);
		for (int i = 1; i < clauses.length; i++) {
			Spans si = clauses[i].getSpans(context, acceptDocs, termContexts);
			if (combi == null)
				return null; // if no hits in one of the clauses, no hits in and query
			combi = new SpansAnd(combi, si);
		}

		return combi;
	}

	@Override
	public String toString(String field) {
		return "SpanQueryAnd(" + clausesToString(field) + ")";
	}
}
