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
		if (exclude.size() > 0)
			throw new RuntimeException("clausesNot not empty!");

		List<T> chResults = new ArrayList<T>();

		// Keep track of which clauses can match the empty sequence. Use this to build alternatives
		// at the end. (see makeAlternatives)
		List<Boolean> matchesEmptySeq = new ArrayList<Boolean>();

		// To deal with wildcard tokens at the end of a sequence. See below.
		TextPatternAnyToken previousAnyTokensPart = null;

		// Translate the clauses from back to front, because this makes it easier to
		// deal with TextPatternAnyToken (see below).
		for (int i = include.size() - 1; i >= 0; i--) {
			TextPattern cl = include.get(i);

			if (cl instanceof TextPatternAnyToken) {
				// These are a special case, because we shouldn't translate these into a
				// query by themselves. We need to combine them with a query to the left
				// or to the right.
				// (We used to prefer the query to the right, as this prevented us from
				// accidentally going past the end of the document (old version of
				// SpansExpansionRaw), but this issue has since been fixed. We still
				// go through the sequence from right to left for this reason, however)
				TextPatternAnyToken any = (TextPatternAnyToken) cl;
				if (chResults.size() > 0) {
					// Yes, we have a query to the right. Use that.
					T rightNeighbour = chResults.remove(0);
					boolean rightNeighbourMatchesEmpty = matchesEmptySeq.remove(0);
					T result = translator.expand(context, rightNeighbour, true, any.min, any.max);
					chResults.add(0, result);
					matchesEmptySeq
							.add(0, rightNeighbourMatchesEmpty && any.matchesEmptySequence());
				} else {
					// No, we don't have a query to the right, use the one to the left.
					// Of course, we haven't seen that one yet, so save the pattern and
					// handle it in the next loop iteration

					if (previousAnyTokensPart != null) {
						// We already encountered a matchall which we haven't
						// yet processed. Simply add the two together.
						int min = previousAnyTokensPart.min + any.min;
						int max = previousAnyTokensPart.max + any.max;
						if (previousAnyTokensPart.max == -1 || any.max == -1)
							max = -1; // infinite expansion
						previousAnyTokensPart = new TextPatternAnyToken(min, max);
					}
					else
						previousAnyTokensPart = any;
				}
				continue; // done with the AnyToken pattern, on to the next
			}

			// Translate this part of the sequence
			T translated = cl.translate(translator, context);
			boolean translatedMatchesEmpty = cl.matchesEmptySequence();

			// If a wildcard part (TextPatternAnyToken) was found to the right of this,
			// expand this part to the right. This only happens if the AnyToken part is
			// at the end of the sequence.
			if (previousAnyTokensPart != null) {
				translated = translator.expand(context, translated, false, previousAnyTokensPart.min,
						previousAnyTokensPart.max);
				if (translatedMatchesEmpty)
					translatedMatchesEmpty = previousAnyTokensPart.matchesEmptySequence();
				previousAnyTokensPart = null;
			}

			// Insert at start of list, to preserve query order
			chResults.add(0, translated);
			matchesEmptySeq.add(0, translatedMatchesEmpty);
		}

		// Did the whole sequence consist of any-token parts?
		if (chResults.size() == 0 && previousAnyTokensPart != null) {
			// Yes.
			// Translate as-is (don't use expansion). Probably less efficient,
			// but it's the only way to resolve this type of query.
			chResults.add(previousAnyTokensPart.translate(translator, context));
		}

		// Is it still a sequence, or just one part?
		if (chResults.size() == 1)
			return chResults.get(0); // just one part, return that

		// Multiple parts; create sequence object
		return makeAlternatives(translator, context, chResults, matchesEmptySeq);
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
	public <T> T makeAlternatives(TextPatternTranslator<T> translator, QueryExecutionContext context,
			List<T> chResults, List<Boolean> matchesEmptySeq) {
		if (chResults.size() == 1) {
			// Last clause in the sequence; just return it
			// FIXME: what if this matches the empty sequence?
			//        e.g. "the" "quick"?
			//        should match both "the quick" and "the",
			//        but right now, just matches "the quick".
			//        To fix this, we should check that if the "tail" end can
			//        match the empty sequence, not just the "head".
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
	public String toString() {
		StringBuilder b = new StringBuilder();
		for (TextPattern cl : include) {
			if (b.length() > 0)
				b.append(" ");
			b.append(cl.toString());
		}
		return b.toString();
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
	public TextPattern rewrite() {
		if (exclude.size() > 0)
			throw new RuntimeException("clausesNot not empty!");

		boolean anyRewritten = false;

		// Flatten nested sequences.
		// This doesn't change the query because the sequence operator is associative.
		List<TextPattern> flat = new ArrayList<TextPattern>();
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
									List<TextPattern> search = new ArrayList<TextPattern>();
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
		List<TextPattern> withRep = new ArrayList<TextPattern>();
		for (TextPattern child: flat) {
			TextPattern rep = child;
			while (true) {
				// Do we have a previous part?
				previousPart = withRep.size() == 0 ? null : withRep.get(withRep.size() - 1);
				if (previousPart == null)
					break;
				// Yes, try to combine with it.
				TextPattern comb = rep.combineWithPrecedingPart(previousPart);
				if (comb == null)
					break;
				// Success! Remove previous part and keep trying with the part before that.
				withRep.remove(withRep.size() - 1);
				rep = comb;
			}
			if (rep == child) {
				// Could not be combined.
				withRep.add(child);
			} else {
				// Combined with previous clause(s).
				withRep.add(rep.rewrite());
				anyRewritten = true;
			}
		}

		/*
		if (result == null) {
			// No rewrites or subsequence-incorporations done yet,
			// but sequence should be unflattened, so clone it now.
			result = (TextPatternSequence) clone();
		}

		// Now, "unflatten" the sequence into a binary tree, taking care to combine NOT-queries
		// with an adjacent positive query.
		boolean changesMade = true;
		while (result.clauses.size() > 2 && changesMade) {
			changesMade = false;
			for (int i = 0; i < result.clauses.size() - 1; i++) {
				TextPattern child = result.clauses.get(i);

				// NOT-query with adjacent positive query?
				if (child.isNegativeOnly()) {
					TextPattern leftNeighbour = i > 0 ? result.clauses.get(i - 1) : null;
					if (leftNeighbour != null && !leftNeighbour.isNegativeOnly()) {
						// Combine with left query
						TextPattern combined = new TextPatternSequence(leftNeighbour, child);
						result.clauses.remove(i - 1);
						result.replaceClause(child, combined);
						changesMade = true;
					} else if (!result.clauses.get(i + 1).isNegativeOnly()){
						// Combine with right query
						TextPattern combined = new TextPatternSequence(child, result.clauses.get(i + 1));
						result.clauses.remove(i + 1);
						result.replaceClause(child, combined);
						changesMade = true;
					} else {
						// Can't combine with left or right neighbour; maybe next pass.
					}
				} else {
					// Positive query; can and should always be combined with right neighbour
					TextPattern combined = new TextPatternSequence(child, result.clauses.get(i + 1));
					result.clauses.remove(i + 1);
					result.replaceClause(child, combined);
					changesMade = true;
				}
			}
		}
		*/

//		if (result.clauses.size() > 2) {
//			// This can only happen if the sequence consists of all NOT queries, because
//			// that's the only situation in which we can't combine any of them.
//			throw new RuntimeException("Cannot process sequence consisting of all NOT queries");
//		} else if (result.clauses.size() == 2 && result.clauses.get(0).isNegativeOnly() && result.clauses.get(1).isNegativeOnly()) {
//			// TextPatternSequence of 2 (correct) which consists of two NOT queries (incorrect, we can't execute this)
//			throw new RuntimeException("Cannot process sequence consisting of all NOT queries");
//		}

		if (!anyRewritten) {
			// No child clause rewritten: return ourselves.
			return this;
		}
		if (withRep.size() == 1)
			return withRep.get(0);
		return new TextPatternSequence(withRep.toArray(new TextPattern[0]));
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

}
