/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
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
