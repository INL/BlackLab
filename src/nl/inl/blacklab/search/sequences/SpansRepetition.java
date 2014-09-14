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

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.BLSpansWrapper;
import nl.inl.blacklab.search.lucene.HitQueryContext;

import org.apache.lucene.search.spans.Spans;

/**
 * Finds all sequences of consecutive hits from the source spans of the specified min and max
 * lengths. Used to implement repetition operators.
 *
 * This generates all possible sequences of consecutive hits, so if we search
 * for B+ in the input string ABBBA, we'll get 3 hits of length 1, 2 hits of length 2,
 * and 1 hit of length 3. In the future, this should be made configurable (to specifically
 * support greedy matching, etc.)
 */
class SpansRepetition extends BLSpans {
	private SpansInBuckets source;

	private int currentDoc = -1;

	boolean sourceNexted = false;

	boolean more = true;

	private int min;

	private int max;

	private int firstToken;

	private int tokenLength;

	private BLSpans spansSource;

	public SpansRepetition(Spans source, int min, int max) {
		// Find all consecutive matches in this Spans
		spansSource = BLSpansWrapper.optWrapSortUniq(source);
		this.source = new SpansInBucketsConsecutive(spansSource);
		this.min = min;
		this.max = max;
		if (max != -1 && min > max)
			throw new RuntimeException("min > max");
		if (min < 1)
			throw new RuntimeException("min < 1");
	}

	/**
	 * @return the Lucene document id of the current hit
	 */
	@Override
	public int doc() {
		return currentDoc;
	}

	/**
	 * @return end position of current hit
	 */
	@Override
	public int end() {
		return source.end(firstToken + tokenLength - 1);
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

		if (sourceNexted) {
			// We have a bucket.

			// Go to the next hit length for this start point.
			tokenLength++;

			// Find the first valid hit in the bucket
			if ((max != -1 && tokenLength > max) || firstToken + tokenLength > source.bucketSize()) {
				// On to the next start point.
				firstToken++;
				tokenLength = min;
			}

			if (firstToken + tokenLength <= source.bucketSize()) {
				// Still a valid rep. hit.
				return true;
			}

			// No valid hits left; on to the next bucket
		}

		// Next bucket
		more = source.next();
		sourceNexted = true;
		if (more) {
			return resetRepeat();
		}
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
		more = source.skipTo(doc);
		sourceNexted = true;
		if (more)
			return resetRepeat();
		return false;
	}

	private boolean resetRepeat() throws IOException {
		while (true) {
			if (source.bucketSize() >= min) {
				// This stretch is large enough to get a repetition hit!
				firstToken = 0;
				tokenLength = min;
				currentDoc = source.doc();
				return true;
			}

			// Not large enough; next bucket
			more = source.next();
			sourceNexted = true;
			if (!more)
				return false;
		}
	}

	/**
	 * @return start of the current hit
	 */
	@Override
	public int start() {
		return source.start(firstToken);
	}

	@Override
	public String toString() {
		return "SpansRepetition(" + source + ", " + min + ", " + max + ")";
	}

	@Override
	public boolean hitsEndPointSorted() {
		return spansSource.hitsEndPointSorted() && min == max;
	}

	@Override
	public boolean hitsStartPointSorted() {
		return true;
	}

	@Override
	public boolean hitsAllSameLength() {
		return spansSource.hitsAllSameLength() && min == max;
	}

	@Override
	public int hitsLength() {
		return hitsAllSameLength() ? spansSource.hitsLength() * min : -1;
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return spansSource.hitsHaveUniqueStart() && min == max;
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return spansSource.hitsHaveUniqueEnd() && min == max;
	}

	@Override
	public boolean hitsAreUnique() {
		return true;
	}

	@Override
	public void setHitQueryContext(HitQueryContext context) {
		source.setHitQueryContext(context);
	}

	@Override
	public void getCapturedGroups(Span[] capturedGroups) {
		int index = firstToken + tokenLength - 1; // use the last match for captured groups
		source.getCapturedGroups(index, capturedGroups);
	}


}
