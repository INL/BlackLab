/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.sequences;

import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.search.Hit;

import org.apache.lucene.search.spans.Spans;

/**
 * Gather hits from a Spans object in "buckets" by the start point of the hits. Allow us to retrieve
 * all hits that start at a certain point.
 */
class SpansInBucketsPerStartPoint implements SpansInBuckets {
	protected Spans source;

	protected int currentDoc = -1;

	protected int currentStart = -1;

	protected List<Integer> endPoints = new ArrayList<Integer>();

	/**
	 * Does the source Spans have more hits?
	 */
	protected boolean moreInSource = true;

	private List<Hit> hits = null;

	public SpansInBucketsPerStartPoint(Spans source) {
		this.source = source;
	}

	@Override
	public int doc() {
		return currentDoc;
	}

	public List<Integer> endPoints() {
		return endPoints;
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
		endPoints.clear();
		while (moreInSource && source.doc() == currentDoc && source.start() == currentStart) {
			endPoints.add(source.end());
			moreInSource = source.next();
		}
		hits = new AbstractList<Hit>() {
			@Override
			public Hit get(int index) {
				// NOTE: this is inefficient if we retrieve the same hit two or more
				// times. But this shouldn't normally happen.
				return new Hit(currentDoc, currentStart, endPoints.get(index));
			}

			@Override
			public int size() {
				return endPoints.size();
			}
		};
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
	public List<Hit> getHits() {
		return hits;
	}

}
