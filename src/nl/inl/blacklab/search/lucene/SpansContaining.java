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
import nl.inl.blacklab.search.sequences.SpanComparatorStartPoint;
import nl.inl.blacklab.search.sequences.SpansInBucketsPerDocumentSorted;

import org.apache.lucene.search.spans.Spans;

/**
 * Keeps spans from a set that contain one or more spans from the second set.
 */
class SpansContaining extends Spans {
	/** The two sets of hits to combine */
	private Spans containers;

	/** Does the Spans object still point to a valid hit? */
	private boolean stillValidContainers = true;

	private SpansInBucketsPerDocumentSorted search;

	private boolean stillValidSearch = true;

	public SpansContaining(Spans containers, Spans search) {
		this.containers = containers;
		stillValidContainers = true;
		this.search = new SpansInBucketsPerDocumentSorted(search, new SpanComparatorStartPoint());
	}

	/**
	 * @return the Lucene document id of the current hit
	 */
	@Override
	public int doc() {
		return containers.doc();
	}

	/**
	 * @return end position of current hit
	 */
	@Override
	public int end() {
		return containers.end();
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
		stillValidContainers = containers.next();
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
			if (search.doc() < containers.doc()) {
				// No, advance search to be in the same document as the container
				stillValidSearch = search.skipTo(containers.doc());
				if (!stillValidSearch)
					return false; // No more search results, we're done.
			}

			// Are there search results in this document?
			if (search.doc() == containers.doc()) {
				// Yes. See if the current container contains any of the search results.
				List<Hit> hits = search.getHits();
				for (Hit hit : hits) {
					if (hit.start >= containers.start() && hit.end <= containers.end()) {
						// Yes, this search result is contained in the current container.
						// Therefore, this container is a result we want. Return.
						return true;
					}
				}
			}

			// No search results found in the current container.
			// Advance to the next container.
			stillValidContainers = containers.next();
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
		stillValidContainers = containers.skipTo(doc);

		// Find first matching container from here
		return synchronize();
	}

	/**
	 * @return start of current span
	 */
	@Override
	public int start() {
		return containers.start();
	}

	@Override
	public String toString() {
		return "SpansContaining(" + containers + " containing " + search + ")";
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
