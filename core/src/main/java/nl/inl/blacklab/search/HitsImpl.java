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
package nl.inl.blacklab.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.SpanWeight.Postings;
import org.apache.lucene.search.spans.Spans;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.tuple.primitive.IntIntPair;
import org.eclipse.collections.impl.factory.primitive.IntIntMaps;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;

import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.highlight.XmlHighlighter;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.util.StringUtil;
import nl.inl.util.ThreadPriority;

/**
 * Represents a list of Hit objects. Also maintains information about the context (concordance)
 * information stored in the Hit objects.
 */
public class HitsImpl extends Hits {

	protected static final Logger logger = LogManager.getLogger(HitsImpl.class);

	/**
	 * The hits.
	 */
	protected List<Hit> hits;

	/**
	 * The captured groups, if we have any.
	 */
	protected Map<Hit, Span[]> capturedGroups;

	/**
	 * The hit contexts.
	 *
	 * There may be multiple contexts for each hit (see contextFieldsPropName).
	 * Each int array starts with three bookkeeping integers, followed by the contexts information.
	 * The bookkeeping integers are:
	 * * 0 = hit start, index of the hit word (and length of the left context), counted from the start the context
	 * * 1 = right start, start of the right context, counted from the start the context
	 * * 2 = context length, length of 1 context. As stated above, there may be multiple contexts.
	 *
	 * The first context therefore starts at index 3.
	 *
	 */
	private int[][] contexts;

	/**
	 * The current context size (number of words around hits we now have).
	 */
	private int currentContextSize;

	/**
	 * The sort order, if we've sorted, or null if not
	 */
	Integer[] sortOrder;

	/**
	 * The KWIC data, if it has been retrieved.
	 *
	 * NOTE: this will always be null if not all the hits have been retrieved.
	 */
	protected Map<Hit, Kwic> kwics;

	/**
	 * The concordances, if they have been retrieved.
	 *
	 * NOTE: when making concordances from the forward index, this will
	 * always be null, because Kwics will be used internally. This is
	 * only used when making concordances from the content store (the old
	 * default).
	 */
	Map<Hit, Concordance> concordances;

	/**
	 * If we have context information, this specifies the property (i.e. word, lemma, pos) the context came from.
	 * Otherwise, it is null.
	 */
	protected List<String> contextFieldsPropName;

	/**
	 * Our SpanQuery.
	 */
	protected BLSpanQuery spanQuery;

	/** The SpanWeight for our SpanQuery, from which we can get the next Spans when the current one's done. */
	private SpanWeight weight;

	/**
	 * The LeafReaderContexts we should query in succession.
	 */
	protected List<LeafReaderContext> atomicReaderContexts;

	/**
	 * What LeafReaderContext we're querying now.
	 */
	protected int atomicReaderContextIndex = -1;

	/**
	 * Term contexts for the terms in the query.
	 */
	private Map<Term, TermContext> termContexts;

	/**
	 * docBase of the segment we're currently in
	 */
	protected int currentDocBase;

	/**
	 * Our Spans object, which may not have been fully read yet.
	 */
	protected BLSpans currentSourceSpans;

	/**
	 * Did we completely read our Spans object?
	 */
	protected boolean sourceSpansFullyRead = true;

	/**
	 * If true, we've stopped retrieving hits because there are more than
	 * the maximum we've set.
	 */
	private boolean maxHitsRetrieved = false;

	/**
	 * If true, we've stopped counting hits because there are more than
	 * the maximum we've set.
	 */
	private boolean maxHitsCounted = false;

	/**
	 * The number of hits we've seen and counted so far. May be more than
	 * the number of hits we've retrieved if that exceeds maxHitsToRetrieve.
	 */
	protected int hitsCounted = 0;

	/**
	 * The number of separate documents we've seen in the hits retrieved.
	 */
	protected int docsRetrieved = 0;

	/**
	 * The number of separate documents we've counted so far (includes non-retrieved hits).
	 */
	protected int docsCounted = 0;

	/**
	 * Document the previous hit was in, so we can count separate documents.
	 */
	protected int previousHitDoc = -1;

	@Override
	public Hits copy() {
		return new HitsImpl(this);
	}

	/**
	 * Construct a Hits object from an existing Hits object.
	 *
	 * The same hits list is reused. Context and sort order are
	 * not copied. All other fields are.
	 *
	 * @param copyFrom the Hits object to copy
	 */
	private HitsImpl(HitsImpl copyFrom) {
		super(copyFrom.searcher);
		try {
			copyFrom.ensureAllHitsRead();
		} catch (InterruptedException e) {
			// (should be detected by the client)
		}
		hits = copyFrom.hits;
		kwics = copyFrom.kwics;
		sourceSpansFullyRead = true;
		hitsCounted = copyFrom.countSoFarHitsCounted();
		docsRetrieved = copyFrom.countSoFarDocsRetrieved();
		docsCounted = copyFrom.countSoFarDocsCounted();
		previousHitDoc = copyFrom.previousHitDoc;

		copySettingsFrom(copyFrom);

		currentContextSize = -1; // context is not copied
		etiquette = new ThreadPriority();
	}

	/**
	 * Make a wrapper Hits object for a list of Hit objects.
	 *
	 * Does not copy the list, but reuses it.
	 *
	 * @param searcher
	 *            the searcher object
	 * @param concordanceFieldName
	 *            field to use by default when finding concordances
	 * @param hits the list of hits to wrap
	 */
	HitsImpl(Searcher searcher, List<Hit> hits) {
		super(searcher);
		this.hits = hits == null ? new ArrayList<Hit>() : hits;
		hitsCounted = this.hits.size();
		currentContextSize = -1;
		int prevDoc = -1;
		docsRetrieved = docsCounted = 0;
		for (Hit h: this.hits) {
			if (h.doc != prevDoc) {
				docsRetrieved++;
				docsCounted++;
				prevDoc = h.doc;
			}
		}
		etiquette = new ThreadPriority();
	}

	/**
	 * Make a wrapper Hits object for a list of Hit objects.
	 *
	 * Does not copy the list, but reuses it.
	 *
	 * @param searcher
	 *            the searcher object
	 * @param concordanceFieldName
	 *            field to use by default when finding concordances
	 * @param hits the list of hits to wrap
	 * @deprecated if you need a different concordance field, set it manually
	 */
	@Deprecated
	HitsImpl(Searcher searcher, String concordanceFieldName, List<Hit> hits) {
		this(searcher, hits);
		settings.setConcordanceField(concordanceFieldName);
	}

	/**
	 * Construct a Hits object from a SpanQuery.
	 *
	 * @param searcher
	 *            the searcher object
	 * @param concordanceFieldPropName
	 *            field to use by default when finding concordances
	 * @param sourceQuery
	 *            the query to execute to get the hits
	 * @throws TooManyClauses if the query is overly broad (expands to too many terms)
	 */
	HitsImpl(Searcher searcher, SpanQuery sourceQuery) throws TooManyClauses {
		this(searcher, (List<Hit>)null);
		try {
			IndexReader reader = searcher.getIndexReader();
			if (!(sourceQuery instanceof BLSpanQuery))
				throw new IllegalArgumentException("Supplied query must be a BLSpanQuery!");
			spanQuery = ((BLSpanQuery)sourceQuery).rewrite(reader);
			termContexts = new HashMap<>();
			Set<Term> terms = new HashSet<>();
			spanQuery = BLSpanQuery.ensureSortedUnique(spanQuery);
			weight = spanQuery.createWeight(searcher.getIndexSearcher(), false);
			weight.extractTerms(terms);
			etiquette = new ThreadPriority();
			for (Term term: terms) {
				try {
					etiquette.behave();
				} catch (InterruptedException e) {
					// Taking too long, break it off.
					// Not a very graceful way to do it... but at least it won't
					// be stuck forever.
					Thread.currentThread().interrupt(); // client can check this
					throw new RuntimeException("Query matches too many terms; aborted.");
				}
				termContexts.put(term, TermContext.build(reader.getContext(), term));
			}

			currentSourceSpans = null;
			atomicReaderContexts = reader == null ? null : reader.leaves();
			atomicReaderContextIndex = -1;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		sourceSpansFullyRead = false;
	}

	/**
	 * Construct a Hits object from a SpanQuery.
	 *
	 * @param searcher
	 *            the searcher object
	 * @param concordanceFieldPropName
	 *            field to use by default when finding concordances
	 * @param sourceQuery
	 *            the query to execute to get the hits
	 * @throws TooManyClauses if the query is overly broad (expands to too many terms)
	 * @deprecated if you need a different concordance field, set it manually
	 */
	@Deprecated
	HitsImpl(Searcher searcher, String concordanceFieldPropName, SpanQuery sourceQuery)
			throws TooManyClauses {
		this(searcher, sourceQuery);
		settings.setConcordanceField(concordanceFieldPropName);
	}

	/**
	 * Construct a Hits object from a Spans.
	 *
	 * If possible, don't use this constructor, use the one that takes
	 * a SpanQuery, as it's more efficient.
	 *
	 * Note that the Spans provided must be start-point sorted and contain unique hits.
	 *
	 * @param searcher
	 *            the searcher object
	 * @param source
	 *            where to retrieve the Hit objects from
	 */
	HitsImpl(Searcher searcher, BLSpans source) {
		this(searcher, (List<Hit>)null);

		currentSourceSpans = source;
		try {
			sourceSpansFullyRead = currentSourceSpans.nextDoc() != DocIdSetIterator.NO_MORE_DOCS;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Construct a Hits object from a Spans.
	 *
	 * If possible, don't use this constructor, use the one that takes
	 * a SpanQuery, as it's more efficient.
	 *
	 * @param searcher
	 *            the searcher object
	 * @param concordanceFieldPropName
	 *            field to use by default when finding concordances
	 * @param source
	 *            where to retrieve the Hit objects from
	 * @deprecated if you need a different concordance field, set it manually
	 */
	@Deprecated
	HitsImpl(Searcher searcher, String concordanceFieldPropName, BLSpans source) {
		this(searcher, source);
		settings.setConcordanceField(concordanceFieldPropName);
	}

	/** Sets the desired context size.
	 * @param contextSize the context size (number of words to fetch around hits)
	 * @deprecated use settings().setContextSize()
	 */
	@Override
	@Deprecated
	public synchronized void setContextSize(int contextSize) {
		settings().setContextSize(contextSize);
	}

	/**
	 * Did we stop retrieving hits because we reached the maximum?
	 * @return true if we reached the maximum and stopped retrieving hits
	 */
	@Override
	public boolean maxHitsRetrieved() {
		return maxHitsRetrieved;
	}

	/**
	 * Did we stop counting hits because we reached the maximum?
	 * @return true if we reached the maximum and stopped counting hits
	 */
	@Override
	public boolean maxHitsCounted() {
		return maxHitsCounted;
	}

	private void ensureAllHitsRead() throws InterruptedException {
		ensureHitsRead(-1);
	}

	/**
	 * Ensure that we have read at least as many hits as specified in the parameter.
	 *
	 * @param number the minimum number of hits that will have been read when this method
	 *   returns (unless there are fewer hits than this); if negative, reads all hits
	 * @throws InterruptedException if the thread was interrupted during this operation
	 */
	void ensureHitsRead(int number) throws InterruptedException {
		if (sourceSpansFullyRead || (number >= 0 && hits.size() >= number))
			return;

		synchronized (this) {
			boolean readAllHits = number < 0;
			try {
				int maxHitsToCount = settings.maxHitsToCount();
				int maxHitsToRetrieve = settings.maxHitsToRetrieve();
				while (readAllHits || hits.size() < number) {

					// Don't hog the CPU, don't take too long
					etiquette.behave();

					// Stop if we're at the maximum number of hits we want to count
					if (maxHitsToCount >= 0 && hitsCounted >= maxHitsToCount) {
						maxHitsCounted = true;
						break;
					}

					// Get the next hit from the spans, moving to the next
					// segment when necessary.
					while (true) {
						while (currentSourceSpans == null) {
							// Exhausted (or not started yet); get next segment spans.

							if (spanQuery == null) {
								// We started from a Spans, not a SpanQuery. We're done now.
								// (only used in deprecated methods or while testing)
								return;
							}

							atomicReaderContextIndex++;
							if (atomicReaderContexts != null && atomicReaderContextIndex >= atomicReaderContexts.size()) {
								sourceSpansFullyRead = true;
								return;
							}
							if (atomicReaderContexts != null) {
								// Get the atomic reader context and get the next Spans from it.
								LeafReaderContext context = atomicReaderContexts.get(atomicReaderContextIndex);
								currentDocBase = context.docBase;
								BLSpans spans = (BLSpans) weight.getSpans(context, Postings.OFFSETS);
								currentSourceSpans = spans; //BLSpansWrapper.optWrapSortUniq(spans);
							} else {
								// TESTING
								currentDocBase = 0;
								if (atomicReaderContextIndex > 0) {
									sourceSpansFullyRead = true;
									return;
								}
								BLSpans spans = (BLSpans) weight.getSpans(null, Postings.OFFSETS);
								currentSourceSpans = spans; //BLSpansWrapper.optWrapSortUniq(spans);
							}

							if (currentSourceSpans != null) {
								// Update the hit query context with our new spans,
								// and notify the spans of the hit query context
								// (TODO: figure out if we need to call setHitQueryContext()
								//    for each segment or not; if it's just about capture groups
								//    registering themselves, we only need that for the first Spans.
								//    But it's probably required for backreferences, etc. anyway,
								//    and there won't be that many segments, so it's probably ok)
								hitQueryContext.setSpans(currentSourceSpans);
								currentSourceSpans.setHitQueryContext(hitQueryContext); // let captured groups register themselves
								if (capturedGroups == null && hitQueryContext.numberOfCapturedGroups() > 0) {
									capturedGroups = new HashMap<>();
								}

								int doc = currentSourceSpans.nextDoc();
								if (doc == DocIdSetIterator.NO_MORE_DOCS)
									currentSourceSpans = null; // no matching docs in this segment, try next
							}
						}

						// Advance to next hit
						int start = currentSourceSpans.nextStartPosition();
						if (start == Spans.NO_MORE_POSITIONS) {
							int doc = currentSourceSpans.nextDoc();
							if (doc != DocIdSetIterator.NO_MORE_DOCS) {
								// Go to first hit in doc
								start = currentSourceSpans.nextStartPosition();
							} else {
								// This one is exhausted; go to the next one.
								currentSourceSpans = null;
							}
						}
						if (currentSourceSpans != null) {
							// We're at the next hit.
							break;
						}
					}

					// Count the hit and add it (unless we've reached the maximum number of hits we
					// want)
					hitsCounted++;
					int hitDoc = currentSourceSpans.docID() + currentDocBase;
					if (hitDoc != previousHitDoc) {
						docsCounted++;
						if (!maxHitsRetrieved)
							docsRetrieved++;
						previousHitDoc = hitDoc;
					}
					maxHitsRetrieved = maxHitsToRetrieve >= 0 && hits.size() >= maxHitsToRetrieve;
					if (!maxHitsRetrieved) {
						Hit hit = currentSourceSpans.getHit();
						Hit offsetHit = new Hit(hit.doc + currentDocBase, hit.start, hit.end);
						if (capturedGroups != null) {
							Span[] groups = new Span[hitQueryContext.numberOfCapturedGroups()];
							hitQueryContext.getCapturedGroups(groups);
							capturedGroups.put(offsetHit, groups);
						}
						hits.add(offsetHit);
					}
				}
			} catch (InterruptedException e) {
				maxHitsRetrieved = maxHitsCounted = true; // we've stopped retrieving/counting
				throw e;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * Sort the list of hits.
	 *
	 * Note that if the thread is interrupted during this, sort may return
	 * without the hits actually being fully read and sorted. We don't want
	 * to add throws declarations to our whole API, so we assume the calling
	 * method will check for thread interruption if the application uses it.
	 *
	 * @param sortProp
	 *            the hit property to sort on
	 * @param reverseSort
	 *            if true, sort in descending order
	 * @param sensitive whether to sort case-sensitively or not
	 * @deprecated use sortedBy()
	 */
	@Override
	@Deprecated
	public synchronized void sort(final HitProperty sortProp, boolean reverseSort, boolean sensitive) {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; don't complete the operation but return
			// and let the caller detect and deal with the interruption.
			Thread.currentThread().interrupt();
			return;
		}

		// Make sure we have a sort order array of sufficient size
		if (sortOrder == null || sortOrder.length < hits.size()) {
			sortOrder = new Integer[hits.size()];
		}
		// Fill the array with the original hit order (0, 1, 2, ...)
		int n = hits.size();
		for (int i = 0; i < n; i++)
			sortOrder[i] = i;

		// If we need context, make sure we have it.
		List<String> requiredContext = sortProp.needsContext();
		if (requiredContext != null)
			findContext(requiredContext);

		// Perform the actual sort.
		Arrays.sort(sortOrder, sortProp);

		if (reverseSort) {
			// Instead of creating a new Comparator that reverses the order of the
			// sort property (which adds an extra layer of indirection to each of the
			// O(n log n) comparisons), just reverse the hits now (which runs
			// in linear time).
			for (int i = 0; i < n / 2; i++) {
				sortOrder[i] = sortOrder[n - i - 1];
			}
		}
	}

	/**
	 * Determines if there are at least a certain number of hits
	 *
	 * This may be used if we don't want to process all hits (which
	 * may be a lot) but we do need to know something about the size
	 * of the result set (such as for paging).
	 *
	 * Note that this method applies to the hits retrieved, which may
	 * be less than the total number of hits (depending on maxHitsToRetrieve).
	 *
	 * @param lowerBound the number we're testing against
	 *
	 * @return true if the size of this set is at least lowerBound, false otherwise.
	 */
	@Override
	public boolean sizeAtLeast(int lowerBound) {
		try {
			// Try to fetch at least this many hits
			ensureHitsRead(lowerBound);
		} catch (InterruptedException e) {
			// Thread was interrupted; abort operation
			// and let client decide what to do
			Thread.currentThread().interrupt();
		}

		return hits.size() >= lowerBound;
	}

	/**
	 * Return the number of hits available.
	 *
	 * Note that this method applies to the hits retrieved, which may
	 * be less than the total number of hits (depending on maxHitsToRetrieve).
	 * Use totalSize() to find the total hit count (which may also be limited
	 * depending on maxHitsToCount).
	 *
	 * @return the number of hits available
	 */
	@Override
	public int size() {
		try {
			// Probably not all hits have been seen yet. Collect them all.
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; don't complete the operation but return
			// and let the caller detect and deal with the interruption.
			// Returned value is probably not the correct total number of hits,
			// but will not cause any crashes. The thread was interrupted anyway,
			// the value should never be presented to the user.
			maxHitsCounted = true; // indicate that we've stopped counting
			Thread.currentThread().interrupt();
		}
		return hits.size();
	}

	/**
	 * Return the total number of hits.
	 *
	 * NOTE: Depending on maxHitsToRetrieve, hit retrieval may stop
	 * before all hits are seen. We do keep counting hits though
	 * (until we reach maxHitsToCount, or that value is negative).
	 * This method returns our total hit count. Some of these hits
	 * may not be available.
	 *
	 * @return the total hit count
	 */
	@Override
	public int totalSize() {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; don't complete the operation but return
			// and let the caller detect and deal with the interruption.
			// Returned value is probably not the correct total number of hits,
			// but will not cause any crashes. The thread was interrupted anyway,
			// the value should never be presented to the user.
			Thread.currentThread().interrupt();
		}
		return hitsCounted;
	}

	/**
	 * Return the number of documents in the hits we've retrieved.
	 *
	 * @return the number of documents.
	 */
	@Override
	public int numberOfDocs() {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; don't complete the operation but return
			// and let the caller detect and deal with the interruption.
			// Returned value is probably not the correct total number of hits,
			// but will not cause any crashes. The thread was interrupted anyway,
			// the value should never be presented to the user.
			Thread.currentThread().interrupt();
		}
		return docsRetrieved;
	}

	/**
	 * Return the total number of documents in all hits.
	 * This counts documents even in hits that are not stored, only counted.
	 *
	 * @return the total number of documents.
	 */
	@Override
	public int totalNumberOfDocs() {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; don't complete the operation but return
			// and let the caller detect and deal with the interruption.
			// Returned value is probably not the correct total number of hits,
			// but will not cause any crashes. The thread was interrupted anyway,
			// the value should never be presented to the user.
			Thread.currentThread().interrupt();
		}
		return docsCounted;
	}

	/**
	 * Return the number of hits counted so far.
	 *
	 * If you're retrieving hit in a background thread, call this
	 * method from another thread to get an update of the count so far.
	 *
	 * @return the current total hit count
	 */
	@Override
	public int countSoFarHitsCounted() {
		return hitsCounted;
	}

	/**
	 * Return the number of hits retrieved so far.
	 *
	 * If you're retrieving hits in a background thread, call this
	 * method from another thread to get an update of the count so far.
	 *
	 * @return the current total hit count
	 */
	@Override
	public int countSoFarHitsRetrieved() {
		return hits.size();
	}

	/**
	 * Return the number of documents counted so far.
	 *
	 * If you're retrieving hit in a background thread, call this
	 * method from another thread to get an update of the count so far.
	 *
	 * @return the current total hit count
	 */
	@Override
	public int countSoFarDocsCounted() {
		return docsCounted;
	}

	/**
	 * Return the number of documents retrieved so far.
	 *
	 * If you're retrieving hits in a background thread, call this
	 * method from another thread to get an update of the count so far.
	 *
	 * @return the current total hit count
	 */
	@Override
	public int countSoFarDocsRetrieved() {
		return docsRetrieved;
	}

	/**
	 * Check if we're done retrieving/counting hits.
	 *
	 * If you're retrieving hits in a background thread, call this
	 * method from another thread to check if all hits have been processed.
	 *
	 * @return true iff all hits have been retrieved/counted.
	 */
	@Override
	public boolean doneFetchingHits() {
		return sourceSpansFullyRead || maxHitsCounted;
	}

	/**
	 * Return an iterator over these hits that produces the
	 * hits in their original order.
	 *
	 * @param originalOrder if true, returns hits in original order. If false,
	 *   returns them in sorted order (if any)
	 * @return the iterator
	 */
	@Override
	public Iterator<Hit> getIterator(final boolean originalOrder) {
		// Construct a custom iterator that iterates over the hits in the hits
		// list, but can also take into account the Spans object that may not have
		// been fully read. This ensures we don't instantiate Hit objects for all hits
		// if we just want to display the first few.
		return new Iterator<Hit>() {

			int index = -1;

			@Override
			public boolean hasNext() {
				// Do we still have hits in the hits list?
				try {
					ensureHitsRead(index + 2);
				} catch (InterruptedException e) {
					// Thread was interrupted. Don't finish reading hits and accept possibly wrong
					// answer.
					// Client must detect the interruption and stop the thread.
					Thread.currentThread().interrupt();
				}
				return hits.size() >= index + 2;
			}

			@Override
			public Hit next() {
				// Check if there is a next, taking unread hits from Spans into account
				if (hasNext()) {
					index++;
					return hits.get( (originalOrder || sortOrder == null) ? index : sortOrder[index]);
				}
				throw new NoSuchElementException();
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}

		};
	}

	/**
	 * Return the specified hit number, based on the order they
	 * were originally found (not the sorted order).
	 *
	 * @param i
	 *            index of the desired hit
	 * @return the hit, or null if it's beyond the last hit
	 */
	@Override
	public Hit getByOriginalOrder(int i) {
		try {
			ensureHitsRead(i + 1);
		} catch (InterruptedException e) {
			// Thread was interrupted. Required hit hasn't been gathered;
			// we will just return null.
			Thread.currentThread().interrupt();
		}
		if (i >= hits.size())
			return null;
		return hits.get(i);
	}

	/**
	 * Return the specified hit.
	 *
	 * @param i
	 *            index of the desired hit
	 * @return the hit, or null if it's beyond the last hit
	 */
	@Override
	public Hit get(int i) {
		try {
			ensureHitsRead(i + 1);
		} catch (InterruptedException e) {
			// Thread was interrupted. Required hit hasn't been gathered;
			// we will just return null.
			Thread.currentThread().interrupt();
		}
		if (i >= hits.size())
			return null;
		return hits.get(sortOrder == null ? i : sortOrder[i]);
	}

	/**
	 * Retrieve a single KWIC (KeyWord In Context). Only use if you need a larger
	 * snippet around a single
	 * hit. If you need KWICs for a set of hits, just instantiate a HitsWindow and call
	 * getKwic() on that; it will fetch all KWICs in the window in a batch, which
	 * is more efficient.
	 *
	 * @param fieldName field to use for building the KWIC
	 * @param hit the hit for which we want a KWIC
	 * @param contextSize the desired number of words around the hit
	 * @return the KWIC
	 */
	@Override
	public Kwic getKwic(String fieldName, Hit hit, int contextSize) {
		List<Hit> oneHit = Arrays.asList(hit);
		HitsImpl h = new HitsImpl(searcher, searcher.getMainContentsFieldName(), oneHit);
		h.copySettingsFrom(this); // concordance type, etc.
		Map<Hit, Kwic> oneConc = h.retrieveKwics(contextSize, fieldName);
		return oneConc.get(hit);
	}

	/**
	 * Get a KWIC with a custom context size.
	 *
	 * Don't call this directly for displaying a list of results. In that case,
	 * just instantiate a HitsWindow, call setContextSize() on it to set a
	 * default context size and call getKwic(Hit) for each hit. That's
	 * more efficient if you're dealing with many hits.
	 *
	 * This method is mostly just for getting a larger snippet around
	 * a single hit.
	 *
	 * @param h the hit
	 * @param contextSize the context size for this KWIC
	 *   (only use if you want a different one than the preset preference)
	 * @return KWIC for this hit
	 */
	@Override
	public Kwic getKwic(Hit h, int contextSize) {
		if (contextSize != settings().contextSize()) {
			// Different context size than the default for the whole set;
			// We probably want to show a hit with a larger snippet around it
			// (say, 50 words or so). Don't clobber the context of the other
			// hits, just fetch this snippet separately.
			return getKwic(settings().concordanceField(), h, contextSize);
		}

		// Default context size. Read all hits and find concordances for all of them
		// in batch.
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted. Just go ahead with the hits we did
			// get, so at least we can return a valid concordance object and
			// not break the calling method. It is responsible for checking
			// for thread interruption (only some applications use this at all,
			// so throwing exceptions from all methods is too inconvenient)
			Thread.currentThread().interrupt();
		}
		if (kwics == null) {
			findKwics(); // just try to find the default concordances
		}
		Kwic kwic = kwics.get(h);
		if (kwic == null)
			throw new RuntimeException("KWIC for hit not found: " + h);
		return kwic;
	}

	/**
	 * Retrieve a single concordance. Only use if you need a larger snippet around a single
	 * hit. If you need concordances for a set of hits, just instantiate a HitsWindow and call
	 * getConcordance() on that; it will fetch all concordances in the window in a batch, which
	 * is more efficient.
	 *
	 * @param fieldName field to use for building the concordance
	 * @param hit the hit for which we want a concordance
	 * @param contextSize the desired number of words around the hit
	 * @return the concordance
	 */
	@Override
	public synchronized Concordance getConcordance(String fieldName, Hit hit, int contextSize) {
		List<Hit> oneHit = Arrays.asList(hit);
		HitsImpl h = new HitsImpl(searcher, searcher.getMainContentsFieldName(), oneHit);
		h.copySettingsFrom(this); // concordance type, etc.
		if (settings().concordanceType() == ConcordanceType.FORWARD_INDEX) {
			Map<Hit, Kwic> oneKwic = h.retrieveKwics(contextSize, fieldName);
			return oneKwic.get(hit).toConcordance();
		}
		Map<Hit, Concordance> oneConc = h.retrieveConcordancesFromContentStore(contextSize, fieldName);
		return oneConc.get(hit);
	}

	/**
	 * Get a concordance with a custom context size.
	 *
	 * Don't call this directly for displaying a list of results. In that case,
	 * just instantiate a HitsWindow, call setContextSize() on it to set a
	 * default context size and call getConcordance(Hit) for each hit. That's
	 * more efficient if you're dealing with many hits.
	 *
	 * This method is mostly just for getting a larger snippet around
	 * a single hit.
	 *
	 * @param h the hit
	 * @param contextSize the context size for this concordance
	 *   (only use if you want a different one than the preset preference)
	 * @return concordance for this hit
	 */
	@Override
	public synchronized Concordance getConcordance(Hit h, int contextSize) {
		if (settings().concordanceType() == ConcordanceType.FORWARD_INDEX)
			return getKwic(h, contextSize).toConcordance();

		if (contextSize != settings().contextSize()) {
			// Different context size than the default for the whole set;
			// We probably want to show a hit with a larger snippet around it
			// (say, 50 words or so). Don't clobber the context of the other
			// hits, just fetch this snippet separately.
			return getConcordance(settings().concordanceField(), h, contextSize);
		}

		// Default context size. Read all hits and find concordances for all of them
		// in batch.
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted. Just go ahead with the hits we did
			// get, so at least we can return a valid concordance object and
			// not break the calling method. It is responsible for checking
			// for thread interruption (only some applications use this at all,
			// so throwing exceptions from all methods is too inconvenient)
			Thread.currentThread().interrupt();
		}
		if (concordances == null) {
			findConcordances(); // just try to find the default concordances
		}
		Concordance conc = concordances.get(h);
		if (conc == null)
			throw new RuntimeException("Concordance for hit not found: " + h);
		return conc;
	}

	/**
	 * Retrieve concordances for the hits.
	 *
	 * You shouldn't have to call this manually, as it's automatically called when
	 * you call getConcordance() for the first time.
	 */
	synchronized void findConcordances() {
		if (settings.concordanceType() == ConcordanceType.FORWARD_INDEX) {
			findKwics();
			return;
		}

		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted. Just go ahead with the hits we did
			// get, so at least we'll have valid concordances.
			Thread.currentThread().interrupt();
		}
		// Make sure we don't have the desired concordances already
		if (concordances != null) {
			return;
		}

		// Get the concordances
		concordances = retrieveConcordancesFromContentStore(settings().contextSize(), settings().concordanceField());
	}

	/**
	 * Retrieve KWICs for the hits.
	 *
	 * You shouldn't have to call this manually, as it's automatically called when
	 * you call getKwic() for the first time.
	 *
	 */
	synchronized void findKwics() {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted. Just go ahead with the hits we did
			// get, so at least we'll have valid concordances.
			Thread.currentThread().interrupt();
		}
		// Make sure we don't have the desired concordances already
		if (kwics != null) {
			return;
		}

		// Get the concordances
		kwics = retrieveKwics(settings().contextSize(), settings().concordanceField());
	}

	/**
	 * Retrieve KWICs for a (sub)list of hits.
	 *
	 * KWICs are the hit words 'centered' with a certain number of context words around them.
	 *
	 * The size of the left and right context (in words) may be set using
	 * Searcher.setConcordanceContextSize().
	 *
	 * @param contextSize
	 *            how many words around the hit to retrieve
	 * @param fieldName
	 *            field to use for building KWICs
	 *
	 * @return the KWICs
	 */
	private Map<Hit, Kwic> retrieveKwics(int contextSize, String fieldName) {

		// Group hits per document
		MutableIntObjectMap<List<Hit>> hitsPerDocument = IntObjectMaps.mutable.empty();
		for (Hit key: this) {
			List<Hit> hitsInDoc = hitsPerDocument.get(key.doc);
			if (hitsInDoc == null) {
				hitsInDoc = new ArrayList<>();
				hitsPerDocument.put(key.doc, hitsInDoc);
			}
			hitsInDoc.add(key);
		}

		if (settings().concordanceType() == ConcordanceType.FORWARD_INDEX) {
			// Yes, make 'em from the forward index (faster)
			ForwardIndex forwardIndex = null;
			String concWordFI = settings().concWordProp();
			if (concWordFI != null)
				forwardIndex = searcher.getForwardIndex(ComplexFieldUtil.propertyField(fieldName,
						concWordFI));

			ForwardIndex punctForwardIndex = null;
			String concPunctFI = settings().concPunctProp();
			if (concPunctFI != null)
				punctForwardIndex = searcher.getForwardIndex(ComplexFieldUtil.propertyField(
						fieldName, concPunctFI));

			Map<String, ForwardIndex> attrForwardIndices = new HashMap<>();
			Collection<String> concAttrFI = settings().concAttrProps();
			if (concAttrFI == null) {
				// All other FIs are attributes
				for (String p: searcher.getForwardIndices().keySet()) {
					String[] components = ComplexFieldUtil.getNameComponents(p);
					String propName = components[1];
					if (propName.equals(concWordFI)
							|| propName.equals(concPunctFI))
						continue;
					attrForwardIndices.put(propName, searcher.getForwardIndex(p));
				}
			} else {
				// Specific list of attribute FIs
				for (String p: concAttrFI) {
					attrForwardIndices.put(p,
							searcher.getForwardIndex(ComplexFieldUtil.propertyField(fieldName, p)));
				}
			}

			Map<Hit, Kwic> conc1 = new HashMap<>();
			for (List<Hit> l: hitsPerDocument.values()) {
				HitsImpl hitsInThisDoc = new HitsImpl(searcher, searcher.getMainContentsFieldName(), l);
				hitsInThisDoc.copySettingsFrom(this);
				hitsInThisDoc.makeKwicsSingleDocForwardIndex(forwardIndex, punctForwardIndex,
						attrForwardIndices, contextSize, conc1);
			}
			return conc1;
		}

		throw new UnsupportedOperationException("Concordance type is set to CONTENT_STORE, but you can only make KWICs from the forward index. NOTE: if your index has no 'punct' property, concordance type will default to CONTENT_STORE instead of FORWARD_INDEX.");
	}

	/**
	 * Retrieve context words for the hits.
	 *
	 * @param fieldProps
	 *            the field and properties to use for the context
	 */
	@Override
	public synchronized void findContext(List<String> fieldProps) {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted. Just go ahead with the hits we did
			// get, so at least we can return with valid context.
			Thread.currentThread().interrupt();
		}
		// Make sure we don't have the desired context already
		if (contextFieldsPropName != null && fieldProps.equals(contextFieldsPropName)
				&& settings().contextSize() == currentContextSize) {
			return;
		}

		List<ForwardIndex> fis = new ArrayList<>();
		for (String fieldPropName: fieldProps) {
			fis.add(searcher.getForwardIndex(fieldPropName));
		}

		// Get the context
		// Group hits per document
		List<Hit> hitsInSameDoc = new ArrayList<>();
		int currentDoc = -1;
		int index = 0;
		if (contexts == null || contexts.length < hits.size()) {
			contexts = new int[hits.size()][];
		}
		for (Hit hit: hits) {
			if (hit.doc != currentDoc) {
				if (currentDoc >= 0) {
					try {
						etiquette.behave();
					} catch (InterruptedException e) {
						// Thread was interrupted. Just go ahead with the hits we did
						// get, so at least we can return with valid context.
						Thread.currentThread().interrupt();
					}

					findPartOfContext(hitsInSameDoc, index - hitsInSameDoc.size(), fis);

					// Reset hits list for next doc
					hitsInSameDoc.clear();
				}
				currentDoc = hit.doc; // start a new document
			}
			hitsInSameDoc.add(hit);
			index++;
		}
		if (!hitsInSameDoc.isEmpty())
			findPartOfContext(hitsInSameDoc, index - hitsInSameDoc.size(), fis);

		currentContextSize = settings().contextSize();
		contextFieldsPropName = new ArrayList<>(fieldProps);
	}

	/**
	 * Helper method for findContext(). Finds the hits in a single document and adds
	 * context to our contexts array.
	 *
	 * @param hitsInSameDoc the hits in one document
	 * @param firstHitIndex index of the first hit
	 * @param fis forward indices needed for contexts
	 */
	private void findPartOfContext(List<Hit> hitsInSameDoc, int firstHitIndex, List<ForwardIndex> fis) {
		// Find context for the hits in the current document
		HitsImpl hitsObj = new HitsImpl(searcher, searcher.getMainContentsFieldName(), hitsInSameDoc);
		hitsObj.copySettingsFrom(this);
		hitsObj.getContextWords(settings().contextSize(), fis);

		// Copy the contexts from the temporary Hits object to this one
		for (int i = 0; i < hitsInSameDoc.size(); i++) {
			contexts[firstHitIndex + i] = hitsObj.getHitContext(i);
		}
	}

	/**
	 * Count occurrences of context words around hit.
	 *
	 * @param propName the property to use for the collocations, or null if default
	 * @param ctx query execution context, containing the sensitivity settings
	 *
	 * @return the frequency of each occurring token
	 */
	@Override
	public synchronized TermFrequencyList getCollocations(String propName,
			QueryExecutionContext ctx) {
		if (propName == null)
			propName = searcher.getIndexStructure().getMainContentsField().getMainProperty().getName();
		if (ctx == null)
			ctx = searcher.getDefaultExecutionContext(settings().concordanceField());
		ctx = ctx.withProperty(propName);
		findContext(Arrays.asList(ctx.luceneField(false)));
		MutableIntIntMap coll = IntIntMaps.mutable.empty();
		for (int j = 0; j < hits.size(); j++) {
			int[] context = contexts[j];

			// Count words
			int contextHitStart = context[CONTEXTS_HIT_START_INDEX];
			int contextRightStart = context[CONTEXTS_RIGHT_START_INDEX];
			int contextLength = context[CONTEXTS_LENGTH_INDEX];
			int indexInContent = CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS;
			for (int i = 0; i < contextLength; i++, indexInContent++) {
				if (i >= contextHitStart && i < contextRightStart)
					continue; // don't count words in hit itself, just around [option..?]
				int w = context[indexInContent];
				int n;
				if (!coll.contains(w))
					n = 1;
				else
					n = coll.get(w) + 1;
				coll.put(w, n);
			}
		}

		// Get the actual words from the sort positions
		boolean caseSensitive = searcher.isDefaultSearchCaseSensitive();
		boolean diacSensitive = searcher.isDefaultSearchDiacriticsSensitive();
		TermFrequencyList collocations = new TermFrequencyList(coll.size());
		Terms terms = searcher.getTerms(contextFieldsPropName.get(0));
		Map<String, Integer> wordFreq = new HashMap<>();
		for (IntIntPair e: coll.keyValuesView()) {
			int key = e.getOne();
			int value = e.getTwo();
			String word = terms.get(key);
			if (!diacSensitive) {
				word = StringUtil.removeAccents(word);
			}
			if (!caseSensitive) {
				word = word.toLowerCase();
			}
			// Note that multiple ids may map to the same word (because of sensitivity settings)
			// Here, those groups are merged.
			Integer n = wordFreq.get(word);
			if (n == null) {
				n = 0;
			}
			n += value;
			wordFreq.put(word, n);
		}

		// Transfer from map to list
		for (Map.Entry<String, Integer> e: wordFreq.entrySet()) {
			collocations.add(new TermFrequency(e.getKey(), e.getValue()));
		}
		return collocations;
	}

	@Override
	public boolean hasCapturedGroups() {
		return capturedGroups != null;
	}

	/**
	 * Get the captured group information for this hit, if any.
	 *
	 * The names of the captured groups can be obtained through
	 * the getCapturedGroupNames() method.
	 *
	 * @param hit the hit to get captured group information for
	 * @return the captured group information, or null if none
	 */
	@Override
	public Span[] getCapturedGroups(Hit hit) {
		if (capturedGroups == null)
			return null;
		return capturedGroups.get(hit);
	}

	/**
	 * Get the captured group information in map form.
	 *
	 * Relatively slow; use getCapturedGroups() and getCapturedGroupNames()
	 * for a faster alternative.
	 *
	 * @param hit hit to get the captured group map for
	 * @return the captured group information map
	 */
	@Override
	public Map<String, Span> getCapturedGroupMap(Hit hit) {
		if (capturedGroups == null)
			return null;
		Map<String, Span> result = new TreeMap<>(); // TreeMap to maintain group ordering
		List<String> names = getCapturedGroupNames();
		Span[] groups = capturedGroups.get(hit);
		for (int i = 0; i < names.size(); i++) {
			result.put(names.get(i), groups[i]);
		}
		return result;
	}

	/**
	 * Get the field our current concordances were retrieved from
	 *
	 * @return the field name
	 */
	@Override
	public List<String> getContextFieldPropName() {
		return contextFieldsPropName;
	}

	/**
	 * Set the field properties to retrieve context from
	 * @param contextField the field properties
	 */
	@Override
	public void setContextField(List<String> contextField) {
		this.contextFieldsPropName = contextField == null ? null : new ArrayList<>(
				contextField);
	}

	/**
	 * Retrieves the KWIC information (KeyWord In Context: left, hit and right context) for
	 * a number of hits in the same document from the ContentStore.
	 *
	 * @param forwardIndex
	 *    Forward index for the words
	 * @param punctForwardIndex
	 *    Forward index for the punctuation
	 * @param attrForwardIndices
	 *    Forward indices for the attributes, or null if none
	 * @param wordsAroundHit
	 *            number of words left and right of hit to fetch
	 * @param theKwics
	 *            where to add the KWICs
	 */
	synchronized void makeKwicsSingleDocForwardIndex(ForwardIndex forwardIndex,
			ForwardIndex punctForwardIndex, Map<String, ForwardIndex> attrForwardIndices,
			int wordsAroundHit, Map<Hit, Kwic> theKwics) {
		if (hits.isEmpty())
			return;

		// Save existing context so we can restore it afterwards
		int[][] oldContexts = null;
		if (!hits.isEmpty() && contexts != null)
			oldContexts = saveContexts();

		// TODO: more efficient to get all contexts with one getContextWords() call!

		// Get punctuation context
		int[][] punctContext = null;
		if (punctForwardIndex != null) {
			getContextWords(wordsAroundHit, Arrays.asList(punctForwardIndex));
			punctContext = saveContexts();
		}
		Terms punctTerms = punctForwardIndex == null ? null : punctForwardIndex.getTerms();

		// Get attributes context
		String[] attrName = null;
		Terms[] attrTerms = null;
		int[][][] attrContext = null;
		if (attrForwardIndices != null) {
			int n = attrForwardIndices.size();
			attrName = new String[n];
			ForwardIndex[] attrFI = new ForwardIndex[n];
			attrTerms = new Terms[n];
			attrContext = new int[n][][];
			int i = 0;
			for (Map.Entry<String, ForwardIndex> e: attrForwardIndices.entrySet()) {
				attrName[i] = e.getKey();
				attrFI[i] = e.getValue();
				attrTerms[i] = attrFI[i].getTerms();
				getContextWords(wordsAroundHit, Arrays.asList(attrFI[i]));
				attrContext[i] = saveContexts();
				i++;
			}
		}

		// Get word context
		if (forwardIndex != null)
			getContextWords(wordsAroundHit, Arrays.asList(forwardIndex));
		Terms terms = forwardIndex == null ? null : forwardIndex.getTerms();

		// Make the concordances from the context
		String concPunctFI = settings().concPunctProp();
		String concWordFI = settings().concWordProp();
		for (int i = 0; i < hits.size(); i++) {
			Hit h = hits.get(i);
			List<String> tokens = new ArrayList<>();
			int[] context = contexts[i];
			int contextLength = context[CONTEXTS_LENGTH_INDEX];
			int contextRightStart = context[CONTEXTS_RIGHT_START_INDEX];
			int contextHitStart = context[CONTEXTS_HIT_START_INDEX];
			int indexInContext = CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS;
			for (int j = 0; j < contextLength; j++, indexInContext++) {

				// Add punctuation before word
				// (Applications may choose to ignore punctuation before the first word)
				if (punctTerms == null) {
					// There is no punctuation forward index. Just put a space
					// between every word.
					tokens.add(" ");
				} else
					tokens.add(punctTerms.get(punctContext[i][indexInContext]));

				// Add extra attributes (e.g. lemma, pos)
				if (attrContext != null) {
					for (int k = 0; k < attrContext.length; k++) {
						tokens.add(attrTerms[k].get(attrContext[k][i][indexInContext]));
					}
				}

				// Add word
				if (terms != null)
					tokens.add(terms.get(context[indexInContext]));
				else
					tokens.add(""); // weird, but make sure the numbers add up at the end

			}
			List<String> properties = new ArrayList<>();
			properties.add(concPunctFI);
			for (int k = 0; k < attrContext.length; k++) {
				properties.add(attrName[k]);
			}
			properties.add(concWordFI);
			Kwic kwic = new Kwic(properties, tokens, contextHitStart, contextRightStart);
			theKwics.put(h, kwic);
		}

		if (oldContexts != null) {
			restoreContexts(oldContexts);
		}
	}

	/**
	 * Get context words from the forward index.
	 *
	 * NOTE: not synchronized because only ever called from synchronized methods!
	 *
	 * @param wordsAroundHit how many words of context we want
	 * @param contextSources
	 *            forward indices to get context from
	 */
	private void getContextWords(int wordsAroundHit, List<ForwardIndex> contextSources) {

		int n = hits.size();
		if (n == 0)
			return;
		int[] startsOfSnippets = new int[n];
		int[] endsOfSnippets = new int[n];
		int i = 0;
		for (Hit h: hits) {
			startsOfSnippets[i] = wordsAroundHit >= h.start ? 0 : h.start - wordsAroundHit;
			endsOfSnippets[i] = h.end + wordsAroundHit;
			i++;
		}

		int fiNumber = 0;
		int doc = hits.get(0).doc;
		for (ForwardIndex forwardIndex: contextSources) {
			// Get all the words from the forward index
			List<int[]> words;
			if (forwardIndex != null) {
				// We have a forward index for this field. Use it.
				int fiid = forwardIndex.luceneDocIdToFiid(doc);
				words = forwardIndex.retrievePartsInt(fiid, startsOfSnippets, endsOfSnippets);
			} else {
				throw new RuntimeException("Cannot get context without a forward index");
			}

			// Build the actual concordances
			Iterator<int[]> wordsIt = words.iterator();
			int hitNum = 0;
			if (contexts == null || contexts.length < hits.size()) {
				contexts = new int[hits.size()][];
			}
			for (Hit hit: hits) {
				int[] theseWords = wordsIt.next();

				// Put the concordance in the Hit object
				int firstWordIndex = startsOfSnippets[hitNum];

				if (fiNumber == 0) {
					// Allocate context array and set hit and right start and context length
					contexts[hitNum] = new int[CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS + theseWords.length * contextSources.size()];
					contexts[hitNum][CONTEXTS_HIT_START_INDEX] = hit.start - firstWordIndex;
					contexts[hitNum][CONTEXTS_RIGHT_START_INDEX] = hit.end - firstWordIndex;
					contexts[hitNum][CONTEXTS_LENGTH_INDEX] = theseWords.length;
				}
				// Copy the context we just retrieved into the context array
				int start = fiNumber * theseWords.length + CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS;
				System.arraycopy(theseWords, 0, contexts[hitNum], start, theseWords.length);
				hitNum++;
			}

			fiNumber++;
		}
	}

	/**
	 * Get the context information from the list of hits, so we can
	 * look up a different context but still have access to this one as well.
	 *
	 * NOTE: not synchronized because only ever called from synchronized methods!
	 *
	 * @return the context
	 */
	private int[][] saveContexts() {
		int[][] saved = new int[contexts.length][];
		for (int i = 0; i < contexts.length; i++) {
			saved[i] = Arrays.copyOf(contexts[i], contexts[i].length);
		}
		return saved;
	}

	/**
	 * Put context information into the list of hits.
	 *
	 * NOTE: not synchronized because only ever called from synchronized methods!
	 *
	 * @param saved the context to restore
	 */
	private void restoreContexts(int[][] saved) {
		if (contexts == null || contexts.length != saved.length) {
			contexts = new int[saved.length][];
		}
		for (int i = 0; i < saved.length; i++) {
			if (contexts[i] == null || contexts[i].length != saved[i].length) {
				contexts[i] = new int[saved[i].length];
			}
			System.arraycopy(saved[i], 0, contexts, 0, saved[i].length);
		}
	}

	/**
	 * Retrieves the concordance information (left, hit and right context) for a number of hits in
	 * the same document from the ContentStore.
	 *
	 * NOTE1: it is assumed that all hits in this Hits object are in the same document!
	 * @param fieldName
	 *            Lucene index field to make conc for
	 * @param wordsAroundHit
	 *            number of words left and right of hit to fetch
	 * @param conc
	 *            where to add the concordances
	 * @param hl
	 */
	private synchronized void makeConcordancesSingleDocContentStore(String fieldName, int wordsAroundHit, Map<Hit, Concordance> conc,
			XmlHighlighter hl) {
		if (hits.isEmpty())
			return;
		int doc = hits.get(0).doc;
		int arrayLength = hits.size() * 2;
		int[] startsOfWords = new int[arrayLength];
		int[] endsOfWords = new int[arrayLength];

		// Determine the first and last word of the concordance, as well as the
		// first and last word of the actual hit inside the concordance.
		int startEndArrayIndex = 0;
		for (Hit hit: hits) {
			int hitStart = hit.start;
			int hitEnd = hit.end - 1;

			int start = hitStart - wordsAroundHit;
			if (start < 0)
				start = 0;
			int end = hitEnd + wordsAroundHit;

			startsOfWords[startEndArrayIndex] = start;
			startsOfWords[startEndArrayIndex + 1] = hitStart;
			endsOfWords[startEndArrayIndex] = hitEnd;
			endsOfWords[startEndArrayIndex + 1] = end;

			startEndArrayIndex += 2;
		}

		// Get the relevant character offsets (overwrites the startsOfWords and endsOfWords
		// arrays)
		searcher.getCharacterOffsets(doc, fieldName, startsOfWords, endsOfWords, true);

		// Make all the concordances
		List<Concordance> newConcs = searcher.makeConcordancesFromContentStore(doc, fieldName, startsOfWords,
				endsOfWords, hl);
		for (int i = 0; i < hits.size(); i++) {
			conc.put(hits.get(i), newConcs.get(i));
		}
	}

	/**
	 * Convenience method to get all hits in a single doc from a larger hitset.
	 *
	 * Don't use this for grouping or per-document results as it's relatively inefficient.
	 *
	 * @param docid the doc id to get hits for
	 * @return the list of hits in this doc (if any)
	 */
	@Override
	public Hits getHitsInDoc(int docid) {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Interrupted. Just return no hits;
			// client should detect thread was interrupted if it
			// wants to use background threads.
			Thread.currentThread().interrupt();
			return Hits.emptyList(searcher);
		}
		List<Hit> hitsInDoc = new ArrayList<>();
		for (Hit hit: hits) {
			if (hit.doc == docid)
				hitsInDoc.add(hit);
		}
		Hits result = Hits.fromList(searcher, hitsInDoc);
		result.copySettingsFrom(this);
		return result;
	}

	/**
	 * Return the context(s) for the specified hit number
	 * @param hitNumber which hit we want the context(s) for
	 * @return the context(s)
	 */
	@Override
	public int[] getHitContext(int hitNumber) {
		return contexts[hitNumber];
	}

	/**
	 * Generate concordances from content store (slower).
	 *
	 * @param hits the hits for which to retrieve concordances
	 * @param contextSize how many words around the hit to retrieve
	 * @param fieldName field to use for building concordances
	 * @return the concordances
	 */
	private Map<Hit, Concordance> retrieveConcordancesFromContentStore(int contextSize, String fieldName) {
		XmlHighlighter hl = new XmlHighlighter(); // used to make fragments well-formed
		hl.setUnbalancedTagsStrategy(searcher.getDefaultUnbalancedTagsStrategy());
		// Group hits per document
		MutableIntObjectMap<List<Hit>> hitsPerDocument = IntObjectMaps.mutable.empty();
		for (Hit key: hits) {
			List<Hit> hitsInDoc = hitsPerDocument.get(key.doc);
			if (hitsInDoc == null) {
				hitsInDoc = new ArrayList<>();
				hitsPerDocument.put(key.doc, hitsInDoc);
			}
			hitsInDoc.add(key);
		}
		Map<Hit, Concordance> conc = new HashMap<>();
		for (List<Hit> l: hitsPerDocument.values()) {
			HitsImpl hitsInThisDoc = new HitsImpl(searcher, l);
			hitsInThisDoc.copySettingsFrom(this);
			hitsInThisDoc.makeConcordancesSingleDocContentStore(fieldName, contextSize, conc, hl);
		}
		return conc;
	}

	@Override
	public void setMaxHitsCounted(boolean maxHitsCounted) {
		this.maxHitsCounted = maxHitsCounted;
	}

	@Override
	protected void setMaxHitsRetrieved(boolean maxHitsRetrieved) {
		this.maxHitsRetrieved = maxHitsRetrieved;
	}

}
