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

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;

/**
 * Returns either the left edge or right edge of the specified query.
 *
 * Note that the results of this query are zero-length spans.
 */
public class SpanQueryEdge extends SpanQueryBase {

	/** if true, return the right edges; if false, the left */
	private boolean rightEdge;

	/**
	 * Construct SpanQueryEdge object.
	 * @param query the query to determine edges from
	 * @param rightEdge if true, return the right edges; if false, the left
	 */
	public SpanQueryEdge(SpanQuery query, boolean rightEdge) {
		super(query);
		this.rightEdge = rightEdge;
	}

	@Override
	public Spans getSpans(LeafReaderContext context, Bits acceptDocs, Map<Term,TermContext> termContexts)  throws IOException {
		Spans result = new SpansEdge(clauses[0].getSpans(context, acceptDocs, termContexts), rightEdge);

		return result;
	}

	@Override
	public String toString(String field) {
		return "SpanQueryEdge(" + clausesToString(field, " & ") + ")";
	}
}
