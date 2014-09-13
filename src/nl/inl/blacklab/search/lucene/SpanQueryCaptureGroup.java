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

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;

/**
 * Captures its clause as a capture group.
 */
public class SpanQueryCaptureGroup extends SpanQueryBase {

	private String name;

	/**
	 * Construct SpanQueryCaptureGroup object.
	 * @param query the query to determine edges from
	 * @param name capture group name
	 */
	public SpanQueryCaptureGroup(SpanQuery query, String name) {
		super(query);
		this.name = name;
	}

	@Override
	public Spans getSpans(AtomicReaderContext context, Bits acceptDocs, Map<Term,TermContext> termContexts)  throws IOException {
		return new SpansCaptureGroup(clauses[0].getSpans(context, acceptDocs, termContexts), name);
	}

	@Override
	public String toString(String field) {
		return "SpanQueryCaptureGroup(" + clausesToString(field, " & ") + ", " + name + ")";
	}
}
