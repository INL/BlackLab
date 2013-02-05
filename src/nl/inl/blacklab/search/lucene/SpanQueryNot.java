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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;

/**
 * Returns all tokens that do not occur in the matches
 * of the specified query.
 *
 * Each token is returned as a single hit.
 */
public class SpanQueryNot extends SpanQueryBase {

	public SpanQueryNot(SpanQuery query) {
		super(query);
	}

	public SpanQueryNot(Collection<SpanQuery> clauscol) {
		super(clauscol);
	}

	public SpanQueryNot(SpanQuery[] _clauses) {
		super(_clauses);
	}

	@Override
	public Spans getSpans(IndexReader reader) throws IOException {
		return new SpansNot(reader, clauses[0].getField(), clauses[0].getSpans(reader));
	}

	@Override
	public String toString(String field) {
		return "SpanQueryNot(" + clausesToString(field, " & ") + ")";
	}
}
