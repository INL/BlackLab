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

import org.apache.lucene.search.spans.SpanCollector;
import org.eclipse.collections.api.iterator.IntIterator;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import nl.inl.blacklab.search.Span;

/**
 * Gets spans for a certain XML element.
 */
class SpansTagsOld extends BLSpans {
	private static final int ARRAYLIST_REALLOC_THRESHOLD = 1000;

	static SpanComparatorStartPoint cmpStartPoint = new SpanComparatorStartPoint();

	/** The two sets of hits to combine */
	private SpansInBucketsPerDocument[] spans = new SpansInBucketsPerDocument[2];

	/** Do the Spans objects still point to valid hits? */
	private int currentDoc[] = new int[2];

	/** The current hit (index into starts and ends lists) */
	private int currentHit = -1;

	/** Starts of hits in current document */
	private IntArrayList starts = new IntArrayList();

	/** Ends of hits in current document */
	private IntArrayList ends = new IntArrayList();

	/**
	 * Construct SpansTagsOld.
	 *
	 * startTags and endTags should be startpoint-sorted,
	 * which they always are by default.
	 *
	 * @param startTags start tags
	 * @param endTags end tags
	 */
	public SpansTagsOld(BLSpans startTags, BLSpans endTags) {
		BLSpans[] origSpans = { startTags, endTags };
		for (int i = 0; i < 2; i++) {
			spans[i] = new SpansInBucketsPerDocument(origSpans[i]);
		}
		currentDoc[0] = currentDoc[1] = -1;
	}

	@Override
	public int docID() {
		return spans[0].docID();
	}

	@Override
	public int startPosition() {
		if (currentHit < 0)
			return -1;
		if (currentHit >= starts.size())
			return NO_MORE_POSITIONS;
		return starts.get(currentHit);
	}

	@Override
	public int endPosition() {
		if (currentHit < 0)
			return -1;
		if (currentHit >= starts.size())
			return NO_MORE_POSITIONS;
		return ends.get(currentHit);
	}

	@Override
	public int nextDoc() throws IOException {
		// Hit list in current document exhausted. Are there more documents to do?
		if (currentDoc[0] == NO_MORE_DOCS || currentDoc[1] == NO_MORE_DOCS)
			return NO_MORE_DOCS;

		// Move to the next document
		currentDoc[0] = spans[0].nextDoc();
		currentDoc[1] = spans[1].nextDoc();
		if (currentDoc[0] != currentDoc[1])
			throw new RuntimeException("Error, start and end tags not in synch");
		if (currentDoc[0] == NO_MORE_DOCS || currentDoc[1] == NO_MORE_DOCS)
			return NO_MORE_DOCS;
		gatherHits();
		return currentDoc[0];
	}

	@Override
	public int nextStartPosition() throws IOException {
		// Do we have more hits in the list?
		currentHit++;
		if (currentHit < starts.size()) {
			// Yep, we're at the next hit in the list.
			return starts.get(currentHit);
		}
		return NO_MORE_POSITIONS;
	}

	private void gatherHits() throws IOException {
		// Put the start and end tag positions in one list (ends negative)
		// (Note that we add 2 to the tag position to avoid the problem of x == -x for x == 0;
		//  below we subtract it again)
		// The list will be sorted by tag position.
		IntArrayList startsAndEnds = new IntArrayList();
		if (spans[0].nextBucket() == SpansInBuckets.NO_MORE_BUCKETS ||
			spans[1].nextBucket() == SpansInBuckets.NO_MORE_BUCKETS) {
			throw new RuntimeException("Both spans must have exactly one bucket per document");
		}
		int startIndex = 0, endIndex = 0;
		boolean startDone = false, endDone = false;
		int startBucketSize = spans[0].bucketSize();
		int endBucketSize = spans[1].bucketSize();
		while(!startDone || !endDone) {
			// Which comes first, the current start tag or end tag?
			int endTagPos = endDone ? -1 : spans[1].startPosition(endIndex);
			boolean addEndTag = false;
			int startTagPos = -1;
			if (startDone)
				addEndTag = true;
			else {
				startTagPos = spans[0].startPosition(startIndex);
				if (!endDone && startTagPos >= endTagPos)
					addEndTag = true;
			}
			if (addEndTag) {
				// Add end tag
				// +2 to avoid 0/-0 problem; -1 because endtag is attached to next token, but this
				// is inconvenient for this process, we want it attached to the previous token now.
				startsAndEnds.add(- (endTagPos + 2 - 1));
				endIndex++;
				if (endIndex >= endBucketSize)
					endDone = true;
			} else {
				// Add start tag
				startsAndEnds.add(startTagPos + 2);
				startIndex++;
				if (startIndex >= startBucketSize)
					startDone = true;
			}
		}

		// Go through the list of all tags, keep track of unmatched open tags and finding matches
		IntArrayList unmatchedOpenTagIndices = new IntArrayList();
		IntArrayList emptyElementIndices = new IntArrayList(); // empty elements between tokens need special attention (see below)

		// Don't always just call .clear() because the application could
		// keep holding on to too much memory after encountering one really
		// large document!
		if (starts.size() < ARRAYLIST_REALLOC_THRESHOLD) {
			// Not a huge amount of memory, so don't reallocate
			starts.clear();
			ends.clear();
		} else {
			// Reallocate in this case to avoid holding on to a lot of memory
			starts = new IntArrayList();
			ends = new IntArrayList();
		}

		currentHit = -1; // before first hit
		IntIterator it = startsAndEnds.intIterator();
		while (it.hasNext()) {
			int tag = it.next();
			if (tag > 0) {
				int startPositionToStore = tag - 2;  // subtract 2 again to get original position (see above)
				if (!emptyElementIndices.isEmpty()) {
					// We have an unmatched close tag, probably an empty element between tokens (see below).
					// Is this the corresponding start tag?
					int index = emptyElementIndices.removeAtIndex(emptyElementIndices.size() - 1);
					if (startPositionToStore == ends.get(index)) {
						// Yes. Fill in start position.
						starts.set(index, startPositionToStore);
					} else {
						throw new RuntimeException("Unmatched close tag");
					}
				} else {
					// Open tag. As yet unmatched. Add to starts list, reserve space in ends list, add the index to the stack.
					starts.add(startPositionToStore);
					ends.add(-1); // "to be filled in later"
					unmatchedOpenTagIndices.add(ends.size() - 1); // index to fill in when end tag found
				}
			} else {
				// Close tag. Are there unmatched open tags?
				int endPositionToStore = -tag - 2 + 1; // subtract 2 and add 1 to let it point to the next token again (see above)
				if (unmatchedOpenTagIndices.isEmpty()) {
					// No. This must be an empty element between two tokens. Because we don't know the
					// relative order of tags between tokens (this information is not in the index),
					// our sort messes this up.
					// Add placeholder at this position and fix it when we find the open tag.
					starts.add(-1); // "to be filled in later"
					ends.add(endPositionToStore);
					emptyElementIndices.add(ends.size() - 1); // index to fill in when end tag found
				} else {
					// Yes. Match it to the most recently added unmatched open tag.
					int index = unmatchedOpenTagIndices.removeAtIndex(unmatchedOpenTagIndices.size() - 1);
					ends.set(index, endPositionToStore);
				}
			}
		}
		if (!unmatchedOpenTagIndices.isEmpty()) {
			throw new RuntimeException("Unmatched open tags left for document");
		}
		if (!emptyElementIndices.isEmpty()) {
			throw new RuntimeException("Unmatched close tag left for document");
		}
	}

	/**
	 * Skip to the specified document (or the first document after it containing hits)
	 *
	 * @param doc
	 *            the doc number to skip to (or past)
	 * @return true if we're still pointing to a valid hit, false if we're done
	 * @throws IOException
	 */
	@Override
	public int advance(int doc) throws IOException {
		// Skip both
		currentDoc[0] = spans[0].advance(doc);
		currentDoc[1] = spans[1].advance(doc);
		if (currentDoc[0] != currentDoc[1])
			throw new RuntimeException("Error, start and end tags not in synch");
		if (currentDoc[0] == NO_MORE_DOCS || currentDoc[1] == NO_MORE_DOCS)
			return NO_MORE_DOCS;
		gatherHits();
		return currentDoc[0];
	}

	@Override
	public String toString() {
		return "SpansTags(" + spans[0] + ", " + spans[1] + ")";
	}

	@Override
	public void passHitQueryContextToClauses(HitQueryContext context) {
		spans[0].setHitQueryContext(context);
		spans[1].setHitQueryContext(context);
	}

	@Override
	public void getCapturedGroups(Span[] capturedGroups) {
		// Cannot capture groups within the tag part of queries
	}

	@Override
	public int width() {
		return 0;
	}

	@Override
	public void collect(SpanCollector collector) throws IOException {
		// nothing to collect
	}

	@Override
	public float positionsCost() {
		return 0;
	}

}
