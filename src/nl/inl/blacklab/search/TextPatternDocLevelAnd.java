/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search;

import java.util.ArrayList;
import java.util.List;

/**
 * A TextPattern returning hits from all clauses, but only in documents that match all clauses.
 */
public class TextPatternDocLevelAnd extends TextPatternCombiner {
	public TextPatternDocLevelAnd(TextPattern... clauses) {
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
		return translator.docLevelAnd(fieldName, chResults);
	}
}
