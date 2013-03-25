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
package nl.inl.blacklab.perdocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Searcher;
import nl.inl.util.ReverseComparator;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.spans.SpanQuery;

/**
 * A list of DocResult objects (document-level query results). The list may be sorted by calling
 * DocResults.sort().
 */
public class DocResults implements Iterable<DocResult> {
	/**
	 * (Part of) our document results
	 */
	protected List<DocResult> results = new ArrayList<DocResult>();

	/**
	 * Our searcher object
	 */
	private Searcher searcher;

	/**
	 * Our source hits object
	 */
	private Hits sourceHits;

	/**
	 * Iterator in our source hits object
	 */
	private Iterator<Hit> sourceHitsIterator;

	/**
	 * A partial DocResult, because we stopped iterating through the Hits.
	 * Pick this up when we continue iterating through it.
	 */
	private DocResult partialDocResult;

	public Searcher getSearcher() {
		return searcher;
	}

	public void add(DocResult r) {
		ensureAllResultsRead();
		results.add(r);
	}

	boolean sourceHitsFullyRead() {
		return sourceHits == null || !sourceHitsIterator.hasNext();
	}

	public DocResults(Searcher searcher, Hits hits) {
		this.searcher = searcher;
		try {
			sourceHits = hits;
			sourceHitsIterator = hits.iterator();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public DocResults(Searcher searcher, String field, SpanQuery query) {
		this(searcher, new Hits(searcher, field, query));
	}

	public DocResults(Searcher searcher, Scorer sc) {
		this.searcher = searcher;
		if (sc == null)
			return; // no matches, empty result set
		try {
			IndexReader indexReader = searcher.getIndexReader();
			while (true) {
				int docId;
				try {
					docId = sc.nextDoc();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				if (docId == DocIdSetIterator.NO_MORE_DOCS)
					break;

				Document d = indexReader.document(docId);
				DocResult dr = new DocResult(searcher, null, docId, d, sc.score());
				results.add(dr);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public DocResults(Searcher searcher, Query query) {
		this(searcher, searcher.findDocScores(query));
	}

	public DocResults(Searcher searcher) {
		this.searcher = searcher;
	}

	/**
	 * Get the list of results
	 * @return the list of results
	 * @deprecated Breaks optimizations. Use iterator or subList() instead.
	 */
	@Deprecated
	public List<DocResult> getResults() {
		ensureAllResultsRead();
		return results;
	}

	/**
	 * Sort the results using the given comparator.
	 *
	 * @param comparator
	 *            how to sort the results
	 */
	void sort(Comparator<DocResult> comparator) {
		ensureAllResultsRead();
		Collections.sort(results, comparator);
	}

	public int size() {
		return sourceHitsFullyRead() ? results.size() : sourceHits.numberOfDocs();
	}

	public void sort(DocProperty prop, boolean sortReverse) {
		Comparator<DocResult> comparator = new ComparatorDocProperty(prop);
		if (sortReverse) {
			comparator = new ReverseComparator<DocResult>(comparator);
		}
		sort(comparator);
	}

	/**
	 * Retrieve a sublist of hits.
	 * @param fromIndex first hit to include in the resulting list
	 * @param toIndex first hit not to include in the resulting list
	 * @return the sublist
	 */
	public List<DocResult> subList(int fromIndex, int toIndex) {
		ensureResultsRead(toIndex - 1);
		return results.subList(fromIndex, toIndex);
	}

	/**
	 * If we still have only partially read our Hits object,
	 * read the rest of it and add all the hits.
	 */
	private void ensureAllResultsRead() {
		ensureResultsRead(-1);
	}

	/**
	 * If we still have only partially read our Hits object,
	 * read some more of it and add the hits.
	 *
	 * @param index the number of results we want to ensure have been read, or negative for all results
	 */
	void ensureResultsRead(int index) {
		if (sourceHitsFullyRead())
			return;

		try {
			// Fill list of document results
			int doc = partialDocResult == null ? -1 : partialDocResult.getDocId();
			DocResult dr = partialDocResult;
			partialDocResult = null;

			@SuppressWarnings("resource")
			IndexReader indexReader = searcher == null ? null : searcher.getIndexReader();
			while ( (index < 0 || results.size() <= index) && sourceHitsIterator.hasNext()) {
				Hit hit = sourceHitsIterator.next();
				if (hit.doc != doc) {
					if (dr != null)
						results.add(dr);
					doc = hit.doc;
					dr = new DocResult(searcher, sourceHits.getConcordanceFieldName(), hit.doc,
							indexReader == null ? null : indexReader.document(hit.doc));
					dr.setContextField(sourceHits.getContextFieldPropName()); // make sure we remember what kind of
													// context we have, if any
				}
				dr.addHit(hit);
			}
			// add the final dr instance to the results collection
			if (dr != null) {
				if (sourceHitsIterator.hasNext())
					partialDocResult = dr; // not done, continue from here later
				else
					results.add(dr); // done
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Were all hits retrieved, or did we stop because there were too many?
	 * @return true if all hits were retrieved
	 */
	public boolean tooManyHits() {
		return sourceHits.tooManyHits();
	}

	/**
	 * Return an iterator over these hits.
	 *
	 * @return the iterator
	 */
	@Override
	public Iterator<DocResult> iterator() {
		// Construct a custom iterator that iterates over the hits in the hits
		// list, but can also take into account the Spans object that may not have
		// been fully read. This ensures we don't instantiate Hit objects for all hits
		// if we just want to display the first few.
		return new Iterator<DocResult>() {

			int index = -1;

			@Override
			public boolean hasNext() {
				// Do we still have hits in the hits list?
				ensureResultsRead(index + 1);
				return index + 1 < results.size();
			}

			@Override
			public DocResult next() {
				// Check if there is a next, taking unread hits from Spans into account
				if (hasNext()) {
					index++;
					return results.get(index);
				}
				throw new NoSuchElementException();
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}

		};
	}



}
