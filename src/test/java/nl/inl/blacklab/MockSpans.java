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
/**
 *
 */
package nl.inl.blacklab;

import java.io.IOException;
import java.util.Collection;

import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;

/**
 * Stub Spans class for testing. Takes arrays and iterates through 'hits'
 * from these arrays.
 */
public class MockSpans extends BLSpans {
	private int[] doc;

	private int[] start;

	private int[] end;
	
	private int currentDoc = -1;

	private int currentHit = -1;

	private boolean alreadyAtFirstMatch = false;

	private boolean singleTokenSpans;

	private boolean sortedSpans;

	private boolean uniqueSpans;

	public MockSpans(int[] doc, int[] start, int[] end) {
		this.doc = doc;
		this.start = start;
		this.end = end;
		
		sortedSpans = singleTokenSpans = uniqueSpans = true;
		int prevDoc = -1, prevStart = -1, prevEnd = -1;
		for (int i = 0; i < doc.length; i++) {
			if (end[i] - start[i] > 1) {
				// Some hits are longer than 1 token
				singleTokenSpans = false;
			}
			if (doc[i] == prevDoc) {
				if (prevStart > start[i] || prevStart == start[i] && prevEnd > end[i]) {
					// Violates sorted rule (sorted by start point, then endpoint)
					sortedSpans = false;
				}
				if (prevStart == start[i] && prevEnd == end[i]) {
					// Duplicate, so not unique
					// (this check only works if the spans is sorted but we take that into account below)
					uniqueSpans = false;
				}
			}
			prevDoc = doc[i];
			prevStart = start[i];
			prevEnd = end[i];
		}
	}

	@Override
	public int docID() {
		return currentDoc;
	}

	@Override
	public int endPosition() {
		if (currentHit < 0 || alreadyAtFirstMatch)
			return -1;
		if (currentDoc == NO_MORE_DOCS || currentHit >= doc.length || doc[currentHit] != currentDoc)
			return NO_MORE_POSITIONS;
		return end[currentHit];
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
	public int nextDoc() {
		if (currentDoc != NO_MORE_DOCS) {
			alreadyAtFirstMatch = false;
			while (currentHit < doc.length && (currentHit == -1 || doc[currentHit] == currentDoc) ) {
				currentHit++;
			}
			if (currentHit >= doc.length) {
				currentDoc = NO_MORE_DOCS;
				return NO_MORE_DOCS;
			}
			alreadyAtFirstMatch = true;
			currentDoc = doc[currentHit];
		}
		return currentDoc;
	}

	@Override
	public int nextStartPosition() {
		if (currentDoc == NO_MORE_DOCS)
			return NO_MORE_POSITIONS;
		if (alreadyAtFirstMatch) {
			alreadyAtFirstMatch = false;
			return startPosition();
		}
		if (currentHit < 0)
			throw new RuntimeException("nextDoc() not called yet!");
		if (currentHit < doc.length && doc[currentHit] == currentDoc) {
			currentHit++;
			return startPosition(); // may return NO_MORE_POSITIONS if we're at the next doc
		}
		return NO_MORE_POSITIONS;
	}

	@Override
	public int advance(int target) throws IOException {
		if (currentDoc != NO_MORE_DOCS) {
			alreadyAtFirstMatch = false;
			do {
				currentDoc = nextDoc();
			} while(currentDoc != NO_MORE_DOCS && currentDoc < target);
		}
		return currentDoc;
	}

	@Override
	public int startPosition() {
		if (currentHit < 0 || alreadyAtFirstMatch)
			return -1;
		if (currentDoc == NO_MORE_DOCS || currentHit >= doc.length || doc[currentHit] != currentDoc)
			return NO_MORE_POSITIONS;
		return start[currentHit];
	}

	@Override
	public boolean hitsEndPointSorted() {
		return singleTokenSpans && sortedSpans;
	}

	@Override
	public boolean hitsStartPointSorted() {
		return sortedSpans;
	}

	@Override
	public boolean hitsAllSameLength() {
		return singleTokenSpans;
	}

	@Override
	public int hitsLength() {
		return singleTokenSpans ? 1 : -1;
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return singleTokenSpans && sortedSpans && uniqueSpans;
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return singleTokenSpans && sortedSpans && uniqueSpans;
	}

	@Override
	public boolean hitsAreUnique() {
		return sortedSpans && uniqueSpans;
	}

	@Override
	public void passHitQueryContextToClauses(HitQueryContext context) {
		// just ignore this here
	}

	@Override
	public void getCapturedGroups(Span[] capturedGroups) {
		// just ignore this here
	}

	public static MockSpans emptySpans() {
		return new MockSpans(new int[0], new int[0], new int[0]);
	}

	public static Spans single(int doc, int start, int end) {
		return new MockSpans(new int[] {doc}, new int[] {start}, new int[] {end});
	}

	public static Spans fromLists(int[] doc, int[] start, int[] end) {
		return new MockSpans(doc, start, end);
	}

}