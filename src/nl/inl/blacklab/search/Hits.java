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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.search.grouping.HitPropertyMultiple;
import nl.inl.util.StringUtil;

import org.apache.log4j.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanQuery;
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
	 * The concordances, if they have been retrieved.
	 *
	 * NOTE: this will always be null if not all the hits have been retrieved.
	 */
	protected Map<Hit, Concordance> concordances;

	/**
	 * The searcher object.
	 */
	protected Searcher searcher;

	/**
	 * If we have context information, this specifies the property (i.e. word, lemma, pos) the context came from.
	 * Otherwise, it is null.
	 */
	protected String contextFieldPropName;

	/**
	 * The default field to use for retrieving concordance information.
	 */
	protected String concordanceFieldName;

	/**
	 * Lucene name for the main property field of the current contents field.
	 */
	private String concordanceMainFieldPropName;

	/**
	 * Did we completely read our Spans object?
	 */
	protected boolean sourceSpansFullyRead = true;

	/**
	 * Our Spans object, which may not have been fully read yet.
	 */
	protected Spans sourceSpans;

	/**
	 * How many hits do we have in total?
	 * (We keep this separately because we may run through a Spans first to
	 * count the hits without storing them, to avoid unnecessarily instantiating
	 * Hit objects)
	 * If -1, we don't know the total number of hits yet.
	 */
	int totalNumberOfHits;

	/**
	 * For extremely large queries, stop retrieving hits after this number.
	 */
	public final static int MAX_HITS_TO_RETRIEVE = 10000000;

	/**
	 * If true, we try to count the total number of hits even if we don't
	 * actually retrieve all of them yet. Now set to false because for large
	 * queries even enumerating the hits takes a lot of time, so this is only
	 * done if requested.
	 */
	private static final boolean PRE_COUNT_TOTAL_HITS = false;

	/**
	 * If true, we've stopped retrieving hits because there are more than
	 * the maximum we've set.
	 */
	private boolean tooManyHits = false;

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
	 * The number of documents counted (only valid if the basis for this is a SpanQuery object;
	 * used by DocResults to report the total number of docs without retrieving them all first).
	 * If the value is -1, we don't know the total number of docs.
	 */
	private int totalNumberOfDocs;

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
		totalNumberOfHits = 0;
		setConcordanceField(concordanceFieldPropName);
		desiredContextSize = searcher == null ? 0 /* only for test */ : searcher.getDefaultContextSize();
		currentContextSize = -1;
		totalNumberOfDocs = -1; // unknown
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
	public
	Hits(Searcher searcher, String concordanceFieldPropName, Spans source) {
		this(searcher, concordanceFieldPropName);

		totalNumberOfHits = -1; // "not known yet"
		sourceSpans = source;
		sourceSpansFullyRead = false;
		totalNumberOfDocs = -1; // unknown
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
	public Hits(Searcher searcher, String concordanceFieldPropName, SpanQuery sourceQuery) throws TooManyClauses {
		this(searcher, concordanceFieldPropName);

		totalNumberOfDocs = -1;
		totalNumberOfHits = -1;

		if (PRE_COUNT_TOTAL_HITS) {
			// Count how many hits there are in total
			sourceSpans = findSpans(sourceQuery);
			try {
				totalNumberOfDocs = 0;
				int doc = -1;
				tooManyHits = false;
				while (sourceSpans.next()) {
					if (doc != sourceSpans.doc()) {
						doc = sourceSpans.doc();
						totalNumberOfDocs++;
					}
					totalNumberOfHits++;
					if (totalNumberOfHits >= MAX_HITS_TO_RETRIEVE) {
						// Too many hits; stop collecting here
						tooManyHits = true;
						break;
					}
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		sourceSpans = findSpans(sourceQuery); // Counted 'em. Now reset.
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
	public void setContextSize(int contextSize) {
		if (this.desiredContextSize == contextSize)
			return; // no need to reset anything
		this.desiredContextSize = contextSize;

		// Reset context and concordances so we get the correct context size next time
		currentContextSize = -1;
		contextFieldPropName = null;
		concordances = null;
	}

	/**
	 * Executes the SpanQuery to get a Spans object.
	 *
	 * @param spanQuery
	 *            the query
	 * @return the results object
	 * @throws BooleanQuery.TooManyClauses
	 *             if a wildcard or regular expression term is overly broad
	 */
	Spans findSpans(SpanQuery spanQuery) throws BooleanQuery.TooManyClauses {
		try {
			IndexReader reader = null;
			if (searcher != null) { // this may happen while testing with stub classes; don't try to rewrite
				reader = searcher.getIndexReader();
			}
			spanQuery = (SpanQuery) spanQuery.rewrite(reader);
			return spanQuery.getSpans(reader);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Were all hits retrieved, or did we stop because there were too many?
	 * @return true if all hits were retrieved
	 */
	public boolean tooManyHits() {
		return tooManyHits;
	}

	/**
	 * Get the list of hits.
	 *
	 * @return the list of hits
	 * @deprecated Breaks optimizations. Use iteration or subList() instead.
	 */
	@Deprecated
	public List<Hit> getHits() {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Interrupted; just return the hits we've gathered so far.
		}
		return Collections.unmodifiableList(hits);
	}

	/**
	 * If we still have only partially read our Spans object,
	 * read the rest of it and add all the hits.
	 * @throws InterruptedException if the thread was interrupted during this operation
	 */
	private void ensureAllHitsRead() throws InterruptedException {
		if (sourceSpansFullyRead)
			return;
		sourceSpansFullyRead = true;

		try {
			while (sourceSpans.next()) {

				if (Thread.currentThread().isInterrupted())
					throw new InterruptedException("Thread was interrupted while gathering hits");

				hits.add(Hit.getHit(sourceSpans));
				if (totalNumberOfHits >= 0 && hits.size() >= totalNumberOfHits) {
					// Either we've got them all, or we should stop
					// collecting them because there's too many
					break;
				}
			}
			totalNumberOfHits = hits.size();
			//logger.debug("Read all hits: " + totalNumberOfHits);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Ensure that we have read at least as many hits as specified in the index parameter.
	 *
	 * @param index the minimum number of hits that will have been read when this method
	 *   returns (unless there are fewer hits than this)
	 * @throws InterruptedException if the thread was interrupted during this operation
	 */
	private void ensureHitsRead(int index) throws InterruptedException {
		if (sourceSpansFullyRead)
			return;

		try {
			while (hits.size() <= index) {

				if (Thread.currentThread().isInterrupted())
					throw new InterruptedException("Thread was interrupted while gathering hits");

				if (!sourceSpans.next()) {
					sourceSpansFullyRead = true;
					totalNumberOfHits = hits.size();
					break;
				}
				hits.add(Hit.getHit(sourceSpans));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
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
	 */
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
	 */
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
	 * @param reverseSort
	 *            if true, sort in descending order
	 */
	public void sort(final HitProperty sortProp, boolean reverseSort) {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; don't complete the operation but return
			// and let the caller detect and deal with the interruption.
			return;
		}

		// Do we need context and don't we have it yet?
		String requiredContext = sortProp.needsContext();
		if (requiredContext != null && (!requiredContext.equals(contextFieldPropName) || currentContextSize != desiredContextSize)) {
			// Get 'em
			findContext(requiredContext);
		}

		Collections.sort(hits, sortProp);
		if (reverseSort) {
			// Instead of creating a new Comparator that reverses the order of the
			// sort property (which adds an extra layer of indirection to each of the
			// O(n log n) comparisons), just reverse the hits now (which runs
			// in linear time).
			Collections.reverse(hits);
		}
	}

	/**
	 * Add a hit to the list
	 *
	 * @param hit
	 *            the hit
	 */
	public void add(Hit hit) {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; don't complete the operation but return
			// and let the caller detect and deal with the interruption.
			return;
		}
		hits.add(hit);
		totalNumberOfHits++;
	}

	/**
	 * Determines if there are at least a certain number of hits
	 *
	 * This may be used if we don't want to process all hits (which
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
			ensureHitsRead(lowerBound);
		} catch (InterruptedException e) {
			// Thread was interrupted; abort operation
			// and let client decide what to do
		}

		return hits.size() >= lowerBound;
	}

	/**
	 * Return the number of hits.
	 *
	 * @return the number of hits
	 */
	public int size() {
		if (totalNumberOfHits >= 0)
			return totalNumberOfHits; // fully known, or pre-counted

		// Probably not all hits have been seen yet. Collect them all.
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted; don't complete the operation but return
			// and let the caller detect and deal with the interruption.
			// Returned value is probably not the correct total number of hits,
			// but will not cause any crashes. The thread was interrupted anyway,
			// the value should never be presented to the user.
			return hits.size();
		}
		return totalNumberOfHits;
	}

	/**
	 * Return an iterator over these hits.
	 *
	 * @return the iterator
	 */
	@Override
	public Iterator<Hit> iterator() {
		// Construct a custom iterator that iterates over the hits in the hits
		// list, but can also take into account the Spans object that may not have
		// been fully read. This ensures we don't instantiate Hit objects for all hits
		// if we just want to display the first few.
		return new Iterator<Hit>() {

			int index = -1;

			@Override
			public boolean hasNext() {
				// Do we still have hits in the hits list?
				if (index + 1 >= hits.size()) {
					// No; are there more hits to be read in the Spans?
					if (sourceSpansFullyRead)
						return false;
					try {
						if (!sourceSpans.next())
							return false;
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
					// Yep; add hit from Spans to hits list and position the iterator correctly
					hits.add(Hit.getHit(sourceSpans));
					index = hits.size() - 2;
				}
				return true;
			}

			@Override
			public Hit next() {
				// Check if there is a next, taking unread hits from Spans into account
				if (hasNext()) {
					index++;
					return hits.get(index);
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
	 * Return the specified hit.
	 *
	 * @param i
	 *            index of the desired hit
	 * @return the hit, or null if it's beyond the last hit
	 */
	public Hit get(int i) {
		try {
			ensureHitsRead(i);
		} catch (InterruptedException e) {
			// Thread was interrupted. Required hit hasn't been gathered;
			// we will just return null.
		}
		if (i >= hits.size())
			return null;
		return hits.get(i);
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
		return conc;
	}

	/**
	 * Retrieve concordances for the hits.
	 *
	 * You shouldn't have to call this manually, as it's automatically called when
	 * you call getConcordance() for the first time.
	 */
	void findConcordances() {
		try {
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
		concordances = searcher.retrieveConcordances(concordanceFieldName, hits, desiredContextSize);
	}

	/**
	 * Retrieve context words for the hits.
	 *
	 * @param fieldPropName
	 *            the field and property to use for the concordances
	 */
	public void findContext(String fieldPropName) {
		try {
			ensureAllHitsRead();
		} catch (InterruptedException e) {
			// Thread was interrupted. Just go ahead with the hits we did
			// get, so at least we can return with valid context.
		}
		// Make sure we don't have the desired concordances already
		if (contextFieldPropName != null && fieldPropName.equals(contextFieldPropName) && desiredContextSize == currentContextSize) {
			return;
		}

		// Get the concordances
		searcher.retrieveContext(fieldPropName, hits, desiredContextSize);
		currentContextSize = desiredContextSize;

		contextFieldPropName = fieldPropName;
	}

	/**
	 * Retrieve context for the hits, for sorting/grouping.
	 *
	 * NOTE: you should never have to call this manually; it is
	 * called if needed by the sorting/grouping code.
	 *
	 * Uses the main property field.
	 */
	void findContext() {
		findContext(concordanceMainFieldPropName);
	}

	/**
	 * Clear any cached concordances so new ones will be created on next call to getConcordance().
	 */
	public void clearConcordances() {
		concordances = null;
	}

	/**
	 * Clear any cached concordances so new ones will be created when necessary.
	 */
	public void clearContext() {
		for (Hit hit: hits) {
			hit.context = null;
		}
		contextFieldPropName = null;
	}

	/**
	 * Count occurrences of context words around hit.
	 *
	 * Uses the default contents field for collocations.
	 */
	public TokenFrequencyList getCollocations() {
		return getCollocations(null, null);
	}

	/**
	 * Count occurrences of context words around hit.
	 *
	 * @param propName the property to use for the collocations, or null if default
	 * @param ctx query execution context, containing the sensitivity settings
	 */
	public TokenFrequencyList getCollocations(String propName, QueryExecutionContext ctx) {
		findContext(ctx.luceneField(false));
		Map<Integer, Integer> coll = new HashMap<Integer, Integer>();
		for (Hit hit: hits) {
			int[] context = hit.context;

			// Count words
			for (int i = 0; i < context.length; i++) {
				if (i >= hit.contextHitStart && i < hit.contextRightStart)
					continue; // don't count words in hit itself, just around
				int w = context[i];
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
		//Map<String, Integer> collStr = new HashMap<String, Integer>();
		Terms terms = searcher.getTerms(contextFieldPropName);
		for (Map.Entry<Integer, Integer> e: coll.entrySet()) {
			String word = terms.getFromSortPosition(e.getKey());
			if (!diacSensitive) {
				word = StringUtil.removeAccents(word);
			}
			if (!caseSensitive) {
				word = word.toLowerCase();
			}
			collocations.add(new TokenFrequency(word, e.getValue()));
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
		if (searcher == null) {
			// Can occur during testing. Just use default main property name.
			concordanceMainFieldPropName = ComplexFieldUtil.propertyField(concordanceFieldName, ComplexFieldUtil.getDefaultMainPropName());
		}
		else {
			// Get the main property name from the index structure.
			concordanceMainFieldPropName = ComplexFieldUtil.mainPropertyField(searcher.getIndexStructure(), concordanceFieldName);
		}
	}

	/**
	 * Get the field our current concordances were retrieved from
	 *
	 * @return the field name
	 */
	public String getContextFieldPropName() {
		return contextFieldPropName;
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
	 */
	public List<Hit> subList(int fromIndex, int toIndex) {
		try {
			ensureHitsRead(toIndex - 1);
		} catch (InterruptedException e) {
			// Thread was interrupted. We may not even have read
			// the first hit in the sublist, so just return an empty list.
			return Collections.emptyList();
		}
		if (toIndex > hits.size())
			toIndex = hits.size();
		return hits.subList(fromIndex, toIndex);
	}

	public void setContextField(String contextField) {
		this.contextFieldPropName = contextField;
	}

	/**
	 * The number of documents counted (only valid if the basis for this is a SpanQuery object;
	 * used by DocResults to report the total number of docs without retrieving them all first)
	 *
	 * @return number of docs, or -1 if we haven't seen all the hits yet and therefore don't know
	 */
	public int numberOfDocs() {
		return totalNumberOfDocs;
	}
}
