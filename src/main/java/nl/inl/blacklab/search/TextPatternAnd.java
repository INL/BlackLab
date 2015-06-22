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
package nl.inl.blacklab.search;

import java.util.ArrayList;
import java.util.List;

/**
 * AND query for combining different properties from a complex field.
 *
 * Note that when generating a SpanQuery, the Span start and end are also matched! Therefore only
 * two hits in the same document at the same start and end position will produce a match. This is
 * useful for e.g. selecting adjectives that start with a 'b' (queries on different property
 * (sub)fields that should apply to the same word).
 */
public class TextPatternAnd extends TextPatternCombiner {
	public TextPatternAnd(TextPattern... clauses) {
		super(clauses);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		List<T> chResults = new ArrayList<T>(clauses.size());
		for (TextPattern cl : clauses) {
			chResults.add(cl.translate(translator, context));
		}
		if (chResults.size() == 1)
			return chResults.get(0);
		return translator.and(context, chResults);
	}

	/*

	NOTE: this code rewrites AND queries containing only NOT children
	into OR queries. It is not currently used but we may want to use something
	like this in the future to better optimize certain queries, so we'll leave
	it here for now.

	@Override
	public TextPattern rewrite() {
		boolean hasOnlyNotChildren = true;
		for (TextPattern child: clauses) {
			if (!(child instanceof TextPatternNot)) {
				hasOnlyNotChildren = false;
				break;
			}
		}
		if (hasOnlyNotChildren) {
			// Node should be rewritten to OR
			TextPattern[] rewrittenAndInv = new TextPattern[clauses.size()];
			for (int i = 0; i < clauses.size(); i++) {
				rewrittenAndInv[i] = clauses.get(i).rewrite().inverted();
			}
			return new TextPatternNot(new TextPatternOr(rewrittenAndInv));
		}
		// Node need not be rewritten
		return this;
	}

	*/
}
