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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.sequences.SpansInBucketsPerDocument;

import org.apache.lucene.search.spans.Spans;

/**
 * Gets spans for a certain XML element.
 */
class SpansTags extends Spans {
	/** The two sets of hits to combine */
	private SpansInBucketsPerDocument[] spans = new SpansInBucketsPerDocument[2];

	/** Do the Spans objects still point to valid hits? */
	private boolean stillValidSpans[] = new boolean[2];

	/** The current hit (index into starts and ends lists) */
	private int currentHit = -1;

	/** Starts of hits in current document */
	private List<Integer> starts = new ArrayList<Integer>();

	/** Ends of hits in current document */
	private List<Integer> ends = new ArrayList<Integer>();

	private static Comparator<Integer> tagLocationComparator;

	static {
		tagLocationComparator = new Comparator<Integer>() {
			@Override
			public int compare(Integer o1, Integer o2) {
				int abs1, abs2;
				abs1 = o1 < 0 ? -o1 : o1;
				abs2 = o2 < 0 ? -o2 : o2;
				if (abs1 == abs2) {
					// Sort end tags after start tags
					return o1 < 0 ? 1 : -1;
				}
				// Sort according to position
				return abs1 - abs2;
			}
		};
	}

	public SpansTags(Spans startTags, Spans endTags) {
		spans[0] = new SpansInBucketsPerDocument(startTags);
		spans[1] = new SpansInBucketsPerDocument(endTags);
		stillValidSpans[1] = true;
		stillValidSpans[0] = true;
	}

	/**
	 * @return the Lucene document id of the current hit
	 */
	@Override
	public int doc() {
		return spans[0].doc();
	}

	/**
	 * @return start of current span
	 */
	@Override
	public int start() {
		return starts.get(currentHit);
	}

	/**
	 * @return end position of current hit
	 */
	@Override
	public int end() {
		return ends.get(currentHit);
	}

	/**
	 * Go to next span.
	 *
	 * @return true if we're at the next span, false if we're done
	 * @throws IOException
	 */
	@Override
	public boolean next() throws IOException {
		// Do we have more hits in the list?
		currentHit++;
		if (currentHit < starts.size()) {
			// Yep, we're at the next hit in the list.
			return true;
		}

		// Hit list in current document exhausted. Are there more documents to do?
		if (!stillValidSpans[0] || !stillValidSpans[1])
			return false;

		// Move to the next document
		stillValidSpans[0] = spans[0].next();
		stillValidSpans[1] = spans[1].next();

		if (!stillValidSpans[0] || !stillValidSpans[1]) {
			if (stillValidSpans[0] != stillValidSpans[1]) {
				throw new RuntimeException(
						"Error, start and end tags not in synch (start and end Spans ended up inside different documents)");
			}
			return false;
		}

		if (spans[0].doc() != spans[1].doc())
			throw new RuntimeException(
					"Error, start and end tags not in synch (start and end Spans ended up inside different documents)");

		gatherHits();
		return true;
	}

	private void gatherHits() {
		// Put the start and end tag positions in one list (ends negative)
		// (Note that we add 1 to the position to avoid the problem of x == -x for x == 0)
		List<Integer> startsAndEnds = new ArrayList<Integer>();
		for (Hit h: spans[0].getHits()) {
			startsAndEnds.add(h.start + 1);
		}
		for (Hit h: spans[1].getHits()) {
			startsAndEnds.add(- (h.start + 1));
		}

		// Sort the list by position (ends after starts)
		// OPT: sort could be prevented by 'zipping' arrays in a single loop
		Collections.sort(startsAndEnds, tagLocationComparator);

		// Go through the list of all tags, keep track of unmatched open tags and finding matches
		List<Integer> unmatchedOpenTagIndices = new ArrayList<Integer>();
		starts.clear();
		ends.clear();
		currentHit = 0; // first hit
		for (Integer tag: startsAndEnds) {
			if (tag > 0) {
				// Open tag. As yet unmatched. Add to starts list, reserve space in ends list, add the index to the stack.
				starts.add(tag - 1); // subtract 1 again (see above)
				ends.add(-1); // "to be filled in later"
				unmatchedOpenTagIndices.add(ends.size() - 1); // index to fill in when end tag found
			} else {
				// Close tag. Match it to the most recently added unmatched open tag.
				int index = unmatchedOpenTagIndices.remove(unmatchedOpenTagIndices.size() - 1);
				ends.set(index, -tag); // No -1 here because end tag points to last token while we need the first token after the tag.
			}
		}
		if (unmatchedOpenTagIndices.size() > 0) {
			throw new RuntimeException("Unmatched open tags left for document");
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
	public boolean skipTo(int doc) throws IOException {
		// Skip both
		stillValidSpans[0] = spans[0].skipTo(doc);
		stillValidSpans[1] = spans[1].skipTo(doc);
		if (!stillValidSpans[0] || !stillValidSpans[1]) {
			if (stillValidSpans[0] != stillValidSpans[1]) {
				throw new RuntimeException(
						"Error, start and end tags not in synch (start and end Spans ended up inside different documents)");
			}
			return false;
		}

		gatherHits();
		return true;
	}

	@Override
	public String toString() {
		return "SpansTags(" + spans[0] + ", " + spans[1] + ")";
	}

	@Override
	public Collection<byte[]> getPayload() {
		return null;
	}

	@Override
	public boolean isPayloadAvailable() {
		return false;
	}

}
