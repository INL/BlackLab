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
import java.util.Comparator;

import org.apache.lucene.search.spans.SpanCollector;
import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Span;

/**
 * Sort the given Spans per document, according to the given comparator.
 */
public class PerDocumentSortedSpans extends BLSpans {

	final static Comparator<Hit> cmpStartPoint = new SpanComparatorStartPoint();

	final static Comparator<Hit> cmpEndPoint = new SpanComparatorEndPoint();

	protected BLSpans source;

	private int curDoc = -1, curStart = -1, curEnd = -1;

	private SpansInBuckets bucketedSpans;

	private boolean eliminateDuplicates;

	private int prevStart, prevEnd;

	private int indexInBucket = -2; // -2 == no bucket yet; -1 == just started a bucket

	public PerDocumentSortedSpans(BLSpans src, Comparator<Hit> comparator, boolean eliminateDuplicates) {
		this.source = src;

		// Wrap a HitsPerDocument and show it to the client as a normal, sequential Spans.
		bucketedSpans = new SpansInBucketsPerDocumentSorted(src, comparator);

		this.eliminateDuplicates = eliminateDuplicates;
	}

	@Override
	public int docID() {
		return curDoc;
	}

	@Override
	public int startPosition() {
		if (indexInBucket < 0)
			return -1;
		if (indexInBucket >= bucketedSpans.bucketSize())
			return NO_MORE_POSITIONS;
		return curStart;
	}

	@Override
	public int endPosition() {
		if (indexInBucket < 0)
			return -1;
		if (indexInBucket >= bucketedSpans.bucketSize())
			return NO_MORE_POSITIONS;
		return curEnd;
	}

	@Override
	public Hit getHit() {
		if (indexInBucket < 0 || indexInBucket >= bucketedSpans.bucketSize())
			return null;
		return bucketedSpans.getHit(indexInBucket);
	}

	@Override
	public int nextDoc() throws IOException {
		curDoc = bucketedSpans.nextDoc();
		indexInBucket = -2;
		curStart = -1;
		curEnd = -1;
		return curDoc;
	}

	@Override
	public int nextStartPosition() throws IOException {
		if (!eliminateDuplicates) {
			// No need to eliminate duplicates
			if (indexInBucket == -2 || indexInBucket >= bucketedSpans.bucketSize() - 1) {
				// Bucket exhausted or no bucket yet; get one
				if (bucketedSpans.nextBucket() == SpansInBuckets.NO_MORE_BUCKETS) {
					indexInBucket = SpansInBuckets.NO_MORE_BUCKETS;
					return NO_MORE_POSITIONS;
				}
				indexInBucket = -1;
			}
			indexInBucket++;
			curStart = bucketedSpans.startPosition(indexInBucket);
			curEnd = bucketedSpans.endPosition(indexInBucket);
		} else {
			// Eliminate any duplicates
			do {
				if (indexInBucket == -2 || indexInBucket >= bucketedSpans.bucketSize() - 1) {
					// Bucket exhausted or no bucket yet; get one
					if (bucketedSpans.nextBucket() == SpansInBuckets.NO_MORE_BUCKETS) {
						indexInBucket = SpansInBuckets.NO_MORE_BUCKETS;
						return NO_MORE_POSITIONS;
					}
					indexInBucket = -1;
				}
				if (indexInBucket >= 0) {
					prevStart = bucketedSpans.startPosition(indexInBucket);
					prevEnd = bucketedSpans.endPosition(indexInBucket);
				} else {
					prevStart = prevEnd = -1;
				}
				indexInBucket++;
				curStart = bucketedSpans.startPosition(indexInBucket);
				curEnd = bucketedSpans.endPosition(indexInBucket);
			} while (prevStart == curStart && prevEnd == curEnd);
		}
		return curStart;
	}

	@Override
	public int advance(int target) throws IOException {
		curDoc = bucketedSpans.advance(target);
		indexInBucket = -2;
		curStart = -1;
		curEnd = -1;
		return curDoc;
	}

	@Override
	public String toString() {
		return bucketedSpans.toString();
	}

	@Override
	public void passHitQueryContextToClauses(HitQueryContext context) {
		bucketedSpans.setHitQueryContext(context);
	}

	@Override
	public void getCapturedGroups(Span[] capturedGroups) {
		if (indexInBucket < 0 || indexInBucket >= bucketedSpans.bucketSize())
			return;
		bucketedSpans.getCapturedGroups(indexInBucket, capturedGroups);
	}

	@Override
	public int width() {
		return 0; // should maybe be bucketedSpans.width(indexInBucket) ? but we don't use .width()
	}

	@Override
	public void collect(SpanCollector collector) throws IOException {
		// BucketedSpans should collect payload as well, but for now, we don't use
		// payload beyond a "simple" SpanQuery like SpanQueryTags.
		// bucketedSpans.collect(collector);
	}

	@Override
	public float positionsCost() {
		return 0; // should maybe be bucketedSpans.positionsCost()
	}
}
