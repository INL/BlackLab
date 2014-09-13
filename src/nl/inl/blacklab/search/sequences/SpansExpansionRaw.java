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

import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.BLSpansWrapper;
import nl.inl.blacklab.search.lucene.DocFieldLengthGetter;
import nl.inl.blacklab.search.lucene.HitQueryContext;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.search.spans.Spans;

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
 * and eliminates duplicates.
 */
class SpansExpansionRaw extends BLSpans {
	private BLSpans clause;

	private boolean more = true;

	private boolean clauseNexted;

	private boolean expandToLeft;

	private int min;

	private int max;

	private int start;

	private int end;

	private int expandStepsLeft = 0;

	/** For which document do we have the token length? */
	private int tokenLengthDocId = -1;

	/** Token length for the doc specified in tokenLengthDocId */
	private int tokenLength;

	/** Used to get the field length in tokens for a document */
	DocFieldLengthGetter lengthGetter;

	public SpansExpansionRaw(AtomicReader reader, String fieldName, Spans clause, boolean expandToLeft, int min, int max) {
		if (!expandToLeft) {
			// We need to know document length to properly do expansion to the right
			lengthGetter = new DocFieldLengthGetter(reader, fieldName);
		}
		this.clause = BLSpansWrapper.optWrap(clause);
		clauseNexted = false;
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

	/**
	 * @return the Lucene document id of the current hit
	 */
	@Override
	public int doc() {
		return clause.doc();
	}

	/**
	 * @return end position of current hit
	 */
	@Override
	public int end() {
		return end;
	}

	/**
	 * Go to the next match.
	 *
	 * @return true if we're on a valid match, false if we're done.
	 * @throws IOException
	 */
	@Override
	public boolean next() throws IOException {
		if (!more)
			return false;

		if (expandStepsLeft > 0) {
			expandStepsLeft--;
			if (expandToLeft) {
				start--;

				// Valid expansion?
				if (start >= 0)
					return true;

				// Can't finish the expansion because we're at the start
				// of the document; go to next hit.
			} else {
				// We can't validate the end expansion because we don't know the length of the
				// content of this field. So expansion to the right always succeeds, and may
				// yield invalid hits. If a query ends with wildcard tokens, you should be aware of
				// this and take steps to eliminate these invalid hits.
				end++;
				return true;
			}
		}

		more = clause.next();
		clauseNexted = true;
		if (more)
			return resetExpand();
		return false;
	}

	/**
	 * Go to the specified document, if it has hits. If not, go to the next document containing
	 * hits.
	 *
	 * @param doc
	 *            the document number to skip to / over
	 * @return true if we're at a valid hit, false if not
	 * @throws IOException
	 */
	@Override
	public boolean skipTo(int doc) throws IOException {
		if (!more)
			return false;
		if (!clauseNexted || clause.doc() < doc) {
			more = clause.skipTo(doc);
			if (more)
				return resetExpand();
			return false;
		}
		return next(); // per Lucene's specification, always at least go to the next hit
	}

	/**
	 * The source clause is at a new hit. Reset the expansion process.
	 *
	 * Note that we may discover we can't do the minimum expansion (because the hit is at the start
	 * of the document, for example), so we may have to advance the clause again, and may actually
	 * run out of hits while doing so.
	 *
	 * @return true if we're at a valid hit and have reset the expansion, false if we're done
	 * @throws IOException
	 */
	private boolean resetExpand() throws IOException {
		while (true) {
			// Attempt to do the initial expansion and reset the counter
			start = clause.start();
			end = clause.end();
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
				if (clause.doc() != tokenLengthDocId) {
					// No, determine length now
					tokenLengthDocId = clause.doc();
					tokenLength = lengthGetter.getFieldLength(tokenLengthDocId);
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
			if (/*start >= 0 &&*/ expandStepsLeft >= 0)
				return true; // Yes, return

			// No, try the next hit, if there one
			if (!next())
				return false; // No hits left, we're done
		}
	}

	/**
	 * @return start of the current hit
	 */
	@Override
	public int start() {
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
		return clause.hitsHaveUniqueStart() && (!expandToLeft || expandToLeft && min == max);
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return clause.hitsHaveUniqueEnd() && (expandToLeft || !expandToLeft && min == max);
	}

	@Override
	public boolean hitsAreUnique() {
		return clause.hitsAreUnique() && min == max;
	}

	@Override
	public void setHitQueryContext(HitQueryContext context) {
		clause.setHitQueryContext(context);
	}
}
