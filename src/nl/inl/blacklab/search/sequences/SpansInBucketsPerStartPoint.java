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
import java.util.List;

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;

import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.search.spans.TermSpans;

/**
 * Gather hits from a Spans object in "buckets" by the start point of the hits. Allow us to retrieve
 * all hits that start at a certain point.
 */
class SpansInBucketsPerStartPoint implements SpansInBuckets {
	protected Spans source;

	protected int currentDoc = -1;

	protected int currentStart = -1;

	private List<Integer> endPoints = new ArrayList<Integer>();

	private List<Span[]> capturedGroupsPerEndpoint = new ArrayList<Span[]>();

	private int bucketSize = 0;

	/**
	 * Does the source Spans have more hits?
	 */
	protected boolean moreInSource = true;

	private HitQueryContext hitQueryContext;

	/** Do we have a hitQueryContext and does it contain captured groups? */
	private boolean doCapturedGroups = true;

	/** Does our clause capture any groups? If not, we don't need to mess with those */
	protected boolean clauseCapturesGroups = true;

	public SpansInBucketsPerStartPoint(Spans source) {
		this.source = source;
	}

	@Override
	public int doc() {
		return currentDoc;
	}

	/**
	 * Go to the next bucket.
	 *
	 * @return true if we're at the next valid group, false if we're done
	 * @throws IOException
	 */
	@Override
	public boolean next() throws IOException {
		if (!moreInSource)
			return false;

		if (currentDoc < 0)
			moreInSource = source.next(); // first next()
		if (!moreInSource)
			return false;

		gatherEndPointsAtStartPoint();
		return true;
	}

	protected void gatherEndPointsAtStartPoint() throws IOException {
		currentDoc = source.doc();
		currentStart = source.start();

		// NOTE: we don't call clear() to avoid holding on to a lot of memory indefinitely
		endPoints = new ArrayList<Integer>();
		capturedGroupsPerEndpoint = new ArrayList<Span[]>();

		doCapturedGroups = clauseCapturesGroups && source instanceof BLSpans && hitQueryContext != null && hitQueryContext.numberOfCapturedGroups() > 0;

		bucketSize = 0;
		while (moreInSource && source.doc() == currentDoc && source.start() == currentStart) {
			endPoints.add(source.end());
			if (doCapturedGroups) {
				Span[] capturedGroups = new Span[hitQueryContext.numberOfCapturedGroups()];
				((BLSpans)source).getCapturedGroups(capturedGroups);
				capturedGroupsPerEndpoint.add(capturedGroups);
			}
			bucketSize++;
			moreInSource = source.next();
		}
	}

	/**
	 * Skip to specified document id. NOTE: if we're already at the target document, don't advance.
	 * This differs from how Spans.skipTo() is defined, which always advances at least one hit.
	 *
	 * @param target
	 *            document id to skip to
	 * @return true if we're at a valid group, false if we're done
	 * @throws IOException
	 */
	@Override
	public boolean skipTo(int target) throws IOException {
		if (currentDoc >= target)
			return true;

		if (!moreInSource)
			return false;

		if (currentDoc < 0) {
			if (!next())
				return false;
			if (currentDoc >= target)
				return true;
		}

		if (source.doc() < target) {
			moreInSource = source.skipTo(target);
			if (!moreInSource)
				return false;
		}
		gatherEndPointsAtStartPoint();
		return true;
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
	public int start(int indexInBucket) {
		return currentStart;
	}

	@Override
	public int end(int indexInBucket) {
		return endPoints.get(indexInBucket);
	}

	@Override
	public Hit getHit(int indexInBucket) {
		return new Hit(doc(), start(indexInBucket), end(indexInBucket));
	}

	@Override
	public void setHitQueryContext(HitQueryContext context) {
		this.hitQueryContext = context;
		int before = context.numberOfCapturedGroups();
		if (source instanceof BLSpans)
			((BLSpans) source).setHitQueryContext(context);
		else if (!(source instanceof TermSpans)) // TermSpans is ok because it is a leaf in the tree
			System.err.println("### SpansInBucketsAbstract: " + source + ", not a BLSpans ###");
		if (context.numberOfCapturedGroups() == before) {
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
}
