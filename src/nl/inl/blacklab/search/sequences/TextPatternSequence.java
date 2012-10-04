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
package nl.inl.blacklab.search.sequences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternAnd;
import nl.inl.blacklab.search.TextPatternTranslator;

/**
 * A sequence of patterns. The patterns specified may be any pattern, and may themselves be
 * sequences if desired.
 *
 * Note, however, that translation to a Query only returns a correct query if the patterns in the
 * sequence are of type TextPatternTerm; otherwise it delivers a document-level AND Query.
 * Translation to a SpanQuery always works correctly.
 *
 * Translation to a Query, therefore, should only be used as a first filtering tool.
 */
public class TextPatternSequence extends TextPatternAnd {
	public TextPatternSequence(TextPattern... clauses) {
		super(clauses);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, String fieldName) {
		List<T> chResults = new ArrayList<T>();

		// Keep track of which clauses can match the empty sequence. Use this to build alternatives
		// at the end. (see makeAlternatives)
		List<Boolean> matchesEmptySeq = new ArrayList<Boolean>();

		// To deal with wildcard tokens at the end of a sequence. See below.
		TextPatternAnyToken previousAnyTokensPart = null;

		// Translate the clauses from back to front, because this makes it easier to
		// deal with TextPatternAnyToken (see below).
		for (int i = clauses.size() - 1; i >= 0; i--) {
			TextPattern cl = clauses.get(i);

			if (cl instanceof TextPatternAnyToken) {
				// These are a special case, because we cannot translate these into a
				// query by themselves. We need to combine them with a query to the left
				// or to the right.
				// We prefer the query to the right, as this prevents us from accidentally
				// going past the end of the document (see SpansExpansionRaw).
				TextPatternAnyToken any = (TextPatternAnyToken) cl;
				if (chResults.size() > 0) {
					// Yes, we have a query to the right. Use that.
					T rightNeighbour = chResults.remove(0);
					boolean rightNeighbourMatchesEmpty = matchesEmptySeq.remove(0);
					T result = translator.expand(rightNeighbour, true, any.min, any.max);
					chResults.add(0, result);
					matchesEmptySeq
							.add(0, rightNeighbourMatchesEmpty && any.matchesEmptySequence());
				} else {
					// No, we don't have a query to the right, use the one to the left.
					// Of course, we haven't seen that one yet, so save the pattern and
					// handle it in the next loop iteration
					previousAnyTokensPart = any;
				}
				continue; // done with the AnyToken pattern, on to the next
			}

			// Translate this part of the sequence
			T translated = cl.translate(translator, fieldName);
			boolean translatedMatchesEmpty = cl.matchesEmptySequence();

			// If a wildcard part (TextPatternAnyToken) was found to the right of this,
			// expand this part to the right. This only happens if the AnyToken part is
			// at the end of the sequence.
			if (previousAnyTokensPart != null) {
				translated = translator.expand(translated, false, previousAnyTokensPart.min,
						previousAnyTokensPart.max);
				if (translatedMatchesEmpty)
					translatedMatchesEmpty = previousAnyTokensPart.matchesEmptySequence();
				previousAnyTokensPart = null;
			}

			// Insert at start of list, to preserve query order
			chResults.add(0, translated);
			matchesEmptySeq.add(0, translatedMatchesEmpty);
		}

		// Is it still a sequence, or just one part?
		if (chResults.size() == 1)
			return chResults.get(0); // just one part, return that

		// Multiple parts; create sequence object
		return makeAlternatives(translator, fieldName, chResults, matchesEmptySeq);
	}

	/**
	 * Given translated clauses, builds several alternatives and combines them with OR.
	 *
	 * This is necessary because of how sequence matching works: first the hits in each
	 * of the clauses are located, then we try to detect valid sequences by looking at these
	 * hits. But when a clause also matches the empty sequence, you may miss valid sequence
	 * matches because there's no hit in the clause to combine with the hits from other clauses.
	 *
	 * @param <T> type to translate to
	 * @param translator translator
	 * @param fieldName field to search in
	 * @param chResults translation results for each of the clauses so far
	 * @param matchesEmptySeq whether each of the clauses matches the empty sequence
	 * @return several alternatives combined with or
	 */
	@SuppressWarnings("unchecked")
	public <T> T makeAlternatives(TextPatternTranslator<T> translator, String fieldName,
			List<T> chResults, List<Boolean> matchesEmptySeq) {
		if (chResults.size() == 1) {
			return chResults.get(0);
		}

		// Recursively determine the query for the "tail" of the list
		List<T> resultsRest = chResults.subList(1, chResults.size());
		List<Boolean> emptyRest = matchesEmptySeq.subList(1, matchesEmptySeq.size());
		T rest = makeAlternatives(translator, fieldName, resultsRest, emptyRest);

		// Now, add the head part and check if it could match the empty sequence
		T firstPart = chResults.get(0);
		boolean firstPartMatchesEmpty = matchesEmptySeq.get(0);
		T result = translator.sequence(fieldName, Arrays.asList(firstPart, rest));
		if (firstPartMatchesEmpty) {
			// Yes, head matches empty sequence. Also include sequence without the head.
			List<T> alternatives = Arrays.asList(result,
					translator.sequence(fieldName, Arrays.asList(rest)));
			result = translator.or(fieldName, alternatives);
		}
		return result;
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		for (TextPattern cl : clauses) {
			if (b.length() > 0)
				b.append(" ");
			b.append(cl.toString());
		}
		return b.toString();
	}

	@Override
	public boolean matchesEmptySequence() {
		for (TextPattern cl : clauses) {
			if (!cl.matchesEmptySequence())
				return false;
		}
		return true;
	}
}
