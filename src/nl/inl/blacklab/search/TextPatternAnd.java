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
 *
 * When generating a Query, a simple document-level AND is used, so in the above case, this would
 * generate the query "documents that contains an adjective and a word starting with
 * 'b'". As always, SpanQuery document results are the "most correct" and are a subset of the Query
 * results.
 */
public class TextPatternAnd extends TextPatternCombiner {
	public TextPatternAnd(TextPattern... clauses) {
		super(clauses);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, String fieldName) {
		List<T> chResults = new ArrayList<T>(clauses.size());
		for (TextPattern cl : clauses) {
			chResults.add(cl.translate(translator, fieldName));
		}
		if (chResults.size() == 1)
			return chResults.get(0);
		return translator.and(fieldName, chResults);
	}
}
