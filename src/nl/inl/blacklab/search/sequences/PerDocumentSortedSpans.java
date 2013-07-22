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

import org.apache.lucene.search.spans.Spans;

/**
 * Sort the given Spans per document, according to the given comparator.
 */
public class PerDocumentSortedSpans extends Spans {
	private int curDoc = -1, curStart = -1, curEnd = -1;

	private SpansInBuckets bucketedSpans;

	private boolean eliminateDuplicates;

	private int prevStart, prevEnd;

	private int indexInBucket = -2; // -2 == not started yet; -1 == just started a bucket

	public PerDocumentSortedSpans(Spans src, Comparator<Hit> comparator, boolean eliminateDuplicates) {
		// Wrap a HitsPerDocument and show it to the client as a normal, sequential Spans.
		bucketedSpans = new SpansInBucketsPerDocumentSorted(src, comparator);

		this.eliminateDuplicates = eliminateDuplicates;
	}

	public PerDocumentSortedSpans(Spans src, Comparator<Hit> comparator) {
		this(src, comparator, false);
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

}
