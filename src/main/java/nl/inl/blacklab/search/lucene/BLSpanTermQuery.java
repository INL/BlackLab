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

import nl.inl.blacklab.index.complex.ComplexFieldUtil;

import org.apache.lucene.index.Fields;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.search.spans.TermSpans;
import org.apache.lucene.util.Bits;

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
public class BLSpanTermQuery extends SpanTermQuery {

	/**
	 * @param term
	 */
	public BLSpanTermQuery(Term term) {
		super(term);
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
		return ComplexFieldUtil.getBaseName(term.field());
	}

	/**
	 * Overridden frmo SpanTermQuery to return a BLSpans instead.
	 */
	@Override
	public Spans getSpans(final LeafReaderContext context, Bits acceptDocs,
			Map<Term, TermContext> termContexts) throws IOException {
		TermContext termContext = termContexts.get(term);
		final TermState state;
		if (termContext == null) {
			// this happens with span-not query, as it doesn't include the NOT
			// side in extractTerms()
			// so we seek to the term now in this segment..., this sucks because
			// its ugly mostly!
			final Fields fields = context.reader().fields();
			if (fields != null) {
				final Terms terms = fields.terms(term.field());
				if (terms != null) {
					final TermsEnum termsEnum = terms.iterator(null);
					if (termsEnum.seekExact(term.bytes())) {
						state = termsEnum.termState();
					} else {
						state = null;
					}
				} else {
					state = null;
				}
			} else {
				state = null;
			}
		} else {
			state = termContext.get(context.ord);
		}

		if (state == null) { // term is not present in that reader
			return TermSpans.EMPTY_TERM_SPANS;
		}

		final TermsEnum termsEnum = context.reader().terms(term.field())
				.iterator(null);
		termsEnum.seekExact(term.bytes(), state);

		PostingsEnum postings = termsEnum.postings(acceptDocs, null, PostingsEnum.POSITIONS | PostingsEnum.OFFSETS);

		if (postings != null) {
			return new TermSpans(postings, term);
		}
		// term does exist, but has no positions
		throw new IllegalStateException(
				"field \""
						+ term.field()
						+ "\" was indexed without position data; cannot run SpanTermQuery (term="
						+ term.text() + ")");
	}
}
