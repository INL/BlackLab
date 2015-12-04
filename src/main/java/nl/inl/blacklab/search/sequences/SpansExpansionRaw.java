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

import java.io.IOException;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.BLSpansWrapper;
import nl.inl.blacklab.search.lucene.DocFieldLengthGetter;
import nl.inl.blacklab.search.lucene.HitQueryContext;

/**
 * Expands the source spans to the left and right by the given ranges.
 *
 * This is used to support sequences including subsequences of completely unknown tokens (like
 * <code>"apple" []{2,4} "pear"</code> to find apple and pear with 2 to 4 tokens in between).
 *
 * Note that this class will generate all possible expansions, so if you call it with left-expansion
 * of between 2 to 4 tokens, it will generate 3 new hits for every hit from the source spans: one
 * hit with 2 more tokens to the left, one hit with 3 more tokens to the left, and one hit with 4
 * more tokens to the left.
 *
 * Note that the hits coming out of this class may contain duplicates and will not always be
 * properly sorted. Both are undesirable; the user doesn't want to see duplicates, and Lucene
 * expects Spans to always be sorted by start point (and possible by end point after that, although
 * that is not certain; should be checked).
 *
 * Therefore, objects of this class should be wrapped in a class that sort the matches per document
 * and eliminates duplicates (hence the 'raw' in the name - not suitable for consumption yet).
 */
class SpansExpansionRaw extends BLSpans {

	/** The clause to expand */
	private BLSpans clause;

	/** Whether or not there's more docs in the clause */
	private int currentDoc = -1;

	/** Current startPosition() in the clause */
	private int clauseStart = -1;

	/** Whether to expand to left (true) or right (false) */
	private boolean expandToLeft;

	/** Minimum number of tokens to expand */
	private int min;

	/** Maximum number of tokens to expand (-1 = infinite) */
	private int max;

	/** Start of the current expanded hit */
	private int start = -1;

	/** End of the current expanded hit */
	private int end = -1;

	/** Number of expansion steps left to do for current clause hit */
	private int expandStepsLeft = 0;

	/** For which document do we have the token length? */
	private int tokenLengthDocId = -1;

	/** Token length for the doc specified in tokenLengthDocId */
	private int tokenLength;

	/** Used to get the field length in tokens for a document */
	DocFieldLengthGetter lengthGetter;

	/** How much to subtract from length (for ignoring closing token) */
	private int subtractFromLength;

	private boolean alreadyAtFirstHit;

	public SpansExpansionRaw(boolean ignoreLastToken, LeafReader reader, String fieldName, Spans clause, boolean expandToLeft, int min, int max) {
		subtractFromLength = ignoreLastToken ? 1 : 0;
		if (!expandToLeft) {
			// We need to know document length to properly do expansion to the right
			// TODO: cache this in Searcher..?
			lengthGetter = new DocFieldLengthGetter(reader, fieldName);
		}
		this.clause = BLSpansWrapper.optWrap(clause);
		this.expandToLeft = expandToLeft;
		this.min = min;
		this.max = max;
		if (max != -1 && min > max)
			throw new RuntimeException("min > max");
		if (min < 0 || max < -1)
			throw new RuntimeException("Expansions cannot be negative");
	}

	/** For test, we don't have an index reader, so use default values (all docs are 5 tokens long)
	 * @param b true if we're testing, false if not */
	public void setTest(boolean b) {
		if (lengthGetter != null)
			lengthGetter.setTest(b);
	}

	@Override
	public int docID() {
		return currentDoc;
	}

	@Override
	public int endPosition() {
		if (alreadyAtFirstHit)
			return -1; // .nextStartPosition() not called yet
		return end;
	}

	@Override
	public int nextDoc() throws IOException {
		alreadyAtFirstHit = false;
		if (currentDoc != NO_MORE_DOCS) {
			do {
				currentDoc = clause.nextDoc();
				if (currentDoc == NO_MORE_DOCS)
					return NO_MORE_DOCS;
				start = end = -1;
				clauseStart = clause.nextStartPosition();
				clauseStart = resetExpand();
			} while (clauseStart == NO_MORE_POSITIONS);
			alreadyAtFirstHit = true;
		}
		return currentDoc;
	}

	@Override
	public int nextStartPosition() throws IOException {
		if (alreadyAtFirstHit) {
			alreadyAtFirstHit = false;
			return start;
		}
		if (currentDoc == NO_MORE_DOCS)
			return NO_MORE_POSITIONS;
		if (clauseStart == NO_MORE_POSITIONS)
			return NO_MORE_POSITIONS;

		if (expandStepsLeft > 0) {
			expandStepsLeft--;
			if (expandToLeft) {
				start--;

				// Valid expansion?
				if (start >= 0)
					return start;

				// Can't finish the expansion because we're at the start
				// of the document; go to next hit.
			} else {
				end++;
				return start;
			}
		}

		clauseStart = clause.nextStartPosition();
		clauseStart = resetExpand();
		return clauseStart;
	}

	@Override
	public int advanceStartPosition(int target) throws IOException {
		if (alreadyAtFirstHit) {
			alreadyAtFirstHit = false;
			if (start >= target)
				return start;
		}
		if (currentDoc == NO_MORE_DOCS)
			return NO_MORE_POSITIONS;
		if (clauseStart == NO_MORE_POSITIONS)
			return NO_MORE_POSITIONS;

		if (expandStepsLeft > 0 && start >= target)
			return nextStartPosition(); // we're already there

		clauseStart = clause.advanceStartPosition(target);
		clauseStart = resetExpand();
		return clauseStart;
	}

	@Override
	public int advance(int doc) throws IOException {
		alreadyAtFirstHit = false;
		if (currentDoc != NO_MORE_DOCS) {
			if (currentDoc < doc) {
				currentDoc = clause.advance(doc);
				if (currentDoc != NO_MORE_DOCS) {
					while (true) {
						clauseStart = start = end = -1;
						clauseStart = clause.nextStartPosition();
						clauseStart = resetExpand();
						if (clauseStart != NO_MORE_POSITIONS) {
							alreadyAtFirstHit = true;
							return currentDoc;
						}
						currentDoc = clause.nextDoc();
						if (currentDoc == NO_MORE_DOCS)
							return NO_MORE_DOCS;
					}
				}
			} else {
				nextDoc(); // per Lucene's specification, always at least go to the next doc
			}
		}
		return currentDoc;
	}

	/**
	 * We're at a new position in the source span. Reset the expansion process.
	 *
	 * Note that we may discover we can't do the minimum expansion (because the hit is at the start
	 * of the document, for example), so we may have to advance the clause again, and may actually
	 * run out of hits while doing so.
	 *
	 * @return the start position if we're at a valid hit and have reset the expansion, NO_MORE_POSITIONS if we're done
	 * @throws IOException
	 */
	private int resetExpand() throws IOException {
		if (clauseStart == NO_MORE_POSITIONS) {
			start = end = NO_MORE_POSITIONS;
			return NO_MORE_POSITIONS;
		}
		while (true) {
			// Attempt to do the initial expansion and reset the counter
			start = clauseStart;
			end = clause.endPosition();
			if (expandToLeft)
				start -= min;
			else
				end += min;

			// What's the maximum we could still expand from here?
			int maxExpandSteps;
			if (expandToLeft) {
				// Can only expand to the left until token 0.
				maxExpandSteps = start;
			} else {
				// Can only expand to the right until last token in document.

				// Do we know this document's length already?
				if (currentDoc != tokenLengthDocId) {
					// No, determine length now
					tokenLengthDocId = currentDoc;
					tokenLength = lengthGetter.getFieldLength(tokenLengthDocId) - subtractFromLength;
				}
				maxExpandSteps = tokenLength - end;
			}
			if (max == -1) {
				// Infinite expansion; just use max
				expandStepsLeft = maxExpandSteps;
			}
			else {
				// Limited expansion; clamp by maximum
				expandStepsLeft = Math.min(max - min, maxExpandSteps);
			}

			// Valid expansion?   [shouldn't be necessary anymore because we calculated max]
			if (expandStepsLeft >= 0)
				return start; // Yes, return

			// No, try the next hit, if there is one
			if (nextStartPosition() == NO_MORE_POSITIONS) {
				start = end = NO_MORE_POSITIONS;
				return NO_MORE_POSITIONS; // No hits left, we're done
			}
		}
	}

	/**
	 * @return start of the current hit
	 */
	@Override
	public int startPosition() {
		if (alreadyAtFirstHit)
			return -1; // .nextStartPosition() not called yet
		return start;
	}

	@Override
	public String toString() {
		return "SpansExpansion(" + clause + ", " + expandToLeft + ", " + min + ", " + max + ")";
	}

	@Override
	public boolean hitsEndPointSorted() {
		return clause.hitsEndPointSorted() && (expandToLeft || !expandToLeft && min == max);
	}

	@Override
	public boolean hitsStartPointSorted() {
		return clause.hitsStartPointSorted() && (!expandToLeft || expandToLeft && min == max);
	}

	@Override
	public boolean hitsAllSameLength() {
		return clause.hitsAllSameLength() && min == max;
	}

	@Override
	public int hitsLength() {
		return hitsAllSameLength() ? clause.hitsLength() + min : -1;
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return clause.hitsHaveUniqueStart() && min == max;
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return clause.hitsHaveUniqueEnd() && min == max;
	}

	@Override
	public boolean hitsAreUnique() {
		return clause.hitsAreUnique() && min == max;
	}

	@Override
	public void passHitQueryContextToClauses(HitQueryContext context) {
		clause.setHitQueryContext(context);
	}

	@Override
	public void getCapturedGroups(Span[] capturedGroups) {
		if (!childClausesCaptureGroups)
			return;
		clause.getCapturedGroups(capturedGroups);
	}
}
