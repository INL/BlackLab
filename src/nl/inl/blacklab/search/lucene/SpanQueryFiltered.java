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

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;

/**
 * Filters a SpanQuery.
 */
public class SpanQueryFiltered extends SpanQueryBase {

	private DocIdSet docIdSet;

	public SpanQueryFiltered(SpanQuery source, Filter filter, AtomicReader reader) throws IOException {
		this(source, filter.getDocIdSet(reader.getContext(), reader.getLiveDocs()));
	}

	public SpanQueryFiltered(SpanQuery source, DocIdSet docIdSet) {
		super(source);
		this.docIdSet = docIdSet;
	}

	@Override
	public Spans getSpans(AtomicReaderContext context, Bits acceptDocs, Map<Term,TermContext> termContexts)  throws IOException {
		Spans result = clauses[0].getSpans(context, acceptDocs, termContexts);
		if (docIdSet != null)
			result = new SpansFiltered(result, docIdSet);
		return result;
	}

	@Override
	public String toString(String field) {
		return "SpanQueryFiltered(" + clausesToString(field, " & ") + ", " + docIdSet + ")";
	}
}
