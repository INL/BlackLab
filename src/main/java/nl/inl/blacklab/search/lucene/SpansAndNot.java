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
package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Collection;

import nl.inl.blacklab.search.Span;

import org.apache.lucene.search.spans.Spans;

/**
 * "AND NOT"-combination of two Spans objects.
 *
 * Determines new spans, based on two spans-objects: one with documents to include, and one with
 * documents to exclude.
 */
public class SpansAndNot extends BLSpans {
	/** AND part (include documents in this spans unless they're also in the exclude part) */
	private BLSpans includeSpans;

	/** NOT part (exclude documents from this spans) */
	private BLSpans excludeSpans;

	private boolean excludeSpansNexted;

	private boolean moreIncludeSpans;

	private boolean moreExcludeSpans;

	public SpansAndNot(Spans includeSpans, Spans excludeSpans) {
		this.includeSpans = BLSpansWrapper.optWrap(includeSpans);
		this.excludeSpans = BLSpansWrapper.optWrap(excludeSpans);
		excludeSpansNexted = false;
		moreIncludeSpans = true;
		moreExcludeSpans = true;
	}

	/**
	 * @return current document number
	 */
	@Override
	public int doc() {
		return includeSpans.doc();
	}

	/**
	 * @return end of current span
	 */
	@Override
	public int end() {
		return includeSpans.end();
	}

	/**
	 * Go to next hit.
	 *
	 * @return true if we're on a valid hit, false otherwise
	 * @throws IOException
	 */
	@Override
	public boolean next() throws IOException {
		// This has to be done right away, but we don't want it in the
		// constructor because it may throw an exception
		if (!excludeSpansNexted) {
			moreExcludeSpans = excludeSpans.next();
			excludeSpansNexted = true;
		}

		boolean done = false;
		int newDocId = -1;
		do {
			if (moreIncludeSpans && includeSpans.next()) {
				// Voldoet deze?
				newDocId = includeSpans.doc();
				if (moreExcludeSpans && excludeSpans.doc() < newDocId) {
					moreExcludeSpans = excludeSpans.skipTo(newDocId);
				}
			} else {
				// Geen spans meer over!
				done = true;
			}
		} while (!done && moreExcludeSpans && includeSpans.doc() == excludeSpans.doc());
		return !done;
	}

	/**
	 * Go to the specified document, if it has any hits. If not, go to the first document after
	 * that with hits.
	 *
	 * @param doc
	 *            the document number to skip to (or over)
	 * @return true if we're on a valid hit, false otherwise
	 * @throws IOException
	 */
	@Override
	public boolean skipTo(int doc) throws IOException {
		if (moreIncludeSpans)
			moreIncludeSpans = includeSpans.skipTo(doc);
		if (!moreIncludeSpans)
			return false;

		if (moreExcludeSpans) {
			moreExcludeSpans = excludeSpans.skipTo(doc);
			excludeSpansNexted = true;
			if (moreExcludeSpans && includeSpans.doc() == excludeSpans.doc()) {
				return next();
			}
		}

		return true;
	}

	/**
	 * @return start of current span
	 */
	@Override
	public int start() {
		return includeSpans.start();
	}

	@Override
	public String toString() {
		return "AndNotSpans(" + "----" + ", " + includeSpans + ", " + excludeSpans + ")";
	}

	@Override
	public Collection<byte[]> getPayload() {
		return null;
	}

	@Override
	public boolean isPayloadAvailable() {
		return false;
	}

	@Override
	public boolean hitsEndPointSorted() {
		return includeSpans.hitsEndPointSorted();
	}

	@Override
	public boolean hitsStartPointSorted() {
		return includeSpans.hitsStartPointSorted();
	}

	@Override
	public boolean hitsAllSameLength() {
		return includeSpans.hitsAllSameLength();
	}

	@Override
	public int hitsLength() {
		return includeSpans.hitsLength();
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return includeSpans.hitsHaveUniqueStart();
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return includeSpans.hitsHaveUniqueEnd();
	}

	@Override
	public boolean hitsAreUnique() {
		return includeSpans.hitsAreUnique();
	}

	@Override
	public void passHitQueryContextToClauses(HitQueryContext context) {
		includeSpans.setHitQueryContext(context);
	}

	@Override
	public void getCapturedGroups(Span[] capturedGroups) {
		if (!childClausesCaptureGroups)
			return;
		includeSpans.getCapturedGroups(capturedGroups);
	}
}
