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

import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.search.Span;

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

	private boolean moreIncludeSpansDocs;
	
	private boolean moreIncludeSpansPos;

	private boolean moreExcludeSpansDocs;

	public SpansAndNot(Spans includeSpans, Spans excludeSpans) {
		this.includeSpans = BLSpansWrapper.optWrapSort(includeSpans);
		this.excludeSpans = BLSpansWrapper.optWrapSort(excludeSpans);
		excludeSpansNexted = false;
		moreIncludeSpansDocs = true;
		moreExcludeSpansDocs = true;
		moreIncludeSpansPos = false;
	}

	/**
	 * @return current document number
	 */
	@Override
	public int docID() {
		return includeSpans.docID();
	}

	/**
	 * @return end of current span
	 */
	@Override
	public int endPosition() {
		return includeSpans.endPosition();
	}

	@Override
	public int nextDoc() throws IOException {
		// This has to be done right away, but we don't want it in the
		// constructor because it may throw an exception
		if (!excludeSpansNexted) {
			moreExcludeSpansDocs = excludeSpans.nextDoc() != NO_MORE_DOCS;
			excludeSpansNexted = true;
		}

		boolean done = false;
		int newDocId = -1;
		do {
			if (moreIncludeSpansDocs && includeSpans.nextDoc() != NO_MORE_DOCS) {
				// Voldoet deze?
				newDocId = includeSpans.docID();
				if (moreExcludeSpansDocs && excludeSpans.docID() < newDocId) {
					moreExcludeSpansDocs = excludeSpans.advance(newDocId) != NO_MORE_DOCS;
				}
			} else {
				// Geen spans meer over!
				return NO_MORE_DOCS;
			}
		} while (!done && moreExcludeSpansDocs && includeSpans.docID() == excludeSpans.docID());
		moreIncludeSpansPos = true;
		return includeSpans.docID();
	}

	/**
	 * Go to next hit.
	 *
	 * @return true if we're on a valid hit, false otherwise
	 * @throws IOException
	 */
	@Override
	public int nextStartPosition() throws IOException {
		if (!moreIncludeSpansPos)
			return NO_MORE_POSITIONS;
		moreIncludeSpansPos = includeSpans.nextStartPosition() != NO_MORE_POSITIONS;
		return includeSpans.startPosition();
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
	public int advance(int doc) throws IOException {
		if (moreIncludeSpansDocs)
			moreIncludeSpansDocs = includeSpans.advance(doc) != NO_MORE_DOCS;
		if (!moreIncludeSpansDocs)
			return NO_MORE_DOCS;

		if (moreExcludeSpansDocs) {
			moreExcludeSpansDocs = excludeSpans.advance(doc) != NO_MORE_DOCS;
			excludeSpansNexted = true;
			if (moreExcludeSpansDocs && includeSpans.docID() == excludeSpans.docID()) {
				return nextDoc();
			}
		}

		moreIncludeSpansPos = true;
		return includeSpans.docID();
	}

	/**
	 * @return start of current span
	 */
	@Override
	public int startPosition() {
		return includeSpans.startPosition();
	}

	@Override
	public String toString() {
		return "AndNotSpans(" + "----" + ", " + includeSpans + ", " + excludeSpans + ")";
	}

	@Override
	public Collection<byte[]> getPayload() throws IOException {
		return includeSpans.getPayload();
	}

	@Override
	public boolean isPayloadAvailable() throws IOException {
		return includeSpans.isPayloadAvailable();
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
