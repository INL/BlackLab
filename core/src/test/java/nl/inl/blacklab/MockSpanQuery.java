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
package nl.inl.blacklab;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * Stub SpanQuery class for testing. Takes arrays and iterates through 'hits'
 * from these arrays.
 */
public class MockSpanQuery extends BLSpanQuery {
	private int[] doc;

	private int[] start;

	private int[] end;

	boolean isSimple;

	public MockSpanQuery(int[] doc, int[] start, int[] end, boolean isSimple) {
		this.doc = doc;
		this.start = start;
		this.end = end;
		this.isSimple = isSimple;
	}

	public MockSpanQuery(int[] doc, int[] start, int[] end) {
		this(doc, start, end, false);
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		return new SpanWeight(this, searcher, null) {

			@Override
			public void extractTerms(Set<Term> terms) {
				// NOP
			}

			@Override
			public void extractTermContexts(Map<Term, TermContext> contexts) {
				// NOP
			}

			@Override
			public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
				return new MockSpans(doc, start, end);
			}
		};
	}

	@Override
	public String toString(String field) {
		return "MockSpanQuery()";
	}

	@Override
	public String getField() {
		return "dummy";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o instanceof MockSpanQuery) {
			final MockSpanQuery that = (MockSpanQuery) o;
			return doc.equals(that.doc) && start.equals(that.start) && end.equals(that.end) && isSimple == that.isSimple;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return doc.hashCode() ^ start.hashCode() ^ end.hashCode() ^ (isSimple ? 0x23357649 : 0);
	}

}
