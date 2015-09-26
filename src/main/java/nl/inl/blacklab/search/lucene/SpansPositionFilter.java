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
import java.util.Collection;

import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter.Filter;
import nl.inl.blacklab.search.sequences.SpanComparatorStartPoint;
import nl.inl.blacklab.search.sequences.SpansInBucketsPerDocument;
import nl.inl.blacklab.search.sequences.SpansInBucketsPerDocumentSorted;

/**
 * Finds hits from a set that contain one or more hits from the second set,
 * or finds hits from a set that are contained by hit(s) from the second set.
 */
class SpansPositionFilter extends BLSpans {
	static SpanComparatorStartPoint cmpStartPoint = new SpanComparatorStartPoint();

	/** The spans we're (possibly) looking for */
	private BLSpans producer;

	/** The spans we use to filter the producer spans */
	private SpansInBucketsPerDocument filter;

	/** What doc is the producer in? */
	private int producerDoc = -1;

	/** What doc is the filter in? */
	private int filterDoc = -1;

	/** What start pos is the producer at? */
	private int producerStart = -1;

	/** Which index in the filter bucket did we use? (needed for getting captured groups) */
	private int filterIndex = -1;

	/** What filter operation to use */
	private Filter op;

	/** Are we already at the first match in a new document, before nextStartPosition() has been called?
	 * Necessary because we have to make sure nextDoc()/advance() actually puts us in a document with at 
	 * least one match.  
	 */
	private boolean alreadyAtFirstMatch = false;

	/**
	 * Find hits containing other hits, or contained by other hits.
	 *
	 * @param producer the hits we may be interested in
	 * @param filter the hits used to filter the producer hits
	 * @param op filter operation to use
	 */
	public SpansPositionFilter(Spans producer, Spans filter, SpanQueryPositionFilter.Filter op) {
		this.producer = BLSpansWrapper.optWrapSort(producer);
		this.op = op;
		if (!(filter instanceof BLSpans) || (filter instanceof BLSpans && ((BLSpans)filter).hitsStartPointSorted())) {
			// Already start point sorted; no need to sort buckets again
			this.filter = new SpansInBucketsPerDocument(filter);
		}
		else {
			// Not sorted yet; sort buckets
			this.filter = new SpansInBucketsPerDocumentSorted(filter, cmpStartPoint);
		}
	}

	/**
	 * Find hits containing other hits.
	 *
	 * @param containers the containers we may be interested in.
	 * @param filter we only want containers that contain at least on hit from this filter.
	 */
	public SpansPositionFilter(Spans containers, Spans filter) {
		this(containers, filter, Filter.CONTAINING);
	}

	@Override
	public int docID() {
		return producerDoc;
	}

	@Override
	public int endPosition() {
		if (alreadyAtFirstMatch)
			return -1; // nextStartPosition() hasn't been called yet
		return producer.endPosition();
	}

	@Override
	public int nextDoc() throws IOException {
		alreadyAtFirstMatch = false;
		
		// Are we done yet?
		if (producerDoc == NO_MORE_DOCS || filterDoc == NO_MORE_DOCS)
			return NO_MORE_DOCS;

		// Advance container
		producerDoc = producer.nextDoc();
		producerStart = -1;
		if (producerDoc == NO_MORE_DOCS)
			return NO_MORE_DOCS; // no more containers; we're done.

		// Find first matching producer span from here
		return findDocWithMatch();
	}

	@Override
	public int nextStartPosition() throws IOException {
		if (producerDoc == NO_MORE_DOCS)
			return NO_MORE_POSITIONS;
		
		if (alreadyAtFirstMatch) {
			// We're already at the first match in the doc. Return it.
			alreadyAtFirstMatch = false;
			return producerStart;
		}
		
		// Are we done yet?
		if (producerStart == NO_MORE_POSITIONS)
			return NO_MORE_POSITIONS;

		// Advance container
		producerStart = producer.nextStartPosition();
		if (producerStart == NO_MORE_POSITIONS)
			return NO_MORE_POSITIONS; // no more producer spans; we're done.

		// Find first matching producer span from here
		return synchronizePos();
	}

	/**
	 * Find a producer span (not necessarily in this document) matching with filter, 
	 * starting from the current producer span.
	 *
	 * @return docID if found, NO_MORE_DOCS if no such producer span exists (i.e. we're done)
	 * @throws IOException
	 */
	private int findDocWithMatch() throws IOException {
		// Find the next "valid" container, if there is one.
		while (producerDoc != NO_MORE_DOCS) {
			
			// Are filter and producer in the same document?
			if (filterDoc < producerDoc) {
				// No, advance filter to be in the same document as the producer
				filterDoc = filter.advance(producerDoc);
				if (filterDoc == NO_MORE_DOCS)
					return NO_MORE_DOCS; // No more filter results, we're done.
				filter.nextBucket();
			}

			// Are there search results in this document?
			producerStart = synchronizePos();
			if (producerStart != NO_MORE_POSITIONS) {
				alreadyAtFirstMatch = true;
				return producerDoc;
			}

			// No search results found in the current container.
			// Advance to the next container.
			producerDoc = producer.nextDoc();
			producerStart = -1;
		}
		return producerDoc;
	}

	/**
	 * Find a producer span matching with filter, starting from the current producer span.
	 *
	 * @return start position if found, NO_MORE_POSITIONS if no such container exists (i.e. we're done)
	 * @throws IOException
	 */
	private int synchronizePos() throws IOException {
		// Find the next "valid" producer spans, if there is one.
		while (producerStart != NO_MORE_POSITIONS) {
			// We're at the first unchecked producer spans. Does it match our filter?
			switch(op) {
			case CONTAINING:
				// Looking for producer hits with a filter hit inside
				for (int i = 0; i < filter.bucketSize(); i++) {
					if (filter.startPosition(i) >= producerStart && filter.endPosition(i) <= producer.endPosition()) {
						// Yes, this filter hit is contained in the current producer hit.
						filterIndex = i; // remember for captured groups
						return producerStart;
					}
				}
				break;
			case WITHIN:
				// Looking for producer hits contained by a filter hit
				for (int i = 0; i < filter.bucketSize(); i++) {
					if (filter.startPosition(i) <= producerStart && filter.endPosition(i) >= producer.endPosition()) {
						// Yes, this filter hit contains the current producer hit.
						filterIndex = i; // remember for captured groups
						return producerStart;
					}
				}
				break;
			case STARTS_AT:
				// Looking for producer hits starting at a filter hit
				for (int i = 0; i < filter.bucketSize(); i++) {
					if (filter.startPosition(i) == producerStart) {
						// Yes, this filter hit starts at the current producer hit.
						filterIndex = i; // remember for captured groups
						return producerStart;
					}
				}
				break;
			case ENDS_AT:
				// Looking for producer hits ending at a filter hit
				for (int i = 0; i < filter.bucketSize(); i++) {
					if (filter.endPosition(i) == producer.endPosition()) {
						// Yes, this filter hit ends at the current producer hit.
						filterIndex = i; // remember for captured groups
						return producerStart;
					}
				}
				break;
			default:
				throw new RuntimeException("Unknown filter operation " + op);
			}

			// Didn't match filter; go to the next position.
			producerStart = producer.nextStartPosition();
		}
		return producerStart;
	}

	@Override
	public int advance(int doc) throws IOException {
		alreadyAtFirstMatch = false;
		
		// Skip both to doc
		producerDoc = producer.advance(doc);
		producerStart = -1;
		if (producerDoc == NO_MORE_DOCS)
			return NO_MORE_DOCS;

		// Find first matching producer span from here
		return findDocWithMatch();
	}

	@Override
	public int startPosition() {
		if (alreadyAtFirstMatch)
			return -1; // nextStartPosition() hasn't been called yet
		return producerStart;
	}
	
	@Override
	public Collection<byte[]> getPayload() throws IOException {
		return producer.getPayload();
	}

	@Override
	public boolean isPayloadAvailable() throws IOException {
		return producer.isPayloadAvailable();
	}

	@Override
	public String toString() {
		switch (op) {
		case CONTAINING:
			return "SpansContaining(" + producer + " containing " + filter + ")";
		case WITHIN:
			return "SpansContaining(" + producer + " within " + filter + ")";
		case STARTS_AT:
			return "SpansContaining(" + producer + " starts at " + filter + ")";
		case ENDS_AT:
			return "SpansContaining(" + producer + " ends at " + filter + ")";
		default:
			throw new RuntimeException("Unknown filter operation " + op);
		}
	}

	@Override
	public boolean hitsEndPointSorted() {
		return producer.hitsEndPointSorted();
	}

	@Override
	public boolean hitsStartPointSorted() {
		return true;
	}

	@Override
	public boolean hitsAllSameLength() {
		return producer.hitsAllSameLength();
	}

	@Override
	public int hitsLength() {
		return producer.hitsLength();
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return producer.hitsHaveUniqueStart();
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return producer.hitsHaveUniqueEnd();
	}

	@Override
	public boolean hitsAreUnique() {
		return producer.hitsAreUnique();
	}

	@Override
	public void passHitQueryContextToClauses(HitQueryContext context) {
		producer.setHitQueryContext(context);
		filter.setHitQueryContext(context);
	}

	@Override
	public void getCapturedGroups(Span[] capturedGroups) {
		if (!childClausesCaptureGroups)
			return;
		producer.getCapturedGroups(capturedGroups);
		filter.getCapturedGroups(filterIndex, capturedGroups);
	}

}
