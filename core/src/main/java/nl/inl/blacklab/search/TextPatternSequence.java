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

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQuerySequence;

/**
 * A sequence of patterns. The patterns specified may be any pattern, and may themselves be
 * sequences if desired.
 */
public class TextPatternSequence extends TextPatternAndNot {
	public TextPatternSequence(TextPattern... clauses) {
		super(clauses);
	}

	@Override
	public BLSpanQuery translate(QueryExecutionContext context) {
		if (!exclude.isEmpty())
			throw new RuntimeException("clausesNot not empty!");

		List<BLSpanQuery> chResults = new ArrayList<>();

		// Keep track of which clauses can match the empty sequence. Use this to build alternatives
		// at the end. (see makeAlternatives)
//		List<Boolean> matchesEmptySeq = new ArrayList<Boolean>();

		// Translate the clauses
		for (TextPattern cl: include) {
			// Translate this part of the sequence
			BLSpanQuery translated = cl.translate(context);

			chResults.add(translated);
//			matchesEmptySeq.add(translatedMatchesEmpty);
		}

		// Is it still a sequence, or just one part?
		if (chResults.size() == 1)
			return chResults.get(0); // just one part, return that

		return new SpanQuerySequence(chResults);

		// Multiple parts; create sequence object
		//return makeAlternatives(translator, context, chResults, matchesEmptySeq);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternSequence) {
			return super.equals(obj);
		}
		return false;
	}

	@Deprecated
	@Override
	public String toString(QueryExecutionContext context) {
		return "SEQ(" + clausesToString(include, context) + ")";
	}

	@Override
	public String toString() {
		return "SEQ(" + clausesToString(include) + ")";
	}
}
