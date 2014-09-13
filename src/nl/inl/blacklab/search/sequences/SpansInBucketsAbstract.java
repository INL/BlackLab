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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;

import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.search.spans.TermSpans;

/**
 * Wrap a Spans to retrieve sequences of certain matches (in "buckets"), so we can process the
 * sequence efficiently.
 *
 * Examples of sequences of hits might be:
 * * all hits in a document
 * * all consecutive hits in a document
 *
 * This way we can retrieve hits and perform some operation on them (like sorting or retrieving some
 * extra information).
 *
 * Note that with this class, "bucketing" is only possible with consecutive hits from the Spans
 * object. If you want other kinds of hit buckets (containing non-consecutive spans), you should
 * just implement the SpansInBuckets interface, not extend SpansInBucketsAbstract.
 *
 * Also, SpansInBuckets assumes all hits in a bucket are from a single document.
 *
 */
abstract class SpansInBucketsAbstract implements SpansInBuckets {
	protected Spans source;

	protected int currentDoc = -1;

	private List<Hit> hits = new ArrayList<Hit>();

	private int bucketSize = 0;

	protected void addHit(int doc, int start, int end) {
		hits.add(new Hit(doc, start, end));
		bucketSize++;
	}

	protected void sortHits(Comparator<Hit> hitComparator) {
		Collections.sort(hits, hitComparator);
	}

	@Override
	public int bucketSize() {
		return bucketSize;
	}

	@Override
	public int start(int index) {
		return hits.get(index).start;
	}

	@Override
	public int end(int index) {
		return hits.get(index).end;
	}

	@Override
	public Hit getHit(int index) {
		return hits.get(index);
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

		// NOTE: we could call .clear() here, but we don't want to hold on to
		// a lot of memory indefinitely after encountering one huge bucket.
		hits = new ArrayList<Hit>();

		bucketSize = 0;
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

	@Override
	public void setHitQueryContext(HitQueryContext context) {
		if (source instanceof BLSpans)
			((BLSpans) source).setHitQueryContext(context);
		else if (!(source instanceof TermSpans)) // TermSpans is ok because it is a leaf in the tree
			System.err.println("### SpansInBucketsAbstract: " + source + ", not a BLSpans ###");
	}

}
