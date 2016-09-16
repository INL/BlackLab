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
import java.util.Comparator;

import nl.inl.blacklab.search.Hit;

/**
 * Wrap a Spans to retrieve hits per document, so we can process all matches in a document
 * efficiently.
 *
 * Hits are sorted by the given comparator.
 */
public class SpansInBucketsPerDocumentSorted extends SpansInBucketsPerDocument {
	private Comparator<Hit> comparator;

	public SpansInBucketsPerDocumentSorted(BLSpans source, Comparator<Hit> comparator) {
		super(source);
		this.comparator = comparator;
	}

	@Override
	protected void gatherHits() throws IOException {
		super.gatherHits();
		if (comparator != null)
			sortHits(comparator);
	}

}