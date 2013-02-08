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
import java.util.List;

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.lucene.SpanQueryPosFilter.Filter;
import nl.inl.blacklab.search.sequences.SpanComparatorStartPoint;
import nl.inl.blacklab.search.sequences.SpansInBucketsPerDocumentSorted;

import org.apache.lucene.search.spans.Spans;

/**
 * Finds hits from a set that contain one or more hits from the second set,
 * or finds hits from a set that are contained by hit(s) from the second set.
 */
class SpansPosFilter extends Spans {
	/** The two sets of hits to combine */
	private Spans producer;

	/** Does the Spans object still point to a valid hit? */
	private boolean stillValidContainers = true;

	private SpansInBucketsPerDocumentSorted filter;

	private boolean stillValidSearch = true;

	/**
	 * What filter operation to use
	 */
	private Filter op;

	/**
	 * Find hits containing other hits, or contained by other hits.
	 *
	 * @param producer the hits we may be interested in
	 * @param filter the hits used to filter the producer hits
	 * @param op filter operation to use
	 */
	public SpansPosFilter(Spans producer, Spans filter, SpanQueryPosFilter.Filter op) {
		this.producer = producer;
		this.op = op;
		stillValidContainers = true;
		this.filter = new SpansInBucketsPerDocumentSorted(filter, new SpanComparatorStartPoint());
	}

	/**
	 * Find hits containing other hits.
	 *
	 * @param containers the containers we may be interested in.
	 * @param filter we only want containers that contain at least on hit from this filter.
	 */
	public SpansPosFilter(Spans containers, Spans filter) {
		this(containers, filter, Filter.CONTAINING);
	}

	/**
	 * @return the Lucene document id of the current hit
	 */
	@Override
	public int doc() {
		return producer.doc();
	}

	/**
	 * @return end position of current hit
	 */
	@Override
	public int end() {
		return producer.end();
	}

	/**
	 * Go to next span.
	 *
	 * @return true if we're at the next span, false if we're done
	 * @throws IOException
	 */
	@Override
	public boolean next() throws IOException {
		// Are we done yet?
		if (!stillValidContainers || !stillValidSearch)
			return false;

		// Advance container
		stillValidContainers = producer.next();
		if (!stillValidContainers)
			return false; // no more containers; we're done.

		// Find first matching container from here
		return synchronize();
	}

	/**
	 * Find a container with search hits in it, starting from the current container.
	 *
	 * @return true if found, false if no such container exists (i.e. we're done)
	 * @throws IOException
	 */
	private boolean synchronize() throws IOException {
		// Find the next "valid" container, if there is one.
		while (true) {
			// Are search and container in the same document?
			if (filter.doc() < producer.doc()) {
				// No, advance search to be in the same document as the container
				stillValidSearch = filter.skipTo(producer.doc());
				if (!stillValidSearch)
					return false; // No more search results, we're done.
			}

			// Are there search results in this document?
			if (filter.doc() == producer.doc()) {
				// Yes. See if the current container contains any of the search results.
				List<Hit> filterHits = filter.getHits();
				switch(op) {
				case CONTAINING:
					// Looking for producer hits with a filter hit inside
					for (Hit filterHit : filterHits) {
						if (filterHit.start >= producer.start() && filterHit.end <= producer.end()) {
							// Yes, this filter hit is contained in the current producer hit.
							return true;
						}
					}
					break;
				case WITHIN:
					// Looking for producer hits contained by a filter hit
					for (Hit filterHit : filterHits) {
						if (filterHit.start <= producer.start() && filterHit.end >= producer.end()) {
							// Yes, this filter hit contains the current producer hit.
							return true;
						}
					}
					break;
				case STARTS_AT:
					// Looking for producer hits with a filter hit inside
					for (Hit filterHit : filterHits) {
						if (filterHit.start == producer.start()) {
							// Yes, this filter hit starts at the current producer hit.
							return true;
						}
					}
					break;
				case ENDS_AT:
					// Looking for producer hits with a filter hit inside
					for (Hit filterHit : filterHits) {
						if (filterHit.end == producer.end()) {
							// Yes, this filter hit ends at the current producer hit.
							return true;
						}
					}
					break;
				default:
					throw new RuntimeException("Unknown filter operation " + op);
				}
			}

			// No search results found in the current container.
			// Advance to the next container.
			stillValidContainers = producer.next();
			if (!stillValidContainers)
				return false; // no more containers; we're done.
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
		// Skip both to doc
		stillValidContainers = producer.skipTo(doc);

		// Find first matching container from here
		return synchronize();
	}

	/**
	 * @return start of current span
	 */
	@Override
	public int start() {
		return producer.start();
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
	public Collection<byte[]> getPayload() {
		return null;
	}

	@Override
	public boolean isPayloadAvailable() {
		return false;
	}

}
