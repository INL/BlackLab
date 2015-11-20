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
/**
 *
 */
package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;

/**
 * BL-specific subclass of SpanTermQuery that changes what getField() returns
 * (the complex field name instead of the full Lucene field name) in order to be
 * able to combine queries in different Lucene fields using AND and OR.
 *
 * TODO: investigate FieldMaskingSpanQuery, which seems to have the same
 * purpose. However, we need all our SpanQuery and Spans classes to be
 * BL-derived because we need to have BL-specific methods available on them
 * (i.e. for token tagging).
 */
public class BLSpanTermQuery extends SpanQuery {

	SpanTermQuery q;

	public BLSpanTermQuery(Term term) {
		q = new SpanTermQuery(term);
	}

	/**
	 * Wrap a SpanTermQuery (see BLSpanTermQuery.from())
	 * @param q query to wrap
	 */
	BLSpanTermQuery(SpanTermQuery q) {
		this.q = q;
	}

	/**
	 * Overriding getField to return only the name of the complex field, not the
	 * property name or alt name following after that.
	 *
	 * i.e. for 'contents%lemma@s', this method will just return 'contents',
	 * which is the complex field we're searching.
	 *
	 * This makes it possible to use termqueries with different fieldnames in
	 * the same AND or OR query.
	 *
	 * @return String field
	 */
	@Override
	public String getField() {
		return ComplexFieldUtil.getBaseName(q.getField());
	}

	/**
	 * Overridden frmo SpanTermQuery to return a BLSpans instead.
	 */
	@Override
	public Spans getSpans(final LeafReaderContext context, Bits acceptDocs,
			Map<Term, TermContext> termContexts) throws IOException {
		Spans spans = q.getSpans(context, acceptDocs, termContexts);
		return new BLSpansWrapper(spans);
	}

	@Override
	public String toString(String arg0) {
		return "BL" + q.toString();
	}

	@Override
	public void extractTerms(Set<Term> terms) {
		q.extractTerms(terms);
	}

	@Override
	public void setBoost(float b) {
		q.setBoost(b);
	}

	@Override
	public float getBoost() {
		return q.getBoost();
	}

	@Override
	public int hashCode() {
		return super.hashCode() ^ q.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (super.equals(obj)) {
			return q.equals(((BLSpanTermQuery)obj).q);
		}
		return false;
	}

	/**
	 * Wrap a SpanTermQuery.
	 * @param q query to wrap
	 * @return wrapped query
	 */
	static BLSpanTermQuery from (SpanTermQuery q) {
		return new BLSpanTermQuery(q);
	}

}
