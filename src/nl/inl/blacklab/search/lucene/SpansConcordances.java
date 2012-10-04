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
import java.util.Iterator;
import java.util.List;

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.sequences.SpansInBucketsPerDocument;

import org.apache.lucene.search.spans.Spans;

/**
 * Generates concordances around hits.
 *
 * Concordances are hits in context (snippets). You can specify the number of words you would like
 * around the hit. You can get the left, hit and right context from the HitConcordance object after
 * calling the result() method.
 */
@Deprecated
public class SpansConcordances extends SpansWithHit {

	// NOT USED ANYMORE, MADE OBSOLETE BY Hits

	protected Searcher searcher;

	protected String fieldName;

	// private Iterator<Group> groupIt;

	private Iterator<Hit> hitIt;

	protected Hit currentHit;

	private boolean useTermVector;

	private SpansInBucketsPerDocument spansInBuckets;

	private boolean more = true;

	public SpansConcordances(Searcher searcher, Spans results, String fieldName) {
		this(searcher, results, fieldName, 5, false);
	}

	public SpansConcordances(Searcher searcher, Spans results, String fieldName, int contextSize) {
		this(searcher, results, fieldName, contextSize, false);
	}

	public SpansConcordances(Searcher searcher, Spans results, String fieldName, int contextSize,
			boolean useTermVector) {
		this.searcher = searcher;
		// groupIt = new ResultsGrouperSequential(results, new GroupCriteria(new
		// HitPropertyDocumentId())).iterator();
		spansInBuckets = new SpansInBucketsPerDocument(results);
		this.fieldName = fieldName;
		this.useTermVector = useTermVector;
	}

	@Override
	public boolean next() {
		if (hitIt == null || !hitIt.hasNext()) {
			if (!nextDocument())
				return false;
		}
		currentHit = hitIt.next();
		return true;
	}

	private boolean nextDocument() {
		if (!more)
			return false;
		try {
			more = spansInBuckets.next();
		} catch (IOException e1) {
			throw new RuntimeException(e1);
		}
		if (!more)
			return false;
		List<Hit> hitList = spansInBuckets.getHits();

		// Gather concordances and initialise iterator
		searcher.retrieveConcordances(fieldName, useTermVector, hitList);
		hitIt = hitList.iterator();

		return true;
	}

	@Override
	public int doc() {
		return currentHit.doc;
	}

	@Override
	public int end() {
		return currentHit.end;
	}

	@Override
	public int start() {
		return currentHit.start;
	}

	@Override
	public Hit getHit() {
		return currentHit;
	}

}
