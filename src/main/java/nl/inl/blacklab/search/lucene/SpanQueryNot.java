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
 * Returns all tokens that do not occur in the matches
 * of the specified query.
 *
 * Each token is returned as a single hit.
 */
public class SpanQueryNot extends SpanQueryBase {

	/** if true, we assume the last token is always a special closing token and ignore it */
	boolean ignoreLastToken = false;

	public SpanQueryNot(SpanQuery query) {
		super(query);
	}

	public SpanQueryNot(Collection<SpanQuery> clauscol) {
		super(clauscol);
	}

	public SpanQueryNot(SpanQuery[] _clauses) {
		super(_clauses);
	}

	/**
	 * A SpanQuery that simply matches all tokens in a field
	 * @param matchAllTokensFieldName what field to match all tokens in
	 */
	private SpanQueryNot(String matchAllTokensFieldName) {
		clauses = new SpanQuery[1];
		clauses[0] = null;
		baseFieldName = matchAllTokensFieldName;
	}

	public static SpanQuery matchAllTokens(boolean ignoreLastToken, String fieldName) {
		SpanQueryNot spanQueryNot = new SpanQueryNot(fieldName);
		spanQueryNot.setIgnoreLastToken(ignoreLastToken);
		return spanQueryNot;
	}

	@Override
	public Spans getSpans(LeafReaderContext context, Bits acceptDocs, Map<Term,TermContext> termContexts)  throws IOException {
		SpanQuery query = clauses[0];
		Spans result = new SpansNot(ignoreLastToken, context.reader(), baseFieldName, query == null ? null : query.getSpans(context, acceptDocs, termContexts));

		return result;
	}

	@Override
	public String toString(String field) {
		return "SpanQueryNot(" + (clauses[0] == null ? "" : clausesToString(field, " & ")) + ")";
	}

	/** Set whether to ignore the last token.
	 *
	 * @param ignoreLastToken if true, we assume the last token is always a special closing token and ignore it
	 */
	public void setIgnoreLastToken(boolean ignoreLastToken) {
		this.ignoreLastToken = ignoreLastToken;
	}

}
