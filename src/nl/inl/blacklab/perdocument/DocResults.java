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
import nl.inl.blacklab.search.grouping.HitPropValueInt;
import nl.inl.util.ReverseComparator;
import nl.inl.util.ThreadPriority.Level;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Collector;
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
	Searcher searcher;

	/**
	 * Our source hits object
	 */
	private Hits sourceHits;

	/**
	 * Iterator in our source hits object
	 */
	private Iterator<Hit> sourceHitsIterator;

	/**
	 * A partial list of hits in a doc, because we stopped iterating through the Hits.
	 * (or null if we don't have partial doc hits)
	 * Pick this up when we continue iterating through it.
	 */
	private List<Hit> partialDocHits = null;

	/** id of the partial doc we've done (because we stopped iterating through the Hits),
	 * or -1 for no partial doc.
	 */
	private int partialDocId = -1;

	public Searcher getSearcher() {
		return searcher;
	}

	/**
	 * @param r
	 * @deprecated use constructor that takes a list of results instead
	 */
	@Deprecated
	public void add(DocResult r) {
		try {
			ensureAllResultsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; don't complete the operation but return
			// and let the caller detect and deal with the interruption.
			return;
		}
		results.add(r);
	}

	boolean sourceHitsFullyRead() {
		if (sourceHits == null)
			return true;
		synchronized(sourceHitsIterator) {
			return !sourceHitsIterator.hasNext();
		}
	}

	/**
	 * Construct per-document results objects from a Hits object
	 * @param searcher search object
	 * @param hits the hits to view per-document
	 * @deprecated use Hits.perDocResults()
	 */
	@Deprecated
	public DocResults(Searcher searcher, Hits hits) {
		this.searcher = searcher;
		try {
			sourceHits = hits;
			sourceHitsIterator = hits.iterator();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 *
	 * @param searcher
	 * @param field
	 * @param query
	 * @deprecated use Hits.perDocResults()
	 */
	@Deprecated
	public DocResults(Searcher searcher, String field, SpanQuery query) {
		this(searcher, new Hits(searcher, field, query));
	}

	/**
	 * Wraps a list of DocResult objects with the DocResults interface.
	 *
	 * NOTE: the list is not copied but referenced!
	 *
	 * Used by DocGroups constructor.
	 *
	 * @param searcher the searcher that generated the results
	 * @param results the list of results
	 */
	DocResults(Searcher searcher, List<DocResult> results) {
		this.searcher = searcher;
		this.results = results;
	}

	/**
	 * Construct DocResults from a Scorer (Lucene document results).
	 *
	 * @param searcher the searcher that generated the results
	 * @param scorer the scorer to read document results from
	 */
	DocResults(Searcher searcher, Scorer scorer) {
		this.searcher = searcher;
		if (scorer == null)
			return; // no matches, empty result set
		try {
			IndexReader indexReader = searcher.getIndexReader();
			while (true) {
				int docId;
				try {
					docId = scorer.nextDoc();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				if (docId == DocIdSetIterator.NO_MORE_DOCS)
					break;

				Document d = indexReader.document(docId);
				DocResult dr = new DocResult(searcher, null, docId, d, scorer.score());
				results.add(dr);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Find documents whose metadata matches the specified query
	 * @param searcher searcher object
	 * @param query metadata query, or null to match all documents
	 * @deprecated use Searcher.queryDocuments(); this constructor will be made package-private eventually
	 */
	@Deprecated
	public DocResults(Searcher searcher, Query query) {

		this.searcher = searcher;

		// FIXME: a better approach is to only read documents we're actually interested in instead of all of them; compare with Hits.
		//    even better: make DocResults abstract and provide two implementations, DocResultsFromHits and DocResultsFromQuery.
//		IndexSearcher indexSearcher = searcher.getIndexSearcher();
//		IndexReader reader = indexSearcher.getIndexReader();
//		Weight weight = indexSearcher.createNormalizedWeight(query);
//		Map<String, Integer> freq = new HashMap<String, Integer>();
//		for (AtomicReaderContext arc: reader.leaves()) {
//			Scorer scorer = weight.scorer(arc, true, false, arc.reader().getLiveDocs());
//			while (scorer.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
//				//ddd
//			}
//		}

		searcher.collectDocuments(query, new Collector() {
			AtomicReaderContext reader = null;

			@Override
			public boolean acceptsDocsOutOfOrder() {
				return true;
			}

			@Override
			public void collect(int docId) throws IOException {
				int globalDocId = docId + reader.docBase;
				results.add(new DocResult(DocResults.this.searcher, null, globalDocId, reader.reader().document(docId)));
			}

			@Override
			public void setNextReader(AtomicReaderContext reader) throws IOException {
				this.reader = reader;
			}

			@Override
			public void setScorer(Scorer scorer) throws IOException {
				// (ignore)
			}
		});

		//this(searcher, searcher.findDocScores(query == null ? new MatchAllDocsQuery(): query));
	}

	DocResults(Searcher searcher) {
		this.searcher = searcher;
	}

	/**
	 * Get the list of results
	 * @return the list of results
	 * @deprecated Breaks optimizations. Use iterator or subList() instead.
	 */
	@Deprecated
	public List<DocResult> getResults() {
		try {
			ensureAllResultsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; don't complete the operation but return
			// the results we have.
			// Let caller detect and deal with interruption.
		}
		return results;
	}

	/**
	 * Sort the results using the given comparator.
	 *
	 * @param comparator
	 *            how to sort the results
	 */
	void sort(Comparator<DocResult> comparator) {
		try {
			ensureAllResultsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; just sort the results we have.
			// Let caller detect and deal with interruption.
		}
		Collections.sort(results, comparator);
	}

	/**
	 * Determines if there are at least a certain number of results
	 *
	 * This may be used if we don't want to process all results (which
	 * may be a lot) but we do need to know something about the size
	 * of the result set (such as for paging).
	 *
	 * @param lowerBound the number we're testing against
	 *
	 * @return true if the size of this set is at least lowerBound, false otherwise.
	 */
	public boolean sizeAtLeast(int lowerBound) {
		try {
			// Try to fetch at least this many hits
			ensureResultsRead(lowerBound);
		} catch (InterruptedException e) {
			// Thread was interrupted; abort operation
			// and let client decide what to do
		}

		return results.size() >= lowerBound;
	}

	/**
	 * Get the number of documents in this results set.
	 *
	 * Note that this returns the number of document results available;
	 * if there were so many hits that not all were retrieved (call
	 * maxHitsRetrieved()), you can find the grand total of documents
	 * by calling totalSize().
	 *
	 * @return the number of documents.
	 */
	public int size() {
		// Make sure we've collected all results and return the size of our result list.
		try {
			ensureAllResultsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; return size of the results we have.
			// Let caller detect and deal with interruption.
		}
		return results.size();
	}

	/**
	 * Get the total number of documents.
	 * This even counts documents that weren't retrieved because the
	 * set of hits was too large.
	 *
	 * @return the total number of documents.
	 */
	public int totalSize() {
		if (sourceHits == null)
			return size(); // no hits, just documents
		return sourceHits.totalNumberOfDocs();
	}

	/**
	 * Sort documents based on a document property.
	 * @param prop the property to sort on
	 * @param sortReverse true iff we want to sort in reverse.
	 */
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
		try {
			ensureResultsRead(toIndex - 1);
		} catch (InterruptedException e) {
			// Thread was interrupted. We may not even have read
			// the first result in the sublist, so just return an empty list.
			return Collections.emptyList();
		}
		return results.subList(fromIndex, toIndex);
	}

	/**
	 * If we still have only partially read our Hits object,
	 * read the rest of it and add all the hits.
	 * @throws InterruptedException
	 */
	private void ensureAllResultsRead() throws InterruptedException {
		ensureResultsRead(-1);
	}

	/**
	 * If we still have only partially read our Hits object,
	 * read some more of it and add the hits.
	 *
	 * @param index the number of results we want to ensure have been read, or negative for all results
	 * @throws InterruptedException
	 */
	synchronized void ensureResultsRead(int index) throws InterruptedException {
		if (sourceHitsFullyRead())
			return;

		try {
			synchronized(sourceHitsIterator) {
				// Fill list of document results
				int doc = partialDocId;
				List<Hit> docHits = partialDocHits;
				partialDocId = -1;
				partialDocHits = null;

				IndexReader indexReader = searcher == null ? null : searcher.getIndexReader();
				//Thread currentThread = Thread.currentThread();
				while ( (index < 0 || results.size() <= index) && sourceHitsIterator.hasNext()) {

					Hit hit = sourceHitsIterator.next();
					if (hit.doc != doc) {
						if (docHits != null) {
							Hits hits = new Hits(searcher, docHits);
							hits.copySettingsFrom(sourceHits); // concordance type, etc.
							addDocResultToList(doc, hits, indexReader);
						}
						doc = hit.doc;
						docHits = new ArrayList<Hit>();
					}
					docHits.add(hit);
				}
				// add the final dr instance to the results collection
				if (docHits != null) {
					if (sourceHitsIterator.hasNext()) {
						partialDocId = doc;
						partialDocHits = docHits; // not done, continue from here later
					} else {
						Hits hits = new Hits(searcher, docHits);
						hits.copySettingsFrom(sourceHits); // concordance type, etc.
						addDocResultToList(doc, hits, indexReader);
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void addDocResultToList(int doc, Hits docHits, IndexReader indexReader) throws IOException {
		DocResult docResult = new DocResult(searcher, sourceHits.getConcordanceFieldName(), doc, indexReader == null ? null : indexReader.document(doc), docHits);
		// Make sure we remember what kind of context we have, if any
		docResult.setContextField(sourceHits.getContextFieldPropName());
		results.add(docResult);
	}

	/**
	 * Were all hits retrieved, or did we stop because there were too many?
	 * @return true if all hits were retrieved
	 * @deprecated renamed to maxHitsRetrieved()
	 */
	@Deprecated
	public boolean tooManyHits() {
		return maxHitsRetrieved();
	}

	/**
	 * Did we stop retrieving hits because we reached the maximum?
	 * @return true if we reached the maximum and stopped retrieving hits
	 */
	public boolean maxHitsRetrieved() {
		if (sourceHits == null)
			return false; // no hits, only docs
		return sourceHits.maxHitsRetrieved();
	}

	/**
	 * Did we stop counting hits because we reached the maximum?
	 * @return true if we reached the maximum and stopped counting hits
	 */
	public boolean maxHitsCounted() {
		if (sourceHits == null)
			return false; // no hits, only docs
		return sourceHits.maxHitsCounted();
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
				try {
					ensureResultsRead(index + 1);
				} catch (InterruptedException e) {
					// Thread was interrupted. Act like we're done.
					// Let caller detect and deal with interruption.
					return false;
				}
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

	public DocResult get(int i) {
		try {
			ensureResultsRead(i);
		} catch (InterruptedException e) {
			// Thread was interrupted. Required hit hasn't been gathered;
			// we will just return null.
		}
		if (i >= results.size())
			return null;
		return results.get(i);
	}

	/**
	 * Group these results by the specified document property
	 * @param docProp the document property to group on (i.e. number of hits in doc, value of metadata field, etc.)
	 * @return the grouped results
	 */
	@SuppressWarnings("deprecation") // DocGroups constructor will be made package private eventually
	public DocGroups groupedBy(DocProperty docProp) {
		return new DocGroups(this, docProp);
	}

	/**
	 * Get a window into the doc results
	 * @param first first document result to include
	 * @param number maximum number of document results to include
	 * @return the window
	 */
	@SuppressWarnings("deprecation") // DocResultsWindow constructor will be made package private eventually
	public DocResultsWindow window(int first, int number) {
		return new DocResultsWindow(this, first, number);
	}

	public Hits getOriginalHits() {
		return sourceHits;
	}

	/**
	 * Count the number of results that have the same value for the specified
	 * property. Basically a grouping operation without storing the results.
	 * Used for e.g. faceted search.
	 *
	 * @param countBy property to count
	 * @return the counts
	 */
	public DocCounts countBy(DocProperty countBy) {
		return new DocCounts(this, countBy);
	}

	/**
	 * Sum a property for all the documents.
	 *
	 * Can be used to calculate the total number of tokens in a subcorpus, for example.
	 * Note that this does retrieve all results, so it may be slow for large sets.
	 * In particular, you should try to call this method only for DocResults created with
	 * Searcher.queryDocuments() (and not ones created with Hits.perDocResults()) to avoid
	 * the overhead of fetching hits.
	 *
	 * @param numProp a numeric property to sum
	 * @return the sum
	 */
	public int intSum(DocProperty numProp) {
		try {
			ensureAllResultsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; just process the results we have.
			// Let caller detect and deal with interruption.
		}
		int sum = 0;
		for (DocResult result: results) {
			sum += ((HitPropValueInt)numProp.get(result)).getValue();
		}
		return sum;
	}

	public void setPriorityLevel(Level level) {
		if (sourceHits != null) {
			sourceHits.setPriorityLevel(level);
		}
	}

	public Level getPriorityLevel() {
		return sourceHits.getPriorityLevel();
	}

	public int countSoFarDocsCounted() {
		return sourceHits == null ? results.size() : sourceHits.countSoFarDocsCounted();
	}

	public int countSoFarDocsRetrieved() {
		return sourceHits == null ? results.size() : sourceHits.countSoFarDocsRetrieved();
	}
}
