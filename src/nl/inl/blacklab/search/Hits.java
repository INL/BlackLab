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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeSet;

import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.search.grouping.HitGroups;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.search.grouping.HitPropertyMultiple;
import nl.inl.blacklab.search.grouping.ResultsGrouper;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.BLSpansWrapper;
import nl.inl.util.StringUtil;

import org.apache.log4j.Logger;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;

/**
 * Represents a list of Hit objects. Also maintains information about the context (concordance)
 * information stored in the Hit objects.
 */
public class Hits implements Iterable<Hit> {
	protected static final Logger logger = Logger.getLogger(Hits.class);

	/**
	 * The hits.
	 */
	protected List<Hit> hits;

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
	 * The sort order, if we've sorted, or null if not
	 */
	Integer[] sortOrder;

	public static final int CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS = 3;
	public static final int CONTEXTS_HIT_START_INDEX = 0;
	public static final int CONTEXTS_RIGHT_START_INDEX = 1;
	public static final int CONTEXTS_LENGTH_INDEX = 2;

	/**
	 * The KWIC data, if it has been retrieved.
	 *
	 * NOTE: this will always be null if not all the hits have been retrieved.
	 */
	protected Map<Hit, Kwic> kwics;

	/**
	 * The searcher object.
	 */
	protected Searcher searcher;

	/**
	 * If we have context information, this specifies the property (i.e. word, lemma, pos) the context came from.
	 * Otherwise, it is null.
	 */
	protected List<String> contextFieldsPropName;

	/**
	 * The default field to use for retrieving concordance information.
	 */
	protected String concordanceFieldName;

	/**
	 * Did we completely read our Spans object?
	 */
	protected boolean sourceSpansFullyRead = true;

	/**
	 * Our Spans object, which may not have been fully read yet.
	 */
	protected BLSpans sourceSpans;

	/**
	 * Stop retrieving hits after this number.
	 * (-1 = don't stop retrieving)
	 */
	private static int defaultMaxHitsToRetrieve = 1000000;

	/**
	 * Stop counting hits after this number.
	 * (-1 = don't stop counting)
	 */
	private static int defaultMaxHitsToCount = -1;

	/**
	 * Stop retrieving hits after this number.
	 * (-1 = don't stop retrieving)
	 */
	private int maxHitsToRetrieve = defaultMaxHitsToRetrieve;

	/**
	 * Stop counting hits after this number.
	 * (-1 = don't stop counting)
	 */
	private int maxHitsToCount = defaultMaxHitsToCount;

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
	private int hitsCounted = 0;

	/**
	 * The number of separate documents we've seen in the hits retrieved.
	 */
	private int docsRetrieved = 0;

	/**
	 * The number of separate documents we've counted so far (includes non-retrieved hits).
	 */
	private int docsCounted = 0;

	/**
	 * Document the previous hit was in, so we can count separate documents.
	 */
	private int previousHitDoc = -1;

	/**
	 * The desired context size (number of words to fetch around hits).
	 * Defaults to Searcher.getDefaultContextSize().
	 */
	private int desiredContextSize;

	/**
	 * The current context size (number of words around hits we now have).
	 */
	private int currentContextSize;

	/**
	 * Construct a Hits object from an existing Hits object.
	 *
	 * The same hits list is reused. Context and sort order are
	 * not copied. All other fields are.
	 *
	 * @param copyFrom the Hits object to copy
	 */
	public Hits(Hits copyFrom) {
		try {
			copyFrom.ensureAllHitsRead();
		} catch (InterruptedException e) {
			// (should be detected by the client)
		}
		searcher = copyFrom.searcher;
		hits = copyFrom.hits;
		kwics = copyFrom.kwics;
		concordanceFieldName = copyFrom.concordanceFieldName;
		sourceSpansFullyRead = copyFrom.sourceSpansFullyRead;
		sourceSpans = copyFrom.sourceSpans;
		maxHitsToRetrieve = copyFrom.maxHitsToRetrieve;
		maxHitsToCount = copyFrom.maxHitsToCount;
		maxHitsRetrieved = copyFrom.maxHitsRetrieved;
		maxHitsCounted = copyFrom.maxHitsCounted;
		hitsCounted = copyFrom.hitsCounted;
		docsRetrieved = copyFrom.docsRetrieved;
		docsCounted = copyFrom.docsCounted;
		previousHitDoc = copyFrom.previousHitDoc;
		desiredContextSize = copyFrom.desiredContextSize;

		currentContextSize = -1; // context is not copied
	}

	/**
	 * Construct an empty Hits object
	 *
	 * @param searcher
	 *            the searcher object
	 */
	public Hits(Searcher searcher) {
		this(searcher, searcher.getContentsFieldMainPropName());
	}

	/**
	 * Make a wrapper Hits object for a list of Hit objects.
	 *
	 * Does not copy the list, but reuses it.
	 *
	 * @param searcher
	 *            the searcher object
	 * @param hits the list of hits to wrap
	 */
	public Hits(Searcher searcher, List<Hit> hits) {
		this.searcher = searcher;
		this.hits = hits;
		hitsCounted = hits.size();
		setConcordanceField(searcher.getContentsFieldMainPropName());
		desiredContextSize = searcher.getDefaultContextSize();
		currentContextSize = -1;
	}

	/**
	 * Construct an empty Hits object
	 *
	 * @param searcher
	 *            the searcher object
	 * @param concordanceFieldPropName
	 *            field to use by default when finding concordances
	 */
	public Hits(Searcher searcher, String concordanceFieldPropName) {
		this.searcher = searcher;
		hits = new ArrayList<Hit>();
		hitsCounted = 0;
		setConcordanceField(concordanceFieldPropName);
		desiredContextSize = searcher == null ? 0 /* only for test */: searcher
				.getDefaultContextSize();
		currentContextSize = -1;
	}

	/**
	 * Construct an empty Hits object.
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
	 * @deprecated supply a SpanQuery to a Hits object instead
	 */
	@Deprecated
	public Hits(Searcher searcher, String concordanceFieldPropName, Spans source) {
		this(searcher, concordanceFieldPropName);

		sourceSpans = BLSpansWrapper.optWrap(source);
		sourceSpansFullyRead = false;
	}

	/**
	 * Construct an empty Hits object
	 *
	 * @param searcher
	 *            the searcher object
	 * @param concordanceFieldPropName
	 *            field to use by default when finding concordances
	 * @param sourceQuery
	 *            the query to execute to get the hits
	 * @throws TooManyClauses if the query is overly broad (expands to too many terms)
	 */
	public Hits(Searcher searcher, String concordanceFieldPropName, SpanQuery sourceQuery)
			throws TooManyClauses {
		this(searcher, concordanceFieldPropName);

		try {
			DirectoryReader reader = null;
			SlowCompositeReaderWrapper srw = null;
			if (searcher != null) { // may happen while testing with stub classes; don't try to rewrite
				reader = searcher.getIndexReader();
				srw = new SlowCompositeReaderWrapper(searcher.getIndexReader());
			}
			SpanQuery spanQuery = (SpanQuery) sourceQuery.rewrite(reader);
			Map<Term, TermContext> termContexts = new HashMap<Term, TermContext>();
			TreeSet<Term> terms = new TreeSet<Term>();
			spanQuery.extractTerms(terms);
			for (Term term: terms) {
				termContexts.put(term, TermContext.build(reader.getContext(), term, true));
			}

			sourceSpans = BLSpansWrapper.optWrap(spanQuery.getSpans(srw != null ? srw.getContext() : null,
					srw != null ? srw.getLiveDocs() : null, termContexts));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		// logger.debug("SPANS: " + sourceSpans);
		sourceSpansFullyRead = false;
	}

	/**
	 * Construct an empty Hits object
	 *
	 * @param searcher
	 *            the searcher object
	 * @param sourceQuery
	 *            the query to execute to get the hits
	 * @throws TooManyClauses if the query is overly broad (expands to too many terms)
	 */
	public Hits(Searcher searcher, SpanQuery sourceQuery) throws TooManyClauses {
		this(searcher, searcher.getContentsFieldMainPropName(), sourceQuery);
	}

	/** Returns the context size.
	 * @return context size (number of words to fetch around hits)
	 */
	public int getContextSize() {
		return desiredContextSize;
	}

	/** Sets the desired context size.
	 * @param contextSize the context size (number of words to fetch around hits)
	 */
	public synchronized void setContextSize(int contextSize) {
		if (this.desiredContextSize == contextSize)
			return; // no need to reset anything
		this.desiredContextSize = contextSize;

		// Reset context and concordances so we get the correct context size next time
		currentContextSize = -1;
		contextFieldsPropName = null;
		// concordances = null;
		kwics = null;
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
		return maxHitsRetrieved;
	}

	/**
	 * Did we stop counting hits because we reached the maximum?
	 * @return true if we reached the maximum and stopped counting hits
	 */
	public boolean maxHitsCounted() {
		return maxHitsCounted;
	}

	/**
	 * Get the list of hits.
	 *
	 * @return the list of hits
	 * @deprecated Slow, breaks optimizations. Iterate over the Hits object and/or Hits.window() instead.
	 */
	@Deprecated
	public synchronized List<Hit> getHits() {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Interrupted; just return the hits we've gathered so far.
		}
		// We do it this way because if we return (a copy of) the hits list,
		// you get it unsorted.
		List<Hit> list = new ArrayList<Hit>();
		for (Hit h: this) {
			list.add(h);
		}
		return list;
	}

	/**
	 * If we still have only partially read our Spans object,
	 * read the rest of it and add all the hits.
	 * @throws InterruptedException if the thread was interrupted during this operation
	 */
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
			Thread currentThread = Thread.currentThread();
			try {
				while (readAllHits || hits.size() < number) {

					// Check if the thread should terminate
					if (currentThread.isInterrupted())
						throw new InterruptedException(
								"Thread was interrupted while gathering hits");

					// Stop if we're at the maximum number of hits we want to count
					if (maxHitsToCount >= 0 && hitsCounted >= maxHitsToCount) {
						maxHitsCounted = true;
						break;
					}

					// Advance to next hit
					if (!sourceSpans.next()) {
						sourceSpansFullyRead = true;
						break;
					}

					// Count the hit and add it (unless we've reached the maximum number of hits we
					// want)
					hitsCounted++;
					int hitDoc = sourceSpans.doc();
					if (hitDoc != previousHitDoc) {
						docsCounted++;
						if (!maxHitsRetrieved)
							docsRetrieved++;
						previousHitDoc = hitDoc;
					}
					maxHitsRetrieved = maxHitsToRetrieve >= 0 && hits.size() >= maxHitsToRetrieve;
					if (!maxHitsRetrieved)
						hits.add(sourceSpans.getHit());
				}
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
	 *            the hit property/properties to sort on
	 * @param reverseSort
	 *            if true, sort in descending order
	 * @deprecated use single HitProperty version, possibly with HitPropertyMultiple
	 */
	@Deprecated
	public void sort(HitProperty[] sortProp, boolean reverseSort) {
		if (sortProp.length == 1)
			sort(sortProp[0], reverseSort);
		else
			sort(new HitPropertyMultiple(sortProp), reverseSort);
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
	 *            the hit property/properties to sort on
	 * @deprecated use single HitProperty version, possibly with HitPropertyMultiple
	 */
	@Deprecated
	public void sort(HitProperty... sortProp) {
		sort(sortProp, false);
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
	 */
	public void sort(final HitProperty sortProp) {
		sort(sortProp, false, searcher.isDefaultSearchCaseSensitive());
	}

	/**
	 * Sort the list of hits.
	 *
	 * Note that if the thread is interrupted during this, sort may return
	 * without the hits actually being fully read and sorted. We don't want
	 * to add throws declarations to our whole API, so we assume the calling
	 * method will check for thread interruption if the application uses it.
	 *
	 * Case-sensitivity depends on the default case-sensitivity set on the Searcher
	 * object.
	 *
	 * @param sortProp
	 *            the hit property to sort on
	 * @param reverseSort
	 *            if true, sort in descending order
	 */
	public void sort(final HitProperty sortProp, boolean reverseSort) {
		sort(sortProp, reverseSort, searcher.isDefaultSearchCaseSensitive());
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
	 */
	public synchronized void sort(final HitProperty sortProp, boolean reverseSort, boolean sensitive) {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; don't complete the operation but return
			// and let the caller detect and deal with the interruption.
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

		// Do we need context and don't we have it yet?
		List<String> requiredContext = sortProp.needsContext();
		if (requiredContext != null
				&& (!requiredContext.equals(contextFieldsPropName) || currentContextSize != desiredContextSize)) {
			// Get 'em
			findContext(requiredContext);
		}

		Arrays.sort(sortOrder, sortProp);

		if (reverseSort) {
			// Instead of creating a new Comparator that reverses the order of the
			// sort property (which adds an extra layer of indirection to each of the
			// O(n log n) comparisons), just reverse the hits now (which runs
			// in linear time).
			//Collections.reverse(hits);
			for (int i = 0; i < n / 2; i++) {
				sortOrder[i] = sortOrder[n - i - 1];
			}
		}
	}

	/**
	 * Return a new Hits object with these hits sorted by the given property.
	 *
	 * This keeps the existing sort (or lack of one) intact and allows you to cache
	 * different sorts of the same resultset. The hits themselves are reused between
	 * the two Hits instances, so not too much additional memory is used.
	 *
	 * @param sortProp
	 *            the hit property to sort on
	 * @param reverseSort
	 *            if true, sort in descending order
	 * @param sensitive whether to sort case-sensitively or not
	 * @return a new Hits object with the same hits, sorted in the specified way
	 */
	public Hits sortedBy(HitProperty sortProp, boolean reverseSort, boolean sensitive) {
		Hits hits = new Hits(this);
		sortProp = sortProp.copyWithHits(hits);
		hits.sort(sortProp, reverseSort, sensitive);
		return hits;
	}

	/**
	 * Return a new Hits object with these hits sorted by the given property.
	 *
	 * This keeps the existing sort (or lack of one) intact and allows you to cache
	 * different sorts of the same resultset. The hits themselves are reused between
	 * the two Hits instances, so not too much additional memory is used.
	 *
	 * @param sortProp
	 *            the hit property to sort on
	 * @param reverseSort
	 *            if true, sort in descending order
	 * @return a new Hits object with the same hits, sorted in the specified way
	 */
	public Hits sortedBy(final HitProperty sortProp, boolean reverseSort) {
		return sortedBy(sortProp, reverseSort, searcher.isDefaultSearchCaseSensitive());
	}

	/**
	 * Return a new Hits object with these hits sorted by the given property.
	 *
	 * This keeps the existing sort (or lack of one) intact and allows you to cache
	 * different sorts of the same resultset. The hits themselves are reused between
	 * the two Hits instances, so not too much additional memory is used.
	 *
	 * @param sortProp
	 *            the hit property to sort on
	 * @return a new Hits object with the same hits, sorted in the specified way
	 */
	public Hits sortedBy(final HitProperty sortProp) {
		return sortedBy(sortProp, false, searcher.isDefaultSearchCaseSensitive());
	}

	/**
	 * Group these hits by a criterium (or several criteria).
	 *
	 * @param criteria
	 *            the hit property to group on
	 * @return a HitGroups object representing the grouped hits
	 */
	@SuppressWarnings("deprecation") // ResultsGrouper constructor will be made package-private eventually
	public HitGroups groupedBy(final HitProperty criteria) {
		return new ResultsGrouper(this, criteria);
	}

	/**
	 * Return a per-document view of these hits.
	 *
	 * @return the per-document view.
	 */
	@SuppressWarnings("deprecation") // DocResults constructor will be made package-private eventually
	public DocResults perDocResults() {
		return new DocResults(searcher, this);
	}

	/**
	 * Add a hit to the list
	 *
	 * NOTE: if the hits were sorted, the sort is gone after this!
	 *
	 * @param hit
	 *            the hit
	 */
	public synchronized void add(Hit hit) {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; don't complete the operation but return
			// and let the caller detect and deal with the interruption.
			return;
		}
		hits.add(hit);
		sortOrder = null; // sortOrder not correct any more, and array is too short
		hitsCounted++;
		int hitDoc = hit.doc;
		if (hitDoc != previousHitDoc) {
			docsCounted++;
			docsRetrieved++;
			previousHitDoc = hitDoc;
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
	public boolean sizeAtLeast(int lowerBound) {
		try {
			// Try to fetch at least this many hits
			ensureHitsRead(lowerBound);
		} catch (InterruptedException e) {
			// Thread was interrupted; abort operation
			// and let client decide what to do
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
	public int totalSize() {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; don't complete the operation but return
			// and let the caller detect and deal with the interruption.
			// Returned value is probably not the correct total number of hits,
			// but will not cause any crashes. The thread was interrupted anyway,
			// the value should never be presented to the user.
		}
		return hitsCounted;
	}

	/**
	 * Return the number of documents in the hits we've retrieved.
	 *
	 * @return the number of documents.
	 */
	public int numberOfDocs() {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; don't complete the operation but return
			// and let the caller detect and deal with the interruption.
			// Returned value is probably not the correct total number of hits,
			// but will not cause any crashes. The thread was interrupted anyway,
			// the value should never be presented to the user.
		}
		return docsRetrieved;
	}

	/**
	 * Return the total number of documents in all hits.
	 * This counts documents even in hits that are not stored, only counted.
	 *
	 * @return the total number of documents.
	 */
	public int totalNumberOfDocs() {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; don't complete the operation but return
			// and let the caller detect and deal with the interruption.
			// Returned value is probably not the correct total number of hits,
			// but will not cause any crashes. The thread was interrupted anyway,
			// the value should never be presented to the user.
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
	public boolean doneFetchingHits() {
		return sourceSpansFullyRead || maxHitsCounted;
	}

	/**
	 * Iterate over the hits in the original (pre-sort) order.
	 * @return an iterable object that will produce hits in the original order.
	 */
	public Iterable<Hit> hitsInOriginalOrder() {
		return new Iterable<Hit>() {
			@Override
			public Iterator<Hit> iterator() {
				return Hits.this.getIterator(true);
			}
		};
	}

	/**
	 * Return an iterator over these hits that produces the
	 * hits in their original order.
	 *
	 * @param originalOrder if true, returns hits in original order. If false,
	 *   returns them in sorted order (if any)
	 * @return the iterator
	 */
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
	 * Return an iterator over these hits.
	 *
	 * The order is the sorted order, not the original order. Use
	 * hitsInOriginalOrder() to iterate in the original order.
	 *
	 * @return the iterator
	 */
	@Override
	public Iterator<Hit> iterator() {
		return getIterator(false);
	}

	/**
	 * Return the specified hit number, based on the order they
	 * were originally found (not the sorted order).
	 *
	 * @param i
	 *            index of the desired hit
	 * @return the hit, or null if it's beyond the last hit
	 */
	public Hit getByOriginalOrder(int i) {
		try {
			ensureHitsRead(i + 1);
		} catch (InterruptedException e) {
			// Thread was interrupted. Required hit hasn't been gathered;
			// we will just return null.
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
	public Hit get(int i) {
		try {
			ensureHitsRead(i + 1);
		} catch (InterruptedException e) {
			// Thread was interrupted. Required hit hasn't been gathered;
			// we will just return null.
		}
		if (i >= hits.size())
			return null;
		return hits.get(sortOrder == null ? i : sortOrder[i]);
	}

	/**
	 * Return the concordance for the specified hit.
	 *
	 * The first call to this method will fetch the concordances for all the hits in this
	 * Hits object. So make sure to select an appropriate HitsWindow first: don't call this
	 * method on a Hits set with >1M hits unless you really want to display all of them in one
	 * go.
	 *
	 * @param h the hit
	 * @return concordance for this hit
	 */
	public Concordance getConcordance(Hit h) {
		return getConcordance(h, desiredContextSize);
	}

	/**
	 * Return the KWIC for the specified hit.
	 *
	 * The first call to this method will fetch the KWICs for all the hits in this
	 * Hits object. So make sure to select an appropriate HitsWindow first: don't call this
	 * method on a Hits set with >1M hits unless you really want to display all of them in one
	 * go.
	 *
	 * @param h the hit
	 * @return KWIC for this hit
	 */
	public Kwic getKwic(Hit h) {
		return getKwic(h, desiredContextSize);
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
	public synchronized Concordance getConcordance(String fieldName, Hit hit, int contextSize) {
		List<Hit> oneHit = Arrays.asList(hit);
		Hits h = new Hits(searcher, oneHit);
		Map<Hit, Concordance> oneConc = searcher.retrieveConcordances(h, contextSize, fieldName);
		return oneConc.get(hit);
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
	public Kwic getKwic(String fieldName, Hit hit, int contextSize) {
		List<Hit> oneHit = Arrays.asList(hit);
		Hits h = new Hits(searcher, oneHit);
		Map<Hit, Kwic> oneConc = retrieveKwics(h, contextSize, fieldName);
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
	public synchronized Concordance getConcordance(Hit h, int contextSize) {
		return getKwic(h, contextSize).toConcordance();
		/*
		if (contextSize != desiredContextSize) {
			// Different context size than the default for the whole set;
			// We probably want to show a hit with a larger snippet around it
			// (say, 50 words or so). Don't clobber the context of the other
			// hits, just fetch this snippet separately.
			return getConcordance(concordanceFieldName, h, contextSize);
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
		}
		if (concordances == null) {
			findConcordances(); // just try to find the default concordances
		}
		Concordance conc = concordances.get(h);
		if (conc == null)
			throw new RuntimeException("Concordance for hit not found: " + h);
		return conc;*/
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
	public Kwic getKwic(Hit h, int contextSize) {
		if (contextSize != desiredContextSize) {
			// Different context size than the default for the whole set;
			// We probably want to show a hit with a larger snippet around it
			// (say, 50 words or so). Don't clobber the context of the other
			// hits, just fetch this snippet separately.
			return getKwic(concordanceFieldName, h, contextSize);
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
	 * Retrieve concordances for the hits.
	 *
	 * You shouldn't have to call this manually, as it's automatically called when
	 * you call getConcordance() for the first time.
	 */
	synchronized void findConcordances() {
		/*try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted. Just go ahead with the hits we did
			// get, so at least we'll have valid concordances.
		}
		// Make sure we don't have the desired concordances already
		if (concordances != null) {
			return;
		}

		// Get the concordances
		concordances = searcher.retrieveConcordances(this, desiredContextSize, concordanceFieldName);
		*/
		findKwics();
	}

	/**
	 * Retrieve KWICs for the hits.
	 *
	 * You shouldn't have to call this manually, as it's automatically called when
	 * you call getKwic() for the first time.
	 */
	synchronized void findKwics() {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted. Just go ahead with the hits we did
			// get, so at least we'll have valid concordances.
		}
		// Make sure we don't have the desired concordances already
		if (kwics != null) {
			return;
		}

		// Get the concordances
		kwics = retrieveKwics(this, desiredContextSize, concordanceFieldName);
	}

	/**
	 * Retrieve KWICs for a (sub)list of hits.
	 *
	 * KWICs are the hit words 'centered' with a certain number of context words around them.
	 *
	 * The size of the left and right context (in words) may be set using
	 * Searcher.setConcordanceContextSize().
	 *
	 * @param hits
	 *            the hits for which to retrieve KWICs
	 * @param contextSize
	 *            how many words around the hit to retrieve
	 * @param fieldName
	 *            field to use for building KWICs
	 *
	 * @return the KWICs
	 */
	private Map<Hit, Kwic> retrieveKwics(Hits hits, int contextSize, String fieldName) {

		// Group hits per document
		Map<Integer, List<Hit>> hitsPerDocument = new HashMap<Integer, List<Hit>>();
		for (Hit key: hits) {
			List<Hit> hitsInDoc = hitsPerDocument.get(key.doc);
			if (hitsInDoc == null) {
				hitsInDoc = new ArrayList<Hit>();
				hitsPerDocument.put(key.doc, hitsInDoc);
			}
			hitsInDoc.add(key);
		}

		if (searcher.getMakeConcordancesFromForwardIndex()) {
			// Yes, make 'em from the forward index (faster)
			ForwardIndex forwardIndex = null;
			if (searcher.concWordFI != null)
				forwardIndex = searcher.getForwardIndex(ComplexFieldUtil.propertyField(fieldName,
						searcher.concWordFI));

			ForwardIndex punctForwardIndex = null;
			if (searcher.concPunctFI != null)
				punctForwardIndex = searcher.getForwardIndex(ComplexFieldUtil.propertyField(
						fieldName, searcher.concPunctFI));

			Map<String, ForwardIndex> attrForwardIndices = new HashMap<String, ForwardIndex>();
			if (searcher.concAttrFI == null) {
				// All other FIs are attributes
				for (String p: searcher.forwardIndices.keySet()) {
					String[] components = ComplexFieldUtil.getNameComponents(p);
					String propName = components[1];
					if (propName.equals(searcher.concWordFI)
							|| propName.equals(searcher.concPunctFI))
						continue;
					attrForwardIndices.put(propName, searcher.getForwardIndex(p));
				}
			} else {
				// Specific list of attribute FIs
				for (String p: searcher.concAttrFI) {
					attrForwardIndices.put(p,
							searcher.getForwardIndex(ComplexFieldUtil.propertyField(fieldName, p)));
				}
			}

			Map<Hit, Kwic> conc1 = new HashMap<Hit, Kwic>();
			for (List<Hit> l: hitsPerDocument.values()) {
				Hits hitsInThisDoc = new Hits(searcher, l);
				hitsInThisDoc.makeKwicsSingleDocForwardIndex(forwardIndex, punctForwardIndex,
						attrForwardIndices, contextSize, conc1);
			}
			return conc1;
		}

		throw new RuntimeException("Cannot make KWICs without forward index!");
	}

	/**
	 * Retrieve context words for the hits.
	 *
	 * @param fieldProps
	 *            the field and properties to use for the context
	 */
	public synchronized void findContext(List<String> fieldProps) {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted. Just go ahead with the hits we did
			// get, so at least we can return with valid context.
		}
		// Make sure we don't have the desired context already
		if (contextFieldsPropName != null && fieldProps.equals(contextFieldsPropName)
				&& desiredContextSize == currentContextSize) {
			return;
		}

		List<ForwardIndex> fis = new ArrayList<ForwardIndex>();
		for (String fieldPropName: fieldProps) {
			fis.add(searcher.getForwardIndex(fieldPropName));
		}

		// Get the context
		// Group hits per document
		List<Hit> hitsInSameDoc = new ArrayList<Hit>();
		int currentDoc = -1;
		int index = 0;
		if (contexts == null || contexts.length < hits.size()) {
			contexts = new int[hits.size()][];
		}
		for (Hit hit: hits) {
			if (hit.doc != currentDoc) {
				if (currentDoc >= 0) {
					findPartOfContext(hitsInSameDoc, index - hitsInSameDoc.size(), fis);

					// Reset hits list for next doc
					hitsInSameDoc.clear();
				}
				currentDoc = hit.doc; // start a new document
			}
			hitsInSameDoc.add(hit);
			index++;
		}
		if (hitsInSameDoc.size() > 0)
			findPartOfContext(hitsInSameDoc, index - hitsInSameDoc.size(), fis);

		Map<Integer, List<Hit>> hitsPerDocument = new HashMap<Integer, List<Hit>>();
		for (Hit key: hits) {
			List<Hit> hitsInDoc = hitsPerDocument.get(key.doc);
			if (hitsInDoc == null) {
				hitsInDoc = new ArrayList<Hit>();
				hitsPerDocument.put(key.doc, hitsInDoc);
			}
			hitsInDoc.add(key);
		}

		for (List<Hit> l: hitsPerDocument.values()) {
			if (l.size() > 0) {
				Hits hitsInThisDoc = new Hits(searcher, l);
				hitsInThisDoc.getContextWords(desiredContextSize, fis);
			}
		}
		currentContextSize = desiredContextSize;
		contextFieldsPropName = new ArrayList<String>(fieldProps);
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
		Hits hitsObj = new Hits(searcher, hitsInSameDoc);
		hitsObj.getContextWords(desiredContextSize, fis);

		// Copy the contexts from the temporary Hits object to this one
		for (int i = 0; i < hitsInSameDoc.size(); i++) {
			contexts[firstHitIndex + i] = hitsObj.getHitContext(i);
		}
	}

	/**
	 * Clear any cached concordances so new ones will be created on next call to getConcordance().
	 */
	public synchronized void clearConcordances() {
		// concordances = null;
		kwics = null;
	}

	/**
	 * Count occurrences of context words around hit.
	 *
	 * Uses the default contents field for collocations.
	 *
	 * @return the frequency of each occurring token
	 */
	public TokenFrequencyList getCollocations() {
		return getCollocations(null, null);
	}

	/**
	 * Count occurrences of context words around hit.
	 *
	 * @param propName the property to use for the collocations, or null if default
	 * @param ctx query execution context, containing the sensitivity settings
	 *
	 * @return the frequency of each occurring token
	 */
	public synchronized TokenFrequencyList getCollocations(String propName,
			QueryExecutionContext ctx) {
		findContext(Arrays.asList(ctx.luceneField(false)));
		Map<Integer, Integer> coll = new HashMap<Integer, Integer>();
		//for (Hit hit: hits) {
		//Iterator<Hit> hitIt = hits.iterator();
		//Iterator<int[]> contextIt = contexts.iterator();
		for (int j = 0; j < hits.size(); j++) {
			//Hit hit = hitIt.next();
			int[] context = contexts[j]; //It.next();

			// Count words
			int contextHitStart = context[CONTEXTS_HIT_START_INDEX];
			int contextRightStart = context[CONTEXTS_RIGHT_START_INDEX];
			int contextLength = context[CONTEXTS_LENGTH_INDEX];
			int indexInContent = CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS;
			for (int i = 0; i < contextLength; i++, indexInContent++) {
				if (i >= contextHitStart && i < contextRightStart)
					continue; // don't count words in hit itself, just around
				int w = context[indexInContent];
				Integer n = coll.get(w);
				if (n == null)
					n = 1;
				else
					n++;
				coll.put(w, n);
			}
		}

		// Get the actual words from the sort positions
		boolean caseSensitive = searcher.isDefaultSearchCaseSensitive();
		boolean diacSensitive = searcher.isDefaultSearchDiacriticsSensitive();
		TokenFrequencyList collocations = new TokenFrequencyList(coll.size());
		// TODO: get collocations for multiple contexts?
		Terms terms = searcher.getTerms(contextFieldsPropName.get(0));
		Map<String, Integer> wordFreq = new HashMap<String, Integer>();
		for (Map.Entry<Integer, Integer> e: coll.entrySet()) {
			String word = terms.get(e.getKey());
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
			n += e.getValue();
			wordFreq.put(word, n);
		}

		// Transfer from map to list
		for (Map.Entry<String, Integer> e: wordFreq.entrySet()) {
			collocations.add(new TokenFrequency(e.getKey(), e.getValue()));
		}
		return collocations;
	}

	/**
	 * Returns the searcher object.
	 *
	 * @return the searcher object.
	 */
	public Searcher getSearcher() {
		return searcher;
	}

	/**
	 * Returns the field to use for retrieving concordances.
	 *
	 * @return the field name
	 */
	public String getConcordanceFieldName() {
		return concordanceFieldName;
	}

	/**
	 * Sets the field to use for retrieving concordances.
	 *
	 * @param concordanceFieldName
	 *            the field name
	 */
	public void setConcordanceField(String concordanceFieldName) {
		this.concordanceFieldName = concordanceFieldName;
	}

	/**
	 * Get the field our current concordances were retrieved from
	 *
	 * @return the field name
	 */
	public List<String> getContextFieldPropName() {
		return contextFieldsPropName;
	}

	/**
	 * Retrieve a sublist of hits.
	 *
	 * If toIndex is beyond the last hit, will return a list up to and
	 * including the last hit.
	 *
	 * @param fromIndex first hit to include in the resulting list
	 * @param toIndex first hit not to include in the resulting list
	 * @return the sublist
	 * @deprecated use window()
	 */
	@Deprecated
	public List<Hit> subList(int fromIndex, int toIndex) {
		try {
			ensureHitsRead(toIndex);
		} catch (InterruptedException e) {
			// Thread was interrupted. We may not even have read
			// the first hit in the sublist, so just return an empty list.
			return Collections.emptyList();
		}
		if (toIndex > hits.size())
			toIndex = hits.size();

		// Make sure we get sublist in sort order
		List<Hit> result = new ArrayList<Hit>();
		for (int i = fromIndex; i < toIndex; i++) {
			result.add(get(i));
		}
		return result;
	}

	/**
	 * Get a window into this list of hits.
	 *
	 * Use this if you're displaying part of the resultset, like
	 * in a paging interface. It makes sure BlackLab only works with
	 * the hits you want to display and doesn't do any unnecessary
	 * processing on the other hits.
	 *
	 * HitsWindow includes methods to assist with paging, like figuring
	 * out if there hits before or after the window.
	 *
	 * @param first first hit in the window (0-based)
	 * @param windowSize size of the window
	 * @return the window
	 */
	@SuppressWarnings("deprecation") // we'll make it package-private instead of removing the method
	public HitsWindow window(int first, int windowSize) {
		return new HitsWindow(this, first, windowSize);
	}

	/**
	 * Set the field properties to retrieve context from
	 * @param contextField the field properties
	 */
	public void setContextField(List<String> contextField) {
		this.contextFieldsPropName = contextField == null ? null : new ArrayList<String>(
				contextField);
	}

	/** @return the default maximum number of hits to retrieve. */
	public static int getDefaultMaxHitsToRetrieve() {
		return defaultMaxHitsToRetrieve;
	}

	/** Set the default maximum number of hits to retrieve
	 * @param n the number of hits, or -1 for no limit
	 */
	public static void setDefaultMaxHitsToRetrieve(int n) {
		Hits.defaultMaxHitsToRetrieve = n;
	}

	/** @return the default maximum number of hits to count. */
	public static int getDefaultMaxHitsToCount() {
		return defaultMaxHitsToCount;
	}

	/** Set the default maximum number of hits to count
	 * @param n the number of hits, or -1 for no limit
	 */
	public static void setDefaultMaxHitsToCount(int n) {
		Hits.defaultMaxHitsToCount = n;
	}

	/** @return the maximum number of hits to retrieve. */
	public int getMaxHitsToRetrieve() {
		return maxHitsToRetrieve;
	}

	/** Set the maximum number of hits to retrieve
	 * @param n the number of hits, or -1 for no limit
	 */
	public void setMaxHitsToRetrieve(int n) {
		this.maxHitsToRetrieve = n;
	}

	/** @return the maximum number of hits to count. */
	public int getMaxHitsToCount() {
		return maxHitsToCount;
	}

	/** Set the maximum number of hits to count
	 * @param n the number of hits, or -1 for no limit
	 */
	public void setMaxHitsToCount(int n) {
		this.maxHitsToCount = n;
	}

	/**
	 * Retrieves the concordance information (left, hit and right context) for a number of hits in
	 * the same document from the Forward Index.
	 *
	 * @param forwardIndex
	 *    Forward index for the words
	 * @param punctForwardIndex
	 *    Forward index for the punctuation
	 * @param attrForwardIndices
	 *    Forward indices for the XML attributes (i.e. lemma and pos, or other properties you've indexed),
	 *    or null if none
	 * @param wordsAroundHit
	 *            number of words left and right of hit to fetch
	 * @param conc
	 *            where to add the concordances
	 */
	synchronized void makeConcordancesSingleDocForwardIndex(ForwardIndex forwardIndex,
			ForwardIndex punctForwardIndex, Map<String, ForwardIndex> attrForwardIndices,
			int wordsAroundHit, Map<Hit, Concordance> conc) {
		if (hits.size() == 0)
			return;

		// Save existing context so we can restore it afterwards
		int[][] oldContexts = null;
		if (hits.size() > 0 && contexts != null)
			oldContexts = saveContexts();

		// TODO: more efficient to get all contexts with one getContextWords() call!
		//    Also, don't overwrite 'contexts' but retrieve these in separate local variable.

		// Get punctuation context
		int[][] punctContext = null;
		if (punctForwardIndex != null) {
			getContextWords(wordsAroundHit, Arrays.asList(punctForwardIndex));
			punctContext = saveContexts();
		}
		Terms punctTerms = punctForwardIndex == null ? null : punctForwardIndex.getTerms();

		// Get attributes context
		String[] attrName = null;
		ForwardIndex[] attrFI = null;
		Terms[] attrTerms = null;
		int[][][] attrContext = null;
		if (attrForwardIndices != null) {
			int n = attrForwardIndices.size();
			attrName = new String[n];
			attrFI = new ForwardIndex[n];
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
		for (int i = 0; i < hits.size(); i++) {
			Hit h = hits.get(i);
			StringBuilder[] part = new StringBuilder[3];
			for (int j = 0; j < 3; j++) {
				part[j] = new StringBuilder();
			}
			int currentPart = 0;
			StringBuilder current = part[currentPart];
			int[] context = contexts[i];
			int contextLength = context[CONTEXTS_LENGTH_INDEX];
			int contextRightStart = context[CONTEXTS_RIGHT_START_INDEX];
			int contextHitStart = context[CONTEXTS_HIT_START_INDEX];
			int indexInContext = CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS;
			for (int j = 0; j < contextLength; j++, indexInContext++) {

				if (j == contextRightStart) {
					currentPart = 2;
					current = part[currentPart];
				}

				// Add punctuation
				// (NOTE: punctuation after match is added to right context;
				// punctuation before match is added to left context)
				if (j > 0) {
					if (punctTerms == null) {
						// There is no punctuation forward index. Just put a space
						// between every word.
						current.append(" ");
					} else
						current.append(punctTerms.get(punctContext[i][indexInContext]));
				}

				if (currentPart == 0 && j == contextHitStart) {
					currentPart = 1;
					current = part[currentPart];
				}

				// Make word tag with lemma and pos attributes
				current.append("<w");
				if (attrContext != null) {
					for (int k = 0; k < attrContext.length; k++) {
						current.append(" ")
								.append(attrName[k])
								.append("=\"")
								.append(StringUtil.escapeXmlChars(attrTerms[k]
										.get(attrContext[k][i][indexInContext]))).append("\"");
					}
				}
				current.append(">");

				if (terms != null)
					current.append(terms.get(contexts[i][indexInContext]));

				// End word tag
				current.append("</w>");
			}
			String[] concStr = new String[] { part[0].toString(), part[1].toString(),
					part[2].toString() };
			Concordance concordance = new Concordance(concStr);
			conc.put(h, concordance);
		}

		if (oldContexts != null) {
			restoreContexts(oldContexts);
		}
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
	 * @param kwics
	 *            where to add the KWICs
	 */
	synchronized void makeKwicsSingleDocForwardIndex(ForwardIndex forwardIndex,
			ForwardIndex punctForwardIndex, Map<String, ForwardIndex> attrForwardIndices,
			int wordsAroundHit, Map<Hit, Kwic> kwics) {
		if (hits.size() == 0)
			return;

		// Save existing context so we can restore it afterwards
		int[][] oldContexts = null;
		if (hits.size() > 0 && contexts != null)
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
		ForwardIndex[] attrFI = null;
		Terms[] attrTerms = null;
		int[][][] attrContext = null;
		if (attrForwardIndices != null) {
			int n = attrForwardIndices.size();
			attrName = new String[n];
			attrFI = new ForwardIndex[n];
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
		for (int i = 0; i < hits.size(); i++) {
			Hit h = hits.get(i);
			List<List<String>> part = new ArrayList<List<String>>();
			for (int j = 0; j < 3; j++) {
				part.add(new ArrayList<String>());
			}
			int currentPart = 0;
			List<String> current = part.get(currentPart);
			int[] context = contexts[i];
			int contextLength = context[CONTEXTS_LENGTH_INDEX];
			int contextRightStart = context[CONTEXTS_RIGHT_START_INDEX];
			int contextHitStart = context[CONTEXTS_HIT_START_INDEX];
			int indexInContext = CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS;
			for (int j = 0; j < contextLength; j++, indexInContext++) {

				if (j == contextRightStart) {
					currentPart = 2;
					current = part.get(currentPart);
				}

				if (currentPart == 0 && j == contextHitStart) {
					currentPart = 1;
					current = part.get(currentPart);
				}

				// Add punctuation before word
				// (Applications may choose to ignore punctuation before the first word)
				if (punctTerms == null) {
					// There is no punctuation forward index. Just put a space
					// between every word.
					current.add(" ");
				} else
					current.add(punctTerms.get(punctContext[i][indexInContext]));

				// Add extra attributes (e.g. lemma, pos)
				if (attrContext != null) {
					for (int k = 0; k < attrContext.length; k++) {
						current.add(attrTerms[k].get(attrContext[k][i][indexInContext]));
					}
				}

				// Add word
				if (terms != null)
					current.add(terms.get(context[indexInContext]));
				else
					current.add(""); // weird, but make sure the numbers add up at the end

			}
			List<String> properties = new ArrayList<String>();
			properties.add(searcher.concPunctFI);
			for (int k = 0; k < attrContext.length; k++) {
				properties.add(attrName[k]);
			}
			properties.add(searcher.concWordFI);
			Kwic kwic = new Kwic(properties, part.get(0), part.get(1), part.get(2));
			kwics.put(h, kwic);
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
	 *
	 * @param fieldName
	 *            Lucene index field to make conc for
	 * @param wordsAroundHit
	 *            number of words left and right of hit to fetch
	 * @param conc
	 *            where to add the concordances
	 */
	synchronized void makeConcordancesSingleDoc(String fieldName, int wordsAroundHit,
			Map<Hit, Concordance> conc) {
		if (hits.size() == 0)
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
		List<Concordance> newConcs = searcher.makeFieldConcordances(doc, fieldName, startsOfWords,
				endsOfWords);
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
	public Hits getHitsInDoc(int docid) {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Interrupted. Just return no hits;
			// client should detect thread was interrupted if it
			// wants to use background threads.
			return new Hits(searcher);
		}
		List<Hit> hitsInDoc = new ArrayList<Hit>();
		for (Hit hit: hits) {
			if (hit.doc == docid)
				hitsInDoc.add(hit);
		}
		return new Hits(searcher, hitsInDoc);
	}

	/**
	 * Return the context(s) for the specified hit number
	 * @param hitNumber which hit we want the context(s) for
	 * @return the context(s)
	 */
	public int[] getHitContext(int hitNumber) {
		return contexts[hitNumber];
	}

}
