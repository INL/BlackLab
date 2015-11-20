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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.search.spans.TermSpans;

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;

/**
 * Gather hits from a Spans object in "buckets" by the start point of the hits. Allow us to retrieve
 * all hits that start at a certain point.
 *
 * The reason we don't use SpansInBucketsAbstract here is that it's more efficient to just save the
 * endpoints for the current start point (the source spans is normally startpoint-sorted already).
 */
class SpansInBucketsPerStartPoint extends DocIdSetIterator implements SpansInBuckets {
	protected Spans source;

	protected int currentDoc = -1;

	protected int currentBucketStart = -1;

	protected int currentSpansStart = -1;

	private List<Integer> endPoints = new ArrayList<Integer>();

	private List<Span[]> capturedGroupsPerEndpoint = new ArrayList<Span[]>();

	private int bucketSize = 0;

	private HitQueryContext hitQueryContext;

	/** Do we have a hitQueryContext and does it contain captured groups? */
	private boolean doCapturedGroups = true;

	/** Does our clause capture any groups? If not, we don't need to mess with those */
	protected boolean clauseCapturesGroups = true;

	public SpansInBucketsPerStartPoint(Spans source) {
		this.source = source;
	}

	@Override
	public int docID() {
		return currentDoc;
	}

	@Override
	public int nextDoc() throws IOException {
		if (currentDoc != NO_MORE_DOCS) {
			currentDoc = source.nextDoc();
			currentSpansStart = source.nextStartPosition();
		}
		return currentDoc;
	}

	@Override
	public int nextBucket() throws IOException {
		if (currentDoc < 0) {
			// Not nexted yet, no bucket
			return -1;
		}
		if (currentSpansStart == Spans.NO_MORE_POSITIONS)
			return NO_MORE_BUCKETS;

		return gatherEndPointsAtStartPoint();
	}

	protected int gatherEndPointsAtStartPoint() throws IOException {
		// NOTE: we don't call clear() to avoid holding on to a lot of memory indefinitely
		endPoints = new ArrayList<Integer>();
		capturedGroupsPerEndpoint = new ArrayList<Span[]>();

		doCapturedGroups = clauseCapturesGroups && source instanceof BLSpans && hitQueryContext != null && hitQueryContext.numberOfCapturedGroups() > 0;

		bucketSize = 0;
		currentBucketStart = currentSpansStart;
		while (currentSpansStart != Spans.NO_MORE_POSITIONS && currentSpansStart == currentBucketStart) {
			endPoints.add(source.endPosition());
			if (doCapturedGroups) {
				Span[] capturedGroups = new Span[hitQueryContext.numberOfCapturedGroups()];
				((BLSpans)source).getCapturedGroups(capturedGroups);
				capturedGroupsPerEndpoint.add(capturedGroups);
			}
			bucketSize++;
			currentSpansStart = source.nextStartPosition();
		}
		return currentDoc;
	}

	@Override
	public int advance(int target) throws IOException {
		if (currentDoc >= target) {
			return nextDoc();
		}

		if (currentDoc == NO_MORE_DOCS)
			return DocIdSetIterator.NO_MORE_DOCS;

		if (currentDoc < target) {
			currentDoc = source.advance(target);
			currentSpansStart = source.nextStartPosition();
		}

		return currentDoc;
	}

	@Override
	public String toString() {
		return source.toString();
	}

	@Override
	public int bucketSize() {
		return bucketSize;
	}

	@Override
	public int startPosition(int indexInBucket) {
		return currentBucketStart;
	}

	@Override
	public int endPosition(int indexInBucket) {
		return endPoints.get(indexInBucket);
	}

	@Override
	public Hit getHit(int indexInBucket) {
		return new Hit(docID(), startPosition(indexInBucket), endPosition(indexInBucket));
	}

	@Override
	public void setHitQueryContext(HitQueryContext context) {
		this.hitQueryContext = context;
		int before = context.getCaptureRegisterNumber();
		if (source instanceof BLSpans)
			((BLSpans) source).setHitQueryContext(context);
		else if (!(source instanceof TermSpans)) // TermSpans is ok because it is a leaf in the tree
			System.err.println("### SpansInBucketsAbstract: " + source + ", not a BLSpans ###");
		if (context.getCaptureRegisterNumber() == before) {
			// Our clause doesn't capture any groups; optimize
			clauseCapturesGroups = false;
		}
	}

	@Override
	public void getCapturedGroups(int indexInBucket, Span[] capturedGroups) {
		if (!doCapturedGroups || capturedGroupsPerEndpoint.size() == 0)
			return;
		Span[] previouslyCapturedGroups = capturedGroupsPerEndpoint.get(indexInBucket);
		if (previouslyCapturedGroups != null) {
			for (int i = 0; i < capturedGroups.length; i++) {
				if (previouslyCapturedGroups[i] != null)
					capturedGroups[i] = previouslyCapturedGroups[i];
			}
		}
	}

	@Override
	public long cost() {
		return 300; // (arbitrary value. This is used for scoring, which we don't use yet)
	}

	@Override
	public Collection<byte[]> getPayload(int indexInBucket) {
		// FIXME implement (optional) getPayload() here
		return null;
	}

	@Override
	public boolean isPayloadAvailable(int indexInBucket) {
		// FIXME implement (optional) isPayloadAvailable() here
		return false;
	}
}
