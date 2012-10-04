/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.sequences;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nl.inl.blacklab.search.Hit;

import org.apache.lucene.search.spans.Spans;

/**
 * Wrap a Spans to retrieve sequences of certain matches (in "buckets"), so we can process the
 * sequence efficiently.
 *
 * Examples of sequences of hits might be: - all hits in a document - all consecutive hits in a
 * document
 *
 * This way we can retrieve hits and perform some operation on them (like sorting or retrieving some
 * extra information).
 *
 * Note that with this class, "bucketing" is only possible with consecutive hits from the Spans
 * object. If you want other kinds of hit buckets (containing non-consecutive spans), you should
 * just implement the SpansInBuckets interface, not extend SpansInBucketsAbstract.
 *
 */
abstract class SpansInBucketsAbstract implements SpansInBuckets {
	protected Spans source;

	protected int currentDoc = -1;

	protected List<Hit> hits = new ArrayList<Hit>();

	@Override
	public List<Hit> getHits() {
		return Collections.unmodifiableList(hits);
	}

	/**
	 * Does the source Spans have more hits?
	 */
	protected boolean more = true;

	public SpansInBucketsAbstract(Spans source) {
		this.source = source;
	}

	@Override
	public boolean next() throws IOException {
		if (!more)
			return false;

		if (currentDoc < 0)
			more = source.next(); // first next()
		if (!more)
			return false;

		gatherHitsInternal();

		return true;
	}

	/**
	 * Subclasses should override this to gather the hits they wish to put in the next bucket.
	 *
	 * @throws IOException
	 */
	protected abstract void gatherHits() throws IOException;

	@Override
	public boolean skipTo(int target) throws IOException {
		if (currentDoc >= target) {
			return true;
		}

		if (currentDoc < 0) {
			next();
			if (currentDoc >= target)
				return true;
		}

		if (!more)
			return false;

		if (source.doc() < target) {
			more = source.skipTo(target);
			if (!more)
				return false;
		}

		gatherHitsInternal();

		return true;
	}

	private void gatherHitsInternal() throws IOException {
		currentDoc = source.doc();
		hits.clear();
		gatherHits();
	}

	@Override
	public int doc() {
		return currentDoc;
	}

	@Override
	public String toString() {
		return source.toString();
	}

}
