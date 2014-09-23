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
import java.util.Collection;
import java.util.Comparator;

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.BLSpansWrapper;
import nl.inl.blacklab.search.lucene.HitQueryContext;

import org.apache.lucene.search.spans.Spans;

/**
 * Sort the given Spans per document, according to the given comparator.
 */
public class PerDocumentSortedSpans extends BLSpans {

	static Comparator<Hit> cmpStartPoint = new SpanComparatorStartPoint();

	static Comparator<Hit> cmpEndPoint = new SpanComparatorEndPoint();

	protected BLSpans source;

	private int curDoc = -1, curStart = -1, curEnd = -1;

	private SpansInBuckets bucketedSpans;

	private boolean eliminateDuplicates;

	private int prevStart, prevEnd;

	private int indexInBucket = -2; // -2 == not started yet; -1 == just started a bucket

	/** Sort hits by end point instead of by start point? */
	private boolean sortByEndPoint;

	public PerDocumentSortedSpans(Spans src, boolean sortByEndPoint, boolean eliminateDuplicates) {
		this.source = BLSpansWrapper.optWrap(src);

		// Wrap a HitsPerDocument and show it to the client as a normal, sequential Spans.
		this.sortByEndPoint = sortByEndPoint;
		Comparator<Hit> comparator = null;
		if (sortByEndPoint) {
			if (!source.hitsEndPointSorted())
				comparator = cmpEndPoint;
		} else {
			if (!source.hitsStartPointSorted())
				comparator = cmpStartPoint;
		}
		bucketedSpans = new SpansInBucketsPerDocumentSorted(src, comparator);

		this.eliminateDuplicates = eliminateDuplicates;
	}

	@Override
	public int doc() {
		return curDoc;
	}

	@Override
	public int start() {
		return curStart;
	}

	@Override
	public int end() {
		return curEnd;
	}

	@Override
	public Hit getHit() {
		return bucketedSpans.getHit(indexInBucket);
	}

	@Override
	public boolean next() throws IOException {
		do {
			if (indexInBucket >= 0) {
				prevStart = bucketedSpans.start(indexInBucket);
				prevEnd = bucketedSpans.end(indexInBucket);
			} else {
				prevStart = prevEnd = -1;
			}
			if (indexInBucket == -2 || indexInBucket == bucketedSpans.bucketSize() - 1) {
				if (!bucketedSpans.next())
					return false;
				prevStart = prevEnd = -1;
				indexInBucket = -1;
			}
			indexInBucket++;
			curDoc = bucketedSpans.doc();
			curStart = bucketedSpans.start(indexInBucket);
			curEnd = bucketedSpans.end(indexInBucket);
		} while (eliminateDuplicates && prevStart == curStart && prevEnd == curEnd);
		return true;
	}

	@Override
	public boolean skipTo(int target) throws IOException {
		int oldDoc = bucketedSpans.doc();
		if (!bucketedSpans.skipTo(target))
			return false;
		if (oldDoc != bucketedSpans.doc())
			prevStart = prevEnd = -1;
		indexInBucket = -1;
		return next();
	}

	@Override
	public Collection<byte[]> getPayload() {
		// not used
		return null;
	}

	@Override
	public boolean isPayloadAvailable() {
		// not used
		return false;
	}

	@Override
	public String toString() {
		return bucketedSpans.toString();
	}

	@Override
	public boolean hitsAllSameLength() {
		return source.hitsAllSameLength();
	}

	@Override
	public int hitsLength() {
		return source.hitsLength();
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return source.hitsHaveUniqueStart();
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return source.hitsHaveUniqueEnd();
	}

	@Override
	public boolean hitsAreUnique() {
		return source.hitsAreUnique() || eliminateDuplicates;
	}

	@Override
	public boolean hitsEndPointSorted() {
		return sortByEndPoint ? true : hitsAllSameLength();
	}

	@Override
	public boolean hitsStartPointSorted() {
		return sortByEndPoint ? hitsAllSameLength() : true;
	}

	@Override
	public void passHitQueryContextToClauses(HitQueryContext context) {
		bucketedSpans.setHitQueryContext(context);
	}

	@Override
	public void getCapturedGroups(Span[] capturedGroups) {
		bucketedSpans.getCapturedGroups(indexInBucket, capturedGroups);
	}
}
