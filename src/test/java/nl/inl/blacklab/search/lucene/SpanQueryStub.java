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
import java.util.Set;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;

/**
 * Stub SpanQuery class for testing. Takes arrays and iterates through 'hits'
 * from these arrays.
 */
public class SpanQueryStub extends SpanQuery {
	private int[] doc;

	private int[] start;

	private int[] end;

	public SpanQueryStub(int[] doc, int[] start, int[] end) {
		this.doc = doc;
		this.start = start;
		this.end = end;
	}

	@Override
	public Spans getSpans(AtomicReaderContext arg0, Bits arg1, Map<Term, TermContext> arg2)
			throws IOException {
		return new SpansStub(doc, start, end);
	}
	@Override
	public String toString(String field) {
		return "SpanQueryStub()";
	}

	@Override
	public String getField() {
		return "stub";
	}

	@Override
	public void extractTerms(Set<Term> terms) {
		// (no terms)
	}



}
