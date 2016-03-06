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

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternAndNot;
import nl.inl.blacklab.search.TextPatternEdge;
import nl.inl.blacklab.search.TextPatternOr;
import nl.inl.blacklab.search.TextPatternPositionFilter;
import nl.inl.blacklab.search.TextPatternPositionFilter.Operation;
import nl.inl.blacklab.search.TextPatternTranslator;

/**
 * A sequence of patterns. The patterns specified may be any pattern, and may themselves be
 * sequences if desired.
 */
public class TextPatternSequence extends TextPatternAndNot {
	public TextPatternSequence(TextPattern... clauses) {
		super(clauses);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		if (!exclude.isEmpty())
			throw new RuntimeException("clausesNot not empty!");

		List<T> chResults = new ArrayList<>();

		// Keep track of which clauses can match the empty sequence. Use this to build alternatives
		// at the end. (see makeAlternatives)
//		List<Boolean> matchesEmptySeq = new ArrayList<Boolean>();

		// Translate the clauses
		for (TextPattern cl: include) {
			// Translate this part of the sequence
			T translated = cl.translate(translator, context);
			boolean translatedMatchesEmpty = cl.matchesEmptySequence();
			if (translatedMatchesEmpty)
				throw new RuntimeException("Sequence part matches empty sequence. TextPattern should have been rewritten!");

			chResults.add(translated);
//			matchesEmptySeq.add(translatedMatchesEmpty);
		}

		// Is it still a sequence, or just one part?
		if (chResults.size() == 1)
			return chResults.get(0); // just one part, return that

		return translator.sequence(context, chResults);

		// Multiple parts; create sequence object
		//return makeAlternatives(translator, context, chResults, matchesEmptySeq);
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
	 * @param context the query execution context
	 * @param chResults translation results for each of the clauses so far
	 * @param matchesEmptySeq whether each of the clauses matches the empty sequence
	 * @return several alternatives combined with or
	 */
	@Deprecated
	public <T> T makeAlternatives(TextPatternTranslator<T> translator, QueryExecutionContext context,
			List<T> chResults, List<Boolean> matchesEmptySeq) {
		if (chResults.size() == 1) {
			// Last clause in the sequence; just return it
			return chResults.get(0);
		}

		// Recursively determine the query for the "tail" of the list
		List<T> resultsRest = chResults.subList(1, chResults.size());
		List<Boolean> emptyRest = matchesEmptySeq.subList(1, matchesEmptySeq.size());
		T rest = makeAlternatives(translator, context, resultsRest, emptyRest);
		boolean restMatchesEmpty = true;
		for (int i = 1; i < matchesEmptySeq.size(); i++) {
			if (!matchesEmptySeq.get(i)) {
				restMatchesEmpty = false;
				break;
			}
		}

		// Now, add the head part and check if it could match the empty sequence
		T firstPart = chResults.get(0);
		boolean firstPartMatchesEmpty = matchesEmptySeq.get(0);
		T result = translator.sequence(context, Arrays.asList(firstPart, rest));
		if (firstPartMatchesEmpty) {
			List<T> alternatives;
			if (restMatchesEmpty) {
				// Both can match empty sequence. Generate 2 additional alternatives
				// (only the first part, only the rest part (both empty doesn't make sense))
				alternatives = Arrays.asList(
					result,
					translator.sequence(context, Arrays.asList(rest)),
					translator.sequence(context, Arrays.asList(firstPart)));
			} else {
				// Yes, head matches empty sequence. Also include sequence without the head.
				alternatives = Arrays.asList(result,
						translator.sequence(context, Arrays.asList(rest)));
			}
			result = translator.or(context, alternatives);
		} else if (restMatchesEmpty) {
			// Yes, rest matches empty sequence. Also include sequence without the rest.
			List<T> alternatives = Arrays.asList(
				result,
				translator.sequence(context, Arrays.asList(firstPart)));
			result = translator.or(context, alternatives);
		}
		return result;
	}

	@Override
	public boolean matchesEmptySequence() {
		for (TextPattern cl : include) {
			if (!cl.matchesEmptySequence())
				return false;
		}
		return true;
	}

	@Override
	public TextPattern noEmpty() {
		if (!matchesEmptySequence())
			return this;
		throw new RuntimeException("Sequence should have been rewritten!");
	}

	@Override
	public TextPattern rewrite() {
		if (!exclude.isEmpty())
			throw new RuntimeException("clausesNot not empty!");

		boolean anyRewritten = false;

		// Flatten nested sequences.
		// This doesn't change the query because the sequence operator is associative.
		List<TextPattern> flat = new ArrayList<>();
		for (TextPattern child: include) {
			boolean nestedSequence = child instanceof TextPatternSequence;
			if (nestedSequence) {
				// Child sequence we want to flatten into this sequence.
				// Replace the child, incorporating the child sequence into the rewritten sequence
				((TextPatternSequence)child).getFlatSequence(flat);
				anyRewritten = true;
			} else {
				// Not nested
				flat.add(child);
			}
		}

		// Try to match separate start and end tags in this sequence, and convert into a
		// containing query. (<s> []* 'bla' []* </s> ==> <s/> containing 'bla')
		for (int i = 0; i < flat.size(); i++) {
			TextPattern clause = flat.get(i);
			if (clause instanceof TextPatternEdge) {
				TextPatternEdge start = (TextPatternEdge)clause;
				if (!start.isRightEdge()) {
					String tagName = start.getElementName();
					if (tagName != null) {
						// Start tag found. Is there a matching end tag?
						for (int j = i + 1; j < flat.size(); j++) {
							TextPattern clause2 = flat.get(j);
							if (clause2 instanceof TextPatternEdge) {
								TextPatternEdge end = (TextPatternEdge)clause2;
								if (end.isRightEdge() && end.getElementName().equals(tagName)) {
									// Found start and end tags in sequence. Convert to containing query.
									List<TextPattern> search = new ArrayList<>();
									flat.remove(i); // start tag
									for (int k = 0; k < j - i - 1; k++) {
										search.add(flat.remove(i));
									}
									flat.remove(i); // end tag
									boolean startAny = false;
									if (search.get(0) instanceof TextPatternAnyToken) {
										TextPatternAnyToken any1 = (TextPatternAnyToken)search.get(0);
										if (any1.getMinLength() == 0 && any1.getMaxLength() == -1) {
											startAny = true;
											search.remove(0);
										}
									}
									boolean endAny = false;
									int last = search.size() - 1;
									if (search.get(last) instanceof TextPatternAnyToken) {
										TextPatternAnyToken any2 = (TextPatternAnyToken)search.get(last);
										if (any2.getMinLength() == 0 && any2.getMaxLength() == -1) {
											endAny = true;
											search.remove(last);
										}
									}
									TextPattern producer = start.getClause();
									TextPattern filter = new TextPatternSequence(search.toArray(new TextPattern[0]));
									Operation op;
									if (startAny) {
										if (endAny) {
											op = Operation.CONTAINING;
										} else {
											op = Operation.CONTAINING_AT_END;
										}
									} else {
										if (endAny) {
											op = Operation.CONTAINING_AT_START;
										} else {
											op = Operation.MATCHES;
										}
									}
									flat.add(i, new TextPatternPositionFilter(producer, filter, op));
									anyRewritten = true;
								}
							}
						}
					}
				}
			}
		}

		// Rewrite all clauses and flatten again if necessary.
		for (int i = 0; i < flat.size(); i++) {
			TextPattern child = flat.get(i);
			TextPattern rewritten = child.rewrite();
			boolean nestedSequence = rewritten instanceof TextPatternSequence;
			if (child != rewritten || nestedSequence) {
				anyRewritten = true;
				if (nestedSequence) {
					// Child sequence we want to flatten into this sequence.
					// Replace the child, incorporating the child sequence into the rewritten sequence
					flat.remove(i);
					flat.addAll(i, ((TextPatternSequence)child).include);
				} else {
					// Replace the child with the rewritten version
					flat.set(i, rewritten);
				}
			}
		}

		// Now, see what parts of the sequence can be combined into more efficient queries:
		// - repeating clauses can be turned into a single repetition clause.
		// - anytoken clauses can be combined into expansion clauses, which can be
		//   combined again into distance queries
		// - negative clauses can be rewritten to NOTCONTAINING clauses and combined with
		//   adjacent constant-length query parts.
		TextPattern previousPart = null;
		List<TextPattern> seqCombined = new ArrayList<>();
		for (TextPattern child: flat) {
			TextPattern combined = child;
			while (true) {
				// Do we have a previous part?
				previousPart = seqCombined.isEmpty() ? null : seqCombined.get(seqCombined.size() - 1);
				if (previousPart == null)
					break;
				// Yes, try to combine with it.
				TextPattern tryComb = combined.combineWithPrecedingPart(previousPart);
				if (tryComb == null)
					break;
				// Success! Remove previous part and keep trying with the part before that.
				anyRewritten = true;
				seqCombined.remove(seqCombined.size() - 1);
				combined = tryComb;
			}
			if (combined == child) {
				// Could not be combined.
				seqCombined.add(child);
			} else {
				// Combined with previous clause(s).
				seqCombined.add(combined.rewrite());
			}
		}

		// If any part of the sequence matches the empty sequence, we must
		// rewrite it to several alternatives combined with OR. Do so now.
		List<List<TextPattern>> results = makeAlternatives(seqCombined);
		if (results.size() == 1 && !anyRewritten)
			return this;
		List<TextPattern> orCl = new ArrayList<>();
		for (List<TextPattern> seq: results) {
			if (seq.size() == 1)
				orCl.add(seq.get(0));
			else
				orCl.add(new TextPatternSequence(seq.toArray(new TextPattern[0])));
		}
		if (orCl.size() == 1)
			return orCl.get(0);
		return new TextPatternOr(orCl.toArray(new TextPattern[0])).rewrite();
	}

	/**
	 * Given translated clauses, builds several alternatives and combines them with OR.
	 *
	 * This is necessary because of how sequence matching works: first the hits in each
	 * of the clauses are located, then we try to detect valid sequences by looking at these
	 * hits. But when a clause also matches the empty sequence, you may miss valid sequence
	 * matches because there's no hit in the clause to combine with the hits from other clauses.
	 *
	 * @param alternatives the alternative sequences we have built so far
	 * @param parts translation results for each of the clauses so far
	 * @return several alternatives combined with or
	 */
	List<List<TextPattern>> makeAlternatives(List<TextPattern> parts) {
		if (parts.size() == 1) {
			// Last clause in the sequence; just return it
			// (noEmpty() version because we will build alternatives
			//  in the caller if the input matched the empty sequence)
			return Arrays.asList(Arrays.asList(parts.get(0).noEmpty().rewrite()));
		}

		// Recursively determine the query for the "tail" of the list,
		// and whether it matches the empty sequence or not.
		List<TextPattern> partsTail = parts.subList(1, parts.size());
		boolean restMatchesEmpty = true;
		for (TextPattern part: partsTail) {
			if (!part.matchesEmptySequence()) {
				restMatchesEmpty = false;
				break;
			}
		}
		List<List<TextPattern>> altTail = makeAlternatives(partsTail);

		// Now, add the head part and check if that matches the empty sequence.
		return combine(parts.get(0), altTail, restMatchesEmpty);
	}

	private static List<List<TextPattern>> combine(TextPattern head,
			List<List<TextPattern>> tailAlts, boolean tailMatchesEmpty) {
		List<List<TextPattern>> results = new ArrayList<>();
		TextPattern headNoEmpty = head.noEmpty().rewrite();
		boolean headMatchesEmpty = head.matchesEmptySequence();
		for (List<TextPattern> tailAlt: tailAlts) {
			// Add head in front of each tail alternative
			List<TextPattern> n = new ArrayList<>(tailAlt);
			n.add(0, headNoEmpty);
			results.add(n);

			// If head can be empty, also add original tail alternative
			if (headMatchesEmpty)
				results.add(tailAlt);
		}
		// If tail can be empty, also add the head separately
		if (tailMatchesEmpty)
			results.add(Arrays.asList(headNoEmpty));
		return results;
	}

	private List<TextPattern> getFlatSequence(List<TextPattern> flat) {
		for (TextPattern child: include) {
			boolean nestedSequence = child instanceof TextPatternSequence;
			if (nestedSequence) {
				// Child sequence we want to flatten into this sequence.
				// Replace the child, incorporating the child sequence into the rewritten sequence
				((TextPatternSequence)child).getFlatSequence(flat);
			} else {
				// Not nested
				flat.add(child);
			}
		}
		return flat;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternSequence) {
			return super.equals(obj);
		}
		return false;
	}

	@Override
	public boolean hasConstantLength() {
		for (TextPattern clause: include) {
			if (!clause.hasConstantLength())
				return false;
		}
		return true;
	}

	@Override
	public int getMinLength() {
		int n = 0;
		for (TextPattern clause: include) {
			n += clause.getMinLength();
		}
		return n;
	}

	@Override
	public int getMaxLength() {
		int n = 0;
		for (TextPattern clause: include) {
			int max = clause.getMaxLength();
			if (max < 0)
				return -1; // infinite
			n += max;
		}
		return n;
	}

	@Override
	public String toString(QueryExecutionContext context) {
		return "SEQ(" + clausesToString(include, context) + ")";
	}
}
